package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

/** Historical payload is immutable and versioned independently from Room and the live cart. */
object ReceiptCodec {
    fun encode(cart: CurrentCart,draft: CheckoutDraft,quote: SaleQuote,payment: SalePayment,
        consumption: Map<String,String>,products: List<JSONObject>): String=JSONObject().put("version",1)
        .put("currency","BYN").put("cart",JSONObject(WorkspaceCodec.cart(cart))).put("checkout",JSONObject(CheckoutCodec.encode(draft)))
        .put("gross",quote.gross).put("subtotal",quote.subtotal).put("discount",quote.discount).put("total",quote.total)
        .put("lines",JSONArray().also {a->quote.lines.forEach {a.put(JSONObject().put("id",it.id).put("gross",it.gross).put("discount",it.discount).put("net",it.net))}})
        .put("payments",JSONArray().also {a->payment.parts.forEach {a.put(JSONObject().put("method",it.method).put("amount",it.amount).put("given",it.given ?: JSONObject.NULL).put("change",it.change ?: JSONObject.NULL))}})
        .put("cash",payment.cash).put("card",payment.card).put("deliveryCashOut",payment.deliveryCashOut).put("expectedCashAfter",payment.expectedCashAfter)
        .put("consumption",JSONObject().also {j->consumption.toSortedMap().forEach {(id,q)->j.put(id,q)}})
        .put("products",JSONArray(products)).toString()
    fun decode(raw: String): ReceiptSnapshot {
        val j=JSONObject(raw)
        if(j.getInt("version")!=1 || j.getString("currency")!="BYN") throw CatalogFailure("receipt","Неизвестный формат чека")
        val cart=WorkspaceCodec.cart(j.getJSONObject("cart").toString());val draft=CheckoutCodec.decode(j.getJSONObject("checkout").toString())
        val lines=j.getJSONArray("lines");val payments=j.getJSONArray("payments");val stock=j.getJSONObject("consumption")
        val products=j.getJSONArray("products")
        return ReceiptSnapshot(cart,draft,SaleQuote((0 until lines.length()).map {lines.getJSONObject(it).let {l->SaleLineMoney(l.getString("id"),l.getLong("gross"),l.getLong("discount"),l.getLong("net"))}},
            j.getLong("gross"),j.getLong("subtotal"),j.getLong("discount"),draft.delivery,j.getLong("total")),
            SalePayment((0 until payments.length()).map {payments.getJSONObject(it).let {p->PaymentAllocation(p.getString("method"),p.getLong("amount"),if(p.isNull("given")) null else p.getLong("given"),if(p.isNull("change")) null else p.getLong("change"))}},
                j.getLong("cash"),j.getLong("card"),j.getLong("deliveryCashOut"),j.getLong("expectedCashAfter")),
            stock.keys().asSequence().associateWith {stock.getString(it)},(0 until products.length()).map {products.getJSONObject(it).toString()})
    }
}
data class ReceiptSnapshot(val cart: CurrentCart,val checkout: CheckoutDraft,val quote: SaleQuote,val payment: SalePayment,
    val consumption: Map<String,String>,val products: List<String>)

