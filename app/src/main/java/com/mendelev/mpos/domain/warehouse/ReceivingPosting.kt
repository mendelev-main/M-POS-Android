package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*

/** Command captures the saved invoice and the complete economic baseline approved by the operator. */
data class ReceivingPostCommand(val commandId: String,val actorId: String,val revision: Long,val draft: ReceivingDraft,val overageConfirmed: Boolean=false,val costPolicy: Int=2) {
    fun validate() {
        PurchaseDraftRules.id(commandId);PurchaseDraftRules.id(actorId);require(costPolicy in 1..2)
        if(revision<1) throw CatalogFailure("receiving","Сначала сохраните черновик")
        ReceivingRules.validate(draft)
    }
    fun fingerprint(): String {
        validate()
        return CatalogRules.fingerprint("receiving.post.v$costPolicy",ReceivingCommand("payload",actorId,draft.order.id,revision,draft).fingerprint(),overageConfirmed)
    }
}
/** Immutable posting snapshot. Current product metadata is never used to reconstruct history. */
data class PostedReceiving(val id: String,val commandId: String,val actorId: String,val at: Long,val draftRevision: Long,val draft: ReceivingDraft,val overageConfirmed: Boolean,
    val costPolicy: Int=2,val result: ReceivingPreview=ReceivingPostingRules.preview(draft,draft.order,draft.lines.map {it.product},costPolicy)) {
    fun command()=ReceivingPostCommand(commandId,actorId,draftRevision,draft,overageConfirmed,costPolicy)
}

/** Current policy2: final cost2 HALF_UP. Policy1 is retained for immutable history/replay. */
object ReceivingPostingRules {
    fun preview(d: ReceivingDraft,o: PurchaseOrder,products: List<ReceivingProduct>,costPolicy: Int=2): ReceivingPreview {
        require(costPolicy in 1..2)
        val result=if(costPolicy==1) ReceivingRules.preview(d,o,products) else ReceivingRules.preview(d,o,products,2,java.math.RoundingMode.HALF_UP)
        result.lines.forEach {l->
            if(costPolicy==1 && l.actual.toBigDecimal().signum()>0 && l.newCost.toBigDecimal().signum()==0 &&
                (l.totalMinor>0 || (!l.noStockTracking && l.oldStock.toBigDecimal().signum()>0 && l.oldCost.toBigDecimal().signum()>0)))
                throw CatalogFailure("precision","Себестоимость «${l.name}» слишком мала для точности склада. Приход не проведён")
        }
        return result
    }
    fun validate(r: PostedReceiving) {
        PurchaseDraftRules.id(r.id);require(r.at>=0);r.command().validate()
        val p=preview(r.draft,r.draft.order,r.draft.lines.map {it.product},r.costPolicy)
        require(r.result==p) {"Изменения прихода не соответствуют сохранённым фактам"}
        if(p.hasOverage && !r.overageConfirmed) throw CatalogFailure("overage","Подтвердите превышение заказа")
    }
}
