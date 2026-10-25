package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Confirmed manual parts are not receipts or bank SDK acknowledgements. */
data class SplitProgress(val revision: Long,val checkout: SavedCheckout,val paid: Set<Int>) {
    val complete: Boolean get()=paid.size==checkout.draft.parts.size
    val paidAmount: Long get()=paid.fold(0L) {sum,index->Math.addExact(sum,checkout.draft.parts[index].amount)}
    val pendingCash: Long get()=paid.filter {checkout.draft.parts[it].method=="cash"}.fold(0L) {sum,index->Math.addExact(sum,checkout.draft.parts[index].amount)}
}
class SplitPaymentRepository(private val db: CatalogDatabase,private val afterWrite: (() -> Unit)?=null) {
    private val key="split-progress"
    private fun conflict(): Nothing=throw CatalogFailure("split","Раздельная оплата изменилась. Откройте сохранённый прогресс заново")
    suspend fun load(): SplitProgress?=db.withTransaction {
        val doc=db.workspace().document(key) ?: return@withTransaction null
        val j=JSONObject(doc.payload)
        if(j.getInt("version")!=1) throw CatalogFailure("split","Неизвестный формат раздельной оплаты")
        if(j.getBoolean("completed")) return@withTransaction null
        val saved=SavedCheckout(j.getLong("checkoutRevision"),CheckoutCodec.decode(j.getJSONObject("checkout").toString()))
        val a=j.getJSONArray("paid");val paid=(0 until a.length()).map {a.getInt(it)}
        require(saved.draft.parts.size in 2..10 && paid.isNotEmpty() && paid.distinct().size==paid.size && paid.all {it in saved.draft.parts.indices})
        SplitProgress(doc.revision,saved,paid.toSet())
    }
    suspend fun revision(): Long=db.withTransaction {db.workspace().document(key)?.revision ?: 0L}
    /** Must be invoked by each durable writer inside its own transaction. */
    suspend fun requireNone() {
        check(db.inTransaction())
        if(load()!=null) throw CatalogFailure("split","Сначала завершите сохранённую раздельную оплату")
    }
    suspend fun requireProductUnlocked(id: String) {
        check(db.inTransaction())
        val p=load() ?: return
        val doc=db.workspace().document("current") ?: conflict()
        val cart=WorkspaceCodec.cart(doc.payload)
        if(cart.sessionId!=p.checkout.draft.sessionId || doc.revision!=p.checkout.draft.cartRevision) conflict()
        if(cart.lines.any {it.productId==id || id in it.references} || cart.graph.any {it.id==id})
            throw CatalogFailure("split","Товар участвует в частично оплачиваемом заказе. Сначала завершите оплату")
    }
    suspend fun requireCategoryUnlocked(id: String) {
        check(db.inTransaction())
        if(load()==null) return
        for(p in db.catalog().allProducts().filter {it.categoryId==id}) requireProductUnlocked(p.id)
    }
    /** Deterministic part identity survives taps, process restart and final receipt commit. */
    suspend fun confirm(saved: SavedCheckout,index: Int,expectedRevision: Long): String=InstallationGate.write {db.withTransaction {
        val d=CheckoutCodec.decode(CheckoutCodec.encode(saved.draft));val frozen=SavedCheckout(saved.revision,d)
        require(d.parts.size in 2..10 && index in d.parts.indices)
        val command=CatalogRules.fingerprint("split.part.command.v1",d.commandId,index)
        val fp=CatalogRules.fingerprint("split.part.v1",saved.revision,CheckoutCodec.encode(d),index)
        db.catalog().result(command)?.let {old->if(old.fingerprint!=fp) conflict();return@withTransaction old.resultId}
        val prior=load();val revision=revision()
        if(revision!=expectedRevision || prior!=null && prior.checkout!=frozen) conflict()
        if(CheckoutRepository(db).load()!=frozen) conflict()
        val shift=ShiftRepository(db).snapshot()
        if(shift.active?.id!=d.shiftId || shift.revision!=d.shiftRevision || shift.ownerId!=d.actorId) conflict()
        val now=System.currentTimeMillis()
        if(now<shift.active!!.openedAt) throw CatalogFailure("clock","Время подтверждения раньше открытия смены")
        val work=WorkspaceRepository(db).snapshot();val cart=work.cart
        if(cart.sessionId!=d.sessionId || work.cartRevision!=d.cartRevision || cart.lines.any {CartEngine.stale(it,work.products)}) conflict()
        if(d.discounts.keys.any {id->cart.lines.none {it.id==id}}) conflict()
        val quote=SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,d.discounts[it.id])},cart.details.type,d.delivery)
        SaleMoney.payments(quote,d.parts,shift.currentCash)
        val calculator=RecipeCalculator(cart.graph)
        if(calculator.shortages(CartEngine.requests(cart),work.balances,OrderReservations.total(work.parked.map {it.cart})).isNotEmpty())
            throw CatalogFailure("stock","Недостаточно остатка для раздельной оплаты")
        val paid=prior?.paid.orEmpty()+index
        require(revision<Long.MAX_VALUE)
        val payload=JSONObject().put("version",1).put("completed",false).put("checkoutRevision",saved.revision)
            .put("checkout",JSONObject(CheckoutCodec.encode(d))).put("paid",JSONArray(paid.sorted())).toString()
        db.workspace().put(WorkspaceDocument(key,revision+1,payload));afterWrite?.invoke()
        db.catalog().result(ResultEntity(command,fp,command));afterWrite?.invoke()
        db.catalog().audit(AuditEntity(UUID.randomUUID().toString(),command,d.actorId,"sale.part.confirmed",d.commandId,now));afterWrite?.invoke()
        command
    }}
    /** Only the enclosing final Sale transaction can consume confirmed progress. */
    internal suspend fun finish(progress: SplitProgress) {
        check(db.inTransaction())
        if(load()!=progress || !progress.complete) conflict()
        require(progress.revision<Long.MAX_VALUE)
        db.workspace().put(WorkspaceDocument(key,progress.revision+1,"{\"version\":1,\"completed\":true}"))
    }
    suspend fun requireReady(saved: SavedCheckout): SplitProgress? {
        check(db.inTransaction())
        val p=load() ?: return null
        if(p.checkout!=saved || !p.complete) throw CatalogFailure("split","Подтвердите все части сохранённой оплаты")
        return p
    }
    suspend fun requireInstallAllowed()=db.withTransaction {requireNone()}
}
