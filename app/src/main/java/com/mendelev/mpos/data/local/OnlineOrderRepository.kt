package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.backup.WebIdentity
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.sale.*
import com.mendelev.mpos.domain.workspace.*
import com.mendelev.mpos.core.operations.InstallationGate
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OnlineAcceptance(val orderId: String,val externalId: String,val sessionId: String,val target: String,val dataset: String,
    val estimate: String,val accepted: Boolean=false,val ready: Boolean=false,val readyConfirmed: Boolean=false,val settled: Boolean=false,
    val conflict: String="",val cancelPending: Boolean=false,val cancelled: Boolean=false,val paid: Boolean=false)
object OnlineOrderCodec {
    const val PREFIX="backend-online:"
    fun session(target: String,orderId: String)=UUID.nameUUIDFromBytes("mpos.web.$target.$orderId".toByteArray()).toString()
    fun owns(id: String)=id.startsWith(PREFIX)
    fun estimate(value: String)=value in listOf("5m","15m","30m","40m","60plus") || Regex("at:([01]\\d|2[0-3]):[0-5]\\d").matches(value)
    fun encode(rows: List<OnlineAcceptance>)=JSONObject().put("version",1).put("records",JSONArray(rows.map {r->JSONObject()
        .put("orderId",r.orderId).put("externalId",r.externalId).put("sessionId",r.sessionId).put("target",r.target).put("dataset",r.dataset)
        .put("estimate",r.estimate).put("accepted",r.accepted).put("ready",r.ready).put("readyConfirmed",r.readyConfirmed).put("settled",r.settled)
        .put("conflict",r.conflict).put("cancelPending",r.cancelPending).put("cancelled",r.cancelled).put("paid",r.paid)})).toString()
    fun decode(raw: String): List<OnlineAcceptance> {
        val j=JSONObject(raw);require(j.getInt("version")==1);val a=j.getJSONArray("records");require(a.length()<=100_000)
        return List(a.length()) {i->val r=a.getJSONObject(i)
            OnlineAcceptance(r.getString("orderId"),r.getString("externalId"),r.getString("sessionId"),r.getString("target"),r.getString("dataset"),r.getString("estimate"),r.getBoolean("accepted"),r.getBoolean("ready"),r.getBoolean("readyConfirmed"),r.getBoolean("settled"),r.optString("conflict"),r.optBoolean("cancelPending"),r.optBoolean("cancelled"),r.optBoolean("paid")).also {
                require(Regex("[A-Za-z0-9_-]{1,100}").matches(it.orderId) && it.externalId.length<=100)
                require(Regex("[0-9a-f]{64}").matches(it.target) && it.sessionId.length in 1..100 && estimate(it.estimate))
                require((!it.readyConfirmed || it.ready && it.accepted) && it.conflict in setOf("","cancelled"))
                require(!it.cancelled || !it.paid)
                require(!it.settled || it.paid || it.cancelled)
            }
        }.also {rows->require(rows.map {it.target to it.orderId}.distinct().size==rows.size && rows.map {it.sessionId}.distinct().size==rows.size)}
    }
}

