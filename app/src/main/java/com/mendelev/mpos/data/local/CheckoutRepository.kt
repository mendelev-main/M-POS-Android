package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.sale.*
import org.json.JSONArray
import org.json.JSONObject

/** Prepared manual accounting inputs, never evidence of a bank charge or a committed receipt. */
data class CheckoutDraft(val commandId: String,val sessionId: String,val cartRevision: Long,
    val shiftId: String,val shiftRevision: Long,val actorId: String,
    val discounts: Map<String,SaleDiscount> = emptyMap(),val delivery: DeliveryCharge? = null,
    val parts: List<PaymentInput> = emptyList())
data class SavedCheckout(val revision: Long,val draft: CheckoutDraft)

object CheckoutCodec {
    fun encode(d: CheckoutDraft): String=JSONObject().put("version",1)
        .put("commandId",d.commandId).put("sessionId",d.sessionId).put("cartRevision",d.cartRevision)
        .put("shiftId",d.shiftId).put("shiftRevision",d.shiftRevision).put("actorId",d.actorId)
        .put("discounts",JSONArray().also {a->d.discounts.toSortedMap().forEach {(id,v)->a.put(JSONObject()
            .put("id",id).put("type",v.type).put("value",v.value).put("name",v.name))}})
        .put("delivery",d.delivery?.let {JSONObject().put("id",it.id).put("name",it.name).put("fee",it.fee).put("revision",it.revision)} ?: JSONObject.NULL)
        .put("parts",JSONArray().also {a->d.parts.forEach {a.put(JSONObject().put("method",it.method)
            .put("amount",it.amount).put("given",it.given ?: JSONObject.NULL))}}).toString()
    fun decode(raw: String): CheckoutDraft {
        val j=JSONObject(raw)
        if(j.getInt("version")!=1) throw CatalogFailure("checkout","Неизвестный формат оплаты")
        val discounts=linkedMapOf<String,SaleDiscount>()
        val ds=j.getJSONArray("discounts")
        require(ds.length()<=500)
        for(i in 0 until ds.length()) {val d=ds.getJSONObject(i);val id=d.getString("id")
            require(!discounts.containsKey(id));discounts[id]=SaleDiscount(d.getString("type"),d.getString("value"),d.getString("name"))}
        val ps=j.getJSONArray("parts");require(ps.length()<=10)
        return CheckoutDraft(j.getString("commandId"),j.getString("sessionId"),j.getLong("cartRevision"),
            j.getString("shiftId"),j.getLong("shiftRevision"),j.getString("actorId"),discounts,
            if(j.isNull("delivery")) null else j.getJSONObject("delivery").let {DeliveryCharge(it.getString("id"),it.getString("name"),it.getLong("fee"),it.getLong("revision"))},
            (0 until ps.length()).map {ps.getJSONObject(it).let {p->PaymentInput(p.getString("method"),p.getLong("amount"),if(p.isNull("given")) null else p.getLong("given"))}})
    }
}

/** Single current checkout document; revision survives discard, preventing ABA replacement. */
class CheckoutRepository(private val db: CatalogDatabase,private val afterWrite: (() -> Unit)? = null) {
    private val key="checkout"
    private fun conflict(): Nothing=throw CatalogFailure("conflict","Оплата или заказ изменились. Проверьте данные заново")
    suspend fun load(): SavedCheckout?=db.withTransaction {
        db.workspace().document(key)?.let {doc->
            val j=JSONObject(doc.payload)
            if(j.optBoolean("discarded",false)) {require(j.getInt("version")==1);null}
            else SavedCheckout(doc.revision,CheckoutCodec.decode(doc.payload))
        }
    }
    suspend fun revision(): Long=db.withTransaction {db.workspace().document(key)?.revision ?: 0}
    suspend fun save(expectedRevision: Long,draft: CheckoutDraft): SavedCheckout=saveInputs(expectedRevision,draft,false)
    /** Unpaid split inputs may lack tender. No paid flags or financial postings are created here. */
    suspend fun saveSplit(expectedRevision: Long,draft: CheckoutDraft): SavedCheckout=saveInputs(expectedRevision,draft,true)
    private suspend fun saveInputs(expectedRevision: Long,draft: CheckoutDraft,split: Boolean): SavedCheckout=InstallationGate.write {db.withTransaction {
        SplitPaymentRepository(db).requireNone()
        val old=db.workspace().document(key)
        if((old?.revision ?: 0)!=expectedRevision) conflict()
        require(expectedRevision in 0 until Long.MAX_VALUE)
        listOf(draft.commandId,draft.sessionId,draft.shiftId,draft.actorId).forEach {require(it.isNotBlank() && it.length<=100)}
        if(db.catalog().result(draft.commandId)!=null) conflict()
        val current=db.workspace().document("current") ?: conflict()
        val cart=WorkspaceCodec.cart(current.payload)
        if(current.revision!=draft.cartRevision || cart.sessionId!=draft.sessionId || cart.lines.isEmpty()) conflict()
        val state=ShiftRepository(db).snapshot()
        if(state.active?.actorId!=draft.actorId || state.active?.id!=draft.shiftId || state.revision!=draft.shiftRevision) conflict()
        val products=db.catalog().allProducts().associateBy {it.id}
        if(cart.lines.any {line->line.references.any {(id,rev)->products[id]?.revision!=rev}}) conflict()
        if(draft.discounts.keys.any {id->cart.lines.none {it.id==id}}) conflict()
        // A delivery tariff must be explicitly selected, including a free tariff.
        val quote=SaleMoney.quote(cart.lines.map {SaleLineInput(it.id,it.unitPrice,it.quantity,draft.discounts[it.id])},cart.details.type,draft.delivery)
        if(split) {
            require(draft.parts.size in 2..10)
            SaleMoney.payments(quote,draft.parts.map {if(it.method=="cash" && it.given==null) it.copy(given=it.amount) else it},state.currentCash)
        }
        require(draft.parts.size<=10)
        draft.parts.forEach {p->require(p.amount>=0 && when(p.method) {
            "cash"->if(split && p.given==null) true else p.given!=null && p.given>=p.amount
            "card"->p.given==null
            else->false
        })}
        val next=SavedCheckout(expectedRevision+1,draft)
        db.workspace().put(WorkspaceDocument(key,next.revision,CheckoutCodec.encode(draft)));afterWrite?.invoke()
        next
    }}
    /** Does not clear the cart and cannot cancel any externally executed payment. */
    suspend fun discard(expectedRevision: Long)=InstallationGate.write {db.withTransaction {
        SplitPaymentRepository(db).requireNone()
        val old=db.workspace().document(key) ?: conflict()
        if(old.revision!=expectedRevision) conflict()
        require(expectedRevision<Long.MAX_VALUE)
        db.workspace().put(WorkspaceDocument(key,expectedRevision+1,"{\"version\":1,\"discarded\":true}"));afterWrite?.invoke()
    }}
}
