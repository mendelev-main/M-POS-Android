package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.core.operations.InstallationGate
import com.mendelev.mpos.domain.loyalty.*
import com.mendelev.mpos.domain.sale.SaleDiscount
import com.mendelev.mpos.domain.workspace.CurrentCart
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class LoyaltyEffect(val receiptId: String,val createdAt: Long,val target: String,val dataset: String,val body: String,
    val saleDone: Boolean=false,val reverseRequested: Boolean=false,val reversalDone: Boolean=false,val blocked: String="",val result: String="") {
    val finished: Boolean get()=blocked.isNotEmpty() || saleDone && (!reverseRequested || reversalDone)
}
object LoyaltyEffectCodec {
    const val PENDING="loyalty-pending:"
    const val RESULT="loyalty-result:"
    fun owns(id: String)=id.startsWith(PENDING) || id.startsWith(RESULT)
    fun key(row: LoyaltyEffect)=(if(row.finished) RESULT else PENDING)+suffix(row.createdAt,row.receiptId)
    fun suffix(time: Long,id: String)=time.toString().padStart(19,'0')+":"+id
    fun encode(row: LoyaltyEffect)=JSONObject().put("version",1).put("receiptId",row.receiptId).put("createdAt",row.createdAt)
        .put("target",row.target).put("dataset",row.dataset).put("body",row.body).put("saleDone",row.saleDone)
        .put("reverseRequested",row.reverseRequested).put("reversalDone",row.reversalDone).put("blocked",row.blocked).put("result",row.result).toString()
    fun decode(raw: String): LoyaltyEffect {
        val j=JSONObject(raw);require(j.getInt("version")==1)
        return LoyaltyEffect(j.getString("receiptId"),j.getLong("createdAt"),j.getString("target"),j.getString("dataset"),j.getString("body"),j.getBoolean("saleDone"),j.getBoolean("reverseRequested"),j.getBoolean("reversalDone"),j.getString("blocked"),j.getString("result")).also {r->
            require(Regex("[A-Za-z0-9_-]{1,100}").matches(r.receiptId) && r.createdAt>=0 && Regex("[0-9a-f]{64}").matches(r.target))
            require(r.dataset.length in 1..100 && r.dataset.all {it.isLetterOrDigit() || it in "-_"} && r.blocked.length<=100 && r.result.length<=1_000_000)
            require(!r.reversalDone || r.saleDone && r.reverseRequested)
            require(r.body.length<=1_000_000)
            val body=JSONObject(r.body);require(body.getString("orderId")==r.receiptId && body.getString("customerId").length in 1..100)
            require(body.getJSONArray("items").length() in 1..500)
        }
    }
    fun body(receiptId: String,time: Long,cart: CurrentCart,discounts: Map<String,SaleDiscount>): String {
        val selection=cart.loyalty ?: error("Клиент чека не сохранён")
        val gifts=LoyaltyRules.allocation(cart,discounts).gifts
        val redemptions=JSONObject();val allocations=JSONObject()
        gifts.forEach {gift->redemptions.put(gift.programId,1);allocations.put(gift.programId,JSONArray().put(JSONObject().put("productId",gift.productId).put("quantity",1)))}
        return JSONObject().put("orderId",receiptId).put("customerId",selection.customer.id)
            .put("consentVersion",if(selection.customer.active) selection.customer.consentVersion else JSONObject.NULL)
            .put("occurredAt",Instant.ofEpochMilli(time).toString())
            .put("items",JSONArray(cart.lines.map {line->JSONObject().put("productId",LoyaltyRules.externalId(cart,line.productId)).put("quantity",line.quantity.toBigDecimal()).put("unitPrice",line.unitPrice.toBigDecimal())}))
            .put("redemptions",redemptions).put("rewardAllocations",allocations).toString()
    }
}

class LoyaltyOutbox(private val db: CatalogDatabase) {
    private suspend fun document(receipt: ReceiptEntity): WorkspaceDocument? {
        val suffix=LoyaltyEffectCodec.suffix(receipt.createdAt,receipt.id)
        return db.workspace().document(LoyaltyEffectCodec.PENDING+suffix) ?: db.workspace().document(LoyaltyEffectCodec.RESULT+suffix)
    }
    /** Caller is the sale transaction; no network before this intent and receipt commit together. */
    suspend fun enqueue(receipt: ReceiptEntity,cart: CurrentCart,discounts: Map<String,SaleDiscount>): Boolean {
        check(db.inTransaction()) {"Лояльность сохраняется только вместе с продажей"}
        val selection=cart.loyalty ?: return false
        require(selection.datasetId.isNotBlank()) {"Выберите клиента заново перед оплатой"}
        check(document(receipt)==null)
        val effect=LoyaltyEffect(receipt.id,receipt.createdAt,selection.target,selection.datasetId,LoyaltyEffectCodec.body(receipt.id,receipt.createdAt,cart,discounts))
        db.workspace().put(WorkspaceDocument(LoyaltyEffectCodec.key(effect),1,LoyaltyEffectCodec.encode(effect)))
        return true
    }
    suspend fun read(receiptId: String): LoyaltyEffect?=db.sales().receipt(receiptId)?.let {document(it)?.let {d->LoyaltyEffectCodec.decode(d.payload)}}
    suspend fun pending(target: String,dataset: String,limit: Int=10): List<LoyaltyEffect> = db.workspace().pendingLoyalty(target,dataset,limit).map {LoyaltyEffectCodec.decode(it.payload)}
    suspend fun update(receiptId: String,change: (LoyaltyEffect)->LoyaltyEffect)=InstallationGate.write {db.withTransaction {updateInside(receiptId,change)}}
    private suspend fun updateInside(receiptId: String,change: (LoyaltyEffect)->LoyaltyEffect) {
        val receipt=db.sales().receipt(receiptId) ?: error("Чек лояльности не найден")
        val document=document(receipt) ?: error("Операция лояльности не найдена")
        val old=LoyaltyEffectCodec.decode(document.payload);val next=change(old)
        require(next.receiptId==old.receiptId && next.createdAt==old.createdAt && next.target==old.target && next.body==old.body)
        LoyaltyEffectCodec.decode(LoyaltyEffectCodec.encode(next))
        val key=LoyaltyEffectCodec.key(next)
        if(key!=document.id) db.workspace().delete(document.id)
        db.workspace().put(WorkspaceDocument(key,Math.addExact(document.revision,1),LoyaltyEffectCodec.encode(next)))
    }
    /** Called inside the full-refund transaction. A pending sale is sent before its reversal. */
    suspend fun reverse(receiptId: String): Boolean {
        check(db.inTransaction()) {"Отмена лояльности сохраняется только вместе с возвратом"}
        if(read(receiptId)==null) return false
        updateInside(receiptId) {it.copy(reverseRequested=true)}
        return true
    }
    suspend fun rebind(target: String,dataset: String,generation: Long) = kotlinx.coroutines.withContext(com.mendelev.mpos.core.access.AdminAuthorization(com.mendelev.mpos.core.access.Administration.session,generation)) {InstallationGate.write {db.adminTransaction {
        db.workspace().documentsByPrefix(LoyaltyEffectCodec.PENDING).forEach {document->val row=LoyaltyEffectCodec.decode(document.payload)
            if(row.target==target && row.dataset!=dataset) updateInside(row.receiptId) {it.copy(dataset=dataset)}
        }
    }}}
}
