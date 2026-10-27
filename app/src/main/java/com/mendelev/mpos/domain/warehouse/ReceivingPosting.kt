package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*

/** Command captures the saved invoice and the complete economic baseline approved by the operator. */
data class ReceivingPostCommand(val commandId: String,val actorId: String,val revision: Long,val draft: ReceivingDraft,val overageConfirmed: Boolean=false) {
    fun validate() {
        PurchaseDraftRules.id(commandId);PurchaseDraftRules.id(actorId)
        if(revision<1) throw CatalogFailure("receiving","Сначала сохраните черновик")
        ReceivingRules.validate(draft)
    }
    fun fingerprint(): String {
        validate()
        return CatalogRules.fingerprint("receiving.post.v1",ReceivingCommand("payload",actorId,draft.order.id,revision,draft).fingerprint(),overageConfirmed)
    }
}
/** Immutable posting snapshot. Current product metadata is never used to reconstruct history. */
data class PostedReceiving(val id: String,val commandId: String,val actorId: String,val at: Long,val draftRevision: Long,val draft: ReceivingDraft,val overageConfirmed: Boolean,
    val result: ReceivingPreview=ReceivingPostingRules.preview(draft,draft.order,draft.lines.map {it.product})) {
    fun command()=ReceivingPostCommand(commandId,actorId,draftRevision,draft,overageConfirmed)
}

/** Policy v1: exact stock; final cost scale12 HALF_EVEN, refusing positive-value underflow. */
object ReceivingPostingRules {
    fun preview(d: ReceivingDraft,o: PurchaseOrder,products: List<ReceivingProduct>): ReceivingPreview {
        val result=ReceivingRules.preview(d,o,products)
        result.lines.forEach {l->
            if(l.actual.toBigDecimal().signum()>0 && l.newCost.toBigDecimal().signum()==0 &&
                (l.totalMinor>0 || (!l.noStockTracking && l.oldStock.toBigDecimal().signum()>0 && l.oldCost.toBigDecimal().signum()>0)))
                throw CatalogFailure("precision","Себестоимость «${l.name}» слишком мала для точности склада. Приход не проведён")
        }
        return result
    }
    fun validate(r: PostedReceiving) {
        PurchaseDraftRules.id(r.id);require(r.at>=0);r.command().validate()
        val p=preview(r.draft,r.draft.order,r.draft.lines.map {it.product})
        require(r.result==p) {"Изменения прихода не соответствуют сохранённым фактам"}
        if(p.hasOverage && !r.overageConfirmed) throw CatalogFailure("overage","Подтвердите превышение заказа")
    }
}