class SaleRepository(private val db: CatalogDatabase,private val clock: Clock=Clock.systemDefaultZone(),private val afterWrite: (() -> Unit)?=null) {
    private fun conflict(): Nothing=throw CatalogFailure("conflict","Заказ или оплата изменились. Проверьте оплату заново")
    private fun wrote() {afterWrite?.invoke()}
    fun observe()=db.sales().observe()
    suspend fun history()=db.sales().history()
    suspend fun receipt(id: String)=db.sales().receipt(id)?.let {it to ReceiptCodec.decode(it.payload)}
    suspend fun outcome(command: String)=db.sales().command(command)
    suspend fun unacknowledged(): String?=db.withTransaction {
        val doc=db.workspace().document("checkout") ?: return@withTransaction null
        val j=JSONObject(doc.payload)
        if(j.optBoolean("discarded",false) && j.has("receiptId")) j.getString("receiptId").also {
            check(db.sales().receipt(it)!=null) {"Не найден результат оплаты"}
        } else null
    }
    suspend fun acknowledge(id: String)=InstallationGate.write {db.withTransaction {
        val doc=db.workspace().document("checkout") ?: return@withTransaction
        val j=JSONObject(doc.payload)
        if(j.optBoolean("discarded",false) && j.optString("receiptId")==id) {
            check(doc.revision<Long.MAX_VALUE)
            db.workspace().put(doc.copy(revision=doc.revision+1,payload="{\"version\":1,\"discarded\":true}"))
        }
    }}
    /** The caller retains the submitted snapshot until outcome lookup/replay resolves any lost response. */
    suspend fun commit(saved: SavedCheckout): String=InstallationGate.write {db.withTransaction {
        val d=CheckoutCodec.decode(CheckoutCodec.encode(saved.draft))
        val submitted=SavedCheckout(saved.revision,d)
        require(d.commandId.isNotBlank() && d.commandId.length<=100)
        val fp=CatalogRules.fingerprint("sale.commit.v1",saved.revision,CheckoutCodec.encode(d))
        db.catalog().result(d.commandId)?.let {old->
            if(old.fingerprint!=fp) conflict()
            check(db.sales().receipt(old.resultId)?.commandId==d.commandId) {"Не найден сохранённый чек"}
            return@withTransaction old.resultId
        }
        val progress=SplitPaymentRepository(db).requireReady(submitted)
        if(CheckoutRepository(db).load()!=submitted) conflict()
        val shiftRepo=ShiftRepository(db)
        val shift=shiftRepo.requireActive(d.shiftId,d.shiftRevision)
        val shiftState=shiftRepo.snapshot()
        if(d.actorId!=shiftState.ownerId || d.actorId!=shift.actorId) throw CatalogFailure("owner","Недопустимый оператор продажи")
        val work=WorkspaceRepository(db).snapshot();val cart=work.cart
        if(cart.sessionId!=d.sessionId || work.cartRevision!=d.cartRevision || cart.lines.isEmpty()) conflict()
        if(cart.lines.any {CartEngine.stale(it,work.products)}) conflict()
        if(d.discounts.keys.any {id->cart.lines.none {it.id==id}}) conflict()
        val quote=SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,d.discounts[it.id])},cart.details.type,d.delivery)
        val payment=SaleMoney.payments(quote,d.parts,shiftState.currentCash)
        val calculator=RecipeCalculator(cart.graph)
        val requests=CartEngine.requests(cart)
        // Parked reservations remain owned by their orders; they are not written off a second time.
        val reservations=OrderReservations.total(work.parked.map {it.cart})
        if(calculator.shortages(requests,work.balances,reservations).isNotEmpty()) throw CatalogFailure("stock","Недостаточно остатка с учётом отложенных заказов")
        val consumption=calculator.consumption(requests).mapValues {CatalogRules.canonical(it.value)}
        val productIds=(cart.lines.flatMap {it.references.keys+it.productId}+cart.graph.map {it.id}).toSortedSet()
        val entities=productIds.map {db.catalog().product(it) ?: conflict()}
        val categories=db.catalog().categorySnapshot().associateBy {it.id}
        val metadata=entities.map {p->JSONObject().put("id",p.id).put("name",p.name).put("categoryId",p.categoryId ?: JSONObject.NULL)
            .put("categoryName",categories[p.categoryId]?.name ?: "").put("cost",p.cost).put("price",p.price).put("revision",p.revision)}
        val prior=db.sales().inShift(shift.id)
        val number=Math.addExact(prior.lastOrNull()?.number ?: 0L,1L)
        val now=clock.millis();if(now<shift.openedAt) throw CatalogFailure("clock","Время продажи раньше открытия смены")
        require(work.cartRevision<Long.MAX_VALUE && shiftState.revision<Long.MAX_VALUE && saved.revision<Long.MAX_VALUE)
        val id=UUID.randomUUID().toString()
        db.sales().insert(ReceiptEntity(id,d.commandId,cart.sessionId,shift.id,number,now,quote.total,payment.cash,payment.card,payment.deliveryCashOut,
            ReceiptCodec.encode(cart,d,quote,payment,consumption,metadata)));wrote()
        productIds.forEach {db.sales().reference(ReceiptProductEntity(id,it));wrote()}
        consumption.toSortedMap().forEach {(productId,qty)->
            val balance=db.catalog().balance(productId) ?: throw CatalogFailure("stock","Не найден остаток ингредиента")
            require(balance.revision<Long.MAX_VALUE)
            val next=balance.quantity.toBigDecimal()-qty.toBigDecimal()
            db.catalog().updateBalance(balance.copy(quantity=CatalogRules.canonical(next),revision=balance.revision+1));wrote()
            db.catalog().movement(MovementEntity(UUID.randomUUID().toString(),productId,CatalogRules.canonical(-qty.toBigDecimal()),"sale",
                CatalogRules.fingerprint("sale.stock.v1",d.commandId,productId),now,id));wrote()
        }
        db.shifts().register(ShiftRegisterEntity(revision=shiftState.revision+1,lastShiftId=shift.id));wrote()
        db.workspace().put(WorkspaceDocument("current",work.cartRevision+1,WorkspaceCodec.cart(CurrentCart(sessionId=UUID.randomUUID().toString()))));wrote()
        db.workspace().put(WorkspaceDocument("checkout",saved.revision+1,JSONObject().put("version",1).put("discarded",true).put("receiptId",id).toString()));wrote()
        if(progress!=null) {SplitPaymentRepository(db).finish(progress);wrote()}
        db.catalog().result(ResultEntity(d.commandId,fp,id));wrote()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),d.commandId,d.actorId,"sale.commit",id,now));wrote()
        // No external adapter is configured. No synthetic print/sync jobs or network side effects.
        id
    }}
}
