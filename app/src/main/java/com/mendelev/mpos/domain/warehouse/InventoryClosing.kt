package com.mendelev.mpos.domain.warehouse

import com.mendelev.mpos.domain.catalog.*
import java.math.RoundingMode
import java.time.LocalDate

/** Null amounts mean a reporting-range overflow, never a zero valuation. */
data class InventorySummary(val shortages: Int,val surpluses: Int,val fixed: Int,val remaining: Int,val lossMinor: Long?)
data class InventoryRecord(val draft: InventoryDraft,val cancelled: Boolean,val at: Long,val date: String,val actorId: String,val commandId: String,val lossMinor: Long?,val policy: Int=1) {
    val id get()=draft.id
}
object InventoryClosingRules {
    fun lineLoss(count: InventoryCount): Long? {
        val f=count.fixed ?: return 0L
        val delta=f.delta.toBigDecimal()
        if(delta.signum()>=0) return 0L
        val value=(-delta*f.product.cost.toBigDecimal()).setScale(2,RoundingMode.HALF_UP)
        return try {value.movePointRight(2).longValueExact()} catch(_:ArithmeticException) {null}
    }
    fun summary(d: InventoryDraft): InventorySummary {
        val fixed=d.counts.filter {it.fixed!=null};var total: Long?=0
        fixed.forEach {c->val amount=lineLoss(c);total=if(total==null || amount==null) null else try {Math.addExact(total!!,amount)} catch(_:ArithmeticException) {null}}
        return InventorySummary(fixed.count {it.fixed!!.delta.toBigDecimal().signum()<0},fixed.count {it.fixed!!.delta.toBigDecimal().signum()>0},fixed.size,d.products.size-fixed.size,total)
    }
    fun validate(r: InventoryRecord) {
        InventoryRules.validate(InventoryDocument(draft=r.draft));require(r.at>=0 && r.policy==1);require(LocalDate.parse(r.date).toString()==r.date)
        PurchaseDraftRules.id(r.actorId);PurchaseDraftRules.id(r.commandId)
        val s=summary(r.draft)
        if(!r.cancelled && s.remaining!=0) throw CatalogFailure("inventory","Сначала зафиксируйте остаток каждого товара")
        require(r.lossMinor==s.lossMinor)
    }
}