/** A durable order and its acknowledgement intent are committed in the same Room transaction. */
class OnlineOrderRepository(private val db: CatalogDatabase,private val afterWrite: ()->Unit={}) {
    suspend fun records()=db.workspace().activeOnlineOrders().map {OnlineOrderCodec.decode(it.payload).single()}
    suspend fun record(session: String)=db.workspace().document(OnlineOrderCodec.PREFIX+session)?.let {OnlineOrderCodec.decode(it.payload).single()}
    internal suspend fun markPaid(session: String): Boolean=record(session)?.let {save(listOf(it.copy(paid=true)));true} ?: false
    suspend fun requirePayable(session: String) {
        record(session)?.let {r->
            if(!r.accepted || r.cancelPending || r.cancelled || r.conflict.isNotEmpty()) throw CatalogFailure("onlinePending","Онлайн-заказ ещё не подтверждён или ожидает отмены. Оплата остановлена")
        }
    }
    suspend fun requireLocalRemovalAllowed(session: String) {
        if(record(session)?.cancelled==false) throw CatalogFailure("onlineOrder","Отмените онлайн-заказ через События, чтобы обновить статус на сайте; объединение онлайн-чеков пока недоступно")
    }
    private suspend fun save(rows: List<OnlineAcceptance>) {
        rows.forEach {row->
            val id=OnlineOrderCodec.PREFIX+row.sessionId
            val doc=db.workspace().document(id)
            val raw=OnlineOrderCodec.encode(listOf(row));OnlineOrderCodec.decode(raw)
            db.workspace().put(WorkspaceDocument(id,Math.addExact(doc?.revision ?: 0,1),raw));afterWrite()
        }
    }
    suspend fun localExists(record: OnlineAcceptance): Boolean = db.workspace().document("parked:${record.sessionId}")!=null ||
        db.workspace().document("current")?.let {WorkspaceCodec.cart(it.payload).sessionId==record.sessionId}==true || db.sales().session(record.sessionId)!=null
    suspend fun accept(order: JSONObject,target: String,dataset: String,estimate: String): OnlineAcceptance=InstallationGate.write {db.withTransaction {
        require(OnlineOrderCodec.estimate(estimate)) {"Выберите время готовности"}
        val orderId=order.getString("id")
        require(Regex("[A-Za-z0-9_-]{1,100}").matches(orderId) && Regex("[0-9a-f]{64}").matches(target))
        val session=OnlineOrderCodec.session(target,orderId)
        record(session)?.let {return@withTransaction it}
        val state=WorkspaceRepository(db).snapshot()
        require(state.parked.size<100) {"Достигнут предел 100 отложенных заказов"}
        val items=order.getJSONArray("order_items");require(items.length() in 1..500)
        val lines=List(items.length()) {i->val item=items.getJSONObject(i)
            val external=item.optString("external_product_id").takeUnless {it.isBlank() || it=="null"}
            val matches=if(external!=null) state.products.filter {it.id==external || it.id==WebIdentity.mapped("product",external)} else state.products.filter {it.name==item.getString("product_name")}
            require(matches.size==1) {"Товар сайта не найден или неоднозначен: ${item.optString("product_name")}"}
            val p=matches.single();val quantity=CatalogRules.decimal(item.get("quantity").toString(),9,"Количество");require(quantity.toBigDecimal().signum()>0)
            CartEngine.quoted(p.id,item.get("price").toString(),"$session:$i",state.products,state.graph)
                .copy(quantity=quantity,name=item.optString("product_name",p.name),comment=item.optString("comment").takeUnless {it=="null"}.orEmpty())
        }
        fun money(key: String)=order.opt(key)?.toString()?.takeUnless {it=="null"}?.toBigDecimal()?.movePointRight(2)?.longValueExact() ?: 0L
        val fee=money("delivery_fee");require(fee>=0)
        val type=when(order.optString("order_type").lowercase()) {"доставка","delivery"->"delivery";"pickup","самовывоз","с собой"->"takeaway";else->"here"}
        require(type=="delivery" || fee==0L)
        val delivery=if(type=="delivery") DeliveryCharge(order.optString("delivery_tariff_id").takeUnless {it.isBlank() || it=="null"} ?: "web-$orderId",
            order.optString("delivery_tariff_name").takeUnless {it.isBlank() || it=="null"} ?: "Доставка с сайта",fee,maxOf(1,order.optLong("delivery_tariff_version",1))) else null
        val quote=SaleMoney.quote(lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity)},type,delivery)
        require(quote.total==money("total")) {"Сумма заказа сайта не совпадает с его составом. Принятие не выполнено"}
        val used=lines.flatMap {it.references.keys}.toSet()
        val external=order.optString("external_id").takeUnless {it.isBlank() || it=="null"} ?: orderId
        val label=OrderRules.uniqueLabel("Веб $external",state.parked.map {it.cart.details.label})
        val details=OrderRules.details(OrderDetails(label,type,order.optString("comment").takeUnless {it=="null"}.orEmpty(),
            order.optString("address").takeUnless {it=="null"}.orEmpty(),order.optString("phone").takeUnless {it=="null"}.orEmpty(),
            order.optString("customer_name").takeUnless {it=="null"}.orEmpty(),delivery,true))
        val cart=CurrentCart(lines,state.graph.filter {it.id in used},session,System.currentTimeMillis(),details)
        WorkspaceCodec.cart(WorkspaceCodec.cart(cart)) // Validate all persisted text and exact decimals before any write.
        val reservations=OrderReservations.total(state.parked.map {it.cart}+listOf(state.cart))
        require(RecipeCalculator(cart.graph).shortages(CartEngine.requests(cart),state.balances,reservations).isEmpty()) {"Недостаточно остатков для заказа сайта"}
        val parked=ParkedOrder(session,state.ordersRevision+1,cart,System.currentTimeMillis())
        db.workspace().put(WorkspaceDocument("parked:$session",parked.revision,WorkspaceCodec.parked(parked)));afterWrite()
        db.workspace().put(WorkspaceDocument("orders",state.ordersRevision+1,"{\"version\":1}"));afterWrite()
        val record=OnlineAcceptance(orderId,external,session,target,dataset,estimate)
        save(listOf(record))
        record
    }}
    suspend fun update(session: String,change: (OnlineAcceptance)->OnlineAcceptance)=InstallationGate.write {db.withTransaction {save(listOf(change(record(session) ?: error("Онлайн-заказ не найден"))))}}
    /** Explicit administration save after restore; never migrate jobs to a different backend identity. */
    suspend fun rebind(target: String,dataset: String,generation: Long)=kotlinx.coroutines.withContext(com.mendelev.mpos.core.access.AdminAuthorization(com.mendelev.mpos.core.access.Administration.session,generation)) {InstallationGate.write {db.adminTransaction {
        save(records().map {if(it.target==target && it.dataset!=dataset && localExists(it)) it.copy(dataset=dataset) else it})
    }}}
    suspend fun markReady(session: String)=InstallationGate.write {db.withTransaction {
        val record=record(session) ?: error("Онлайн-заказ не найден")
        check(localExists(record)) {"Локальный заказ не найден"}
        check(record.accepted) {"Дождитесь подтверждения принятия на сервере"}
        check(!record.cancelPending && !record.cancelled && record.conflict.isEmpty()) {"Заказ отменён или ожидает отмены"}
        save(listOf(record.copy(ready=true)))
    }}
    suspend fun cancel(session: String)=InstallationGate.write {db.withTransaction {
        SplitPaymentRepository(db).requireNone()
        check(db.sales().session(session)==null) {"Оплаченный чек отменяется через возврат"}
        val record=record(session) ?: error("Онлайн-заказ не найден")
        if(!record.cancelled) save(listOf(record.copy(cancelPending=true)))
    }}
    suspend fun finishCancel(session: String)=InstallationGate.write {db.withTransaction {
        SplitPaymentRepository(db).requireNone()
        check(db.sales().session(session)==null) {"Чек уже оплачен"}
        val record=record(session) ?: error("Онлайн-заказ не найден");check(record.cancelPending)
        db.workspace().delete("parked:$session");afterWrite()
        val current=db.workspace().document("current")
        if(current!=null && WorkspaceCodec.cart(current.payload).sessionId==session) {
            db.workspace().put(WorkspaceDocument("current",current.revision+1,WorkspaceCodec.cart(CurrentCart(sessionId=UUID.randomUUID().toString()))));afterWrite()
        }
        val registry=db.workspace().document("orders")!!
        db.workspace().put(registry.copy(revision=registry.revision+1));afterWrite()
        save(listOf(record.copy(cancelPending=false,cancelled=true)))
    }}
}
