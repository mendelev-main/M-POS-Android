package com.mendelev.mpos.domain.backup

import java.math.BigDecimal
import java.math.RoundingMode

/** Frozen source evidence and a comparison calculation, never a native checkout or permission to charge. */
data class WebOpenDiscount(val sourceId: String,val name: String?,val type: String,val value: String)
data class WebOpenModifier(val sourceProductId: String?,val groupId: String?,val optionId: String?,val groupName: String?,val name: String?,val quantity: String?,val priceDelta: String?)
data class WebOpenLine(val position: Int,val sourceLineId: String?,val sourceProductId: String?,val name: String?,
    val quantity: String?,val price: String?,val category: String?,val unit: String?,val basePrice: String?,val manualPrice: Boolean?,val comment: String?,
    val discount: WebOpenDiscount?,val modifiers: List<WebOpenModifier>,val comparedNet: Long?,val blockers: Set<String>)
data class WebOpenOperation(val origin: String,val location: String,val sourceId: String?,val createdAt: Long?,val updatedAt: Long?,
    val orderType: String?,val label: String?,val comment: String?,val sourceActorName: String?,val sourceTimestamp: Long?,val lines: List<WebOpenLine>,val delivery: Long?,
    val comparedTotal: Long?,val savedTotal: Long?,val payment: WebPaymentFacts?,val candidateShiftId: String?,
    val candidateActorId: String?,val blockers: Set<String>) {
    // No customer/contact/cart snapshots in logs.
    override fun toString()="WebOpenOperation(location=$location, lines=${lines.size}, blockers=${blockers.size})"
}

/** 07.4d.1: compare frozen input to paid total; sourceFile+location provenance is NOT a payment command ID. */
object WebOpenOperations {
    fun map(document: WebBackupDocument): List<WebOpenOperation> {
        val root=document.root.fields
        val payments=WebPaymentMapping.map(document).associateBy {it.location}
        val staff=WebStaffMapping.map(document)
        val history=WebReceiptHistory.map(document)
        val shifts=staff.shifts.filter {it.status=="open"}
        val discounts=WebValues.rows(root["discounts"]).map {WebValues.obj(it)}.associateUnique {WebIdentity.source(it["id"])}
        val sessions=buildList {
            (root["currentOrderSession"] as? BackupJson.Obj)?.let {add("current" to it)}
            WebValues.rows(root["parked"]).forEachIndexed {i,v->add("parked:$i" to (v as? BackupJson.Obj ?: backupBad("openSession")))}
        }
        val seenParked=mutableSetOf<String>()
        return sessions.mapNotNull {(location,session)->
            val p=session.fields;val payment=payments[location]
            val rows=WebValues.rows(p["items"])
            if(rows.isEmpty() && payment==null)return@mapNotNull null
            val b=linkedSetOf("operationalCartMappingPending","cashHistoryReconciliationPending")
            payment?.blockers?.let(b::addAll)
            if(payment!=null)b.add("sourceQuoteNotPersisted")
            if(!text(p["webOrderId"]).isNullOrEmpty())b.add("onlineOrderCompletionPending")
            val sourceId=reference(p["id"])
            if(location!="current") {
                if(sourceId==null)b.add("parkedIdentityUnknown") else if(!seenParked.add(sourceId))backupBad("openIdentity")
                if(payment!=null)b.add("parkedPaidDraftNotProducedByPinnedWeb")
            }
            val currency=p["currency"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.text(it)}
            if(currency!=null && currency!="BYN")backupBad("openCurrency")
            val created=WebValues.time(p["createdAt"])?.takeUnless {it==0L}
            val draft=p["paymentDraft"]?.takeUnless {it===BackupJson.Nil}?.let(WebValues::obj)
            val updated=WebValues.time(draft?.get("updatedAt") ?: p["updatedAt"])
            if(created!=null && updated!=null && updated<created)backupBad("openTime")
            val selected=WebValues.obj(p["loyaltyRedemptions"]?.takeUnless {it===BackupJson.Nil} ?: BackupJson.Obj(emptyMap())).values.any {WebValues.decimal(it).signum()>0}
            if(selected)b.add("loyaltyQuoteUnavailable")
            val lines=rows.mapIndexed {i,v->line(i,WebValues.obj(v),discounts)}
            val lineIds=lines.mapNotNull {it.sourceLineId};if(lineIds.distinct().size!=lineIds.size)backupBad("openIdentity")
            lines.forEach {b.addAll(it.blockers)}
            if(lines.isEmpty())b.add("paidSessionHasNoItems")
            val type=text(p["orderType"])
            if(type !in setOf("На месте","С собой","Доставка"))b.add("orderTypeUnknown")
            val rawDelivery=WebValues.minor(p["deliveryFee"])
            val delivery=when(type) {"На месте","С собой"->0L;"Доставка"->rawDelivery;else->null}
            if(type=="Доставка" && (delivery==null || !WebValues.bool(p["deliveryTariffSelected"])))b.add("deliveryTariffUnconfirmed")
            val total=if(lines.isNotEmpty() && lines.all {it.comparedNet!=null} && delivery!=null && !selected) {
                add(lines.fold(0L) {a,l->add(a,l.comparedNet!!)},delivery)
            } else null
            // current session has no saved total apart from paymentDraft. Parked total is saved independently.
            val parkedTotal=if(location!="current")WebValues.minor(p["total"]) else null
            val saved=payment?.total ?: parkedTotal
            if(total==null)b.add("quoteUnavailable")
            if(total!=null && saved!=null && total!=saved)b.add("quoteTotalMismatch")
            if(payment!=null && parkedTotal!=null && parkedTotal!=payment.total)b.add("savedTotalsConflict")
            val shift=shifts.singleOrNull()
            if(shift==null)b.add("openShiftUnavailable") else {
                b.add("shiftBindingInferred") // session has no durable shiftId/employeeId in pinned Web.
                b.addAll(shift.blockers.map {"shift:$it"})
                if(payment!=null && (created==null || shift.openedAt==null || created<shift.openedAt))b.add("sessionShiftTimeUnproven")
                if(payment!=null && (updated==null || shift.openedAt==null || updated<shift.openedAt))b.add("paymentShiftTimeUnproven")
            }
            // A session lacks a durable finalized receipt link. Equality is never proof to discard accepted money.
            if(payment!=null && history.any {it.sourceShiftId==shift?.sourceId && it.total==payment.total})b.add("possibleCompletedReceiptOverlap")
            val key="web-open-"+WebLegacyHistoryArchiveCodec.digest(document.preview.sha256+"\n"+location+"\n"+(sourceId ?: ""))
            WebOpenOperation(key,location,sourceId,created,updated,type,text(p["orderLabel"]),text(p["orderComment"] ?: p["comment"]),
                text(p["employeeName"]),WebValues.time(p["timestamp"]),lines,delivery,total,saved,payment,shift?.id,shift?.actorId,b.toSet())
        }
    }
    private fun line(i: Int,p: Map<String,BackupJson>,discounts: Map<String,Map<String,BackupJson>>): WebOpenLine {
        val b=linkedSetOf<String>()
        val qty=number(p["qty"]);if(qty!=null && qty.toBigDecimal().signum()<=0)backupBad("openQuantity")
        val price=number(p["price"])
        if(qty==null || price==null)b.add("linePriceOrQuantityUnknown")
        if(qty!=null && qty.toBigDecimal().stripTrailingZeros().scale()>12)b.add("quantityPrecisionNeedsReconciliation")
        val product=reference(p["productId"]);if(product==null)b.add("productReferenceUnknown")
        val name=text(p["name"]);if(name.isNullOrBlank())b.add("lineNameUnknown")
        val discountId=reference(p["discountId"])
        val definition=discountId?.let(discounts::get)
        val discount=definition?.let {d->
            val type=WebValues.text(d["type"]);if(type !in setOf("percent","fixed"))backupBad("openDiscount")
            WebOpenDiscount(discountId,text(d["name"]),type,number(d["value"]) ?: backupBad("openDiscount"))
        }
        if(discountId!=null && discount==null)b.add("discountDefinitionUnknown")
        val modifiers=WebValues.rows(p["selectedModifiers"]).map {v->val m=WebValues.obj(v)
            val quantity=number(m["qty"]);if(quantity!=null && quantity.toBigDecimal().signum()<=0)backupBad("openModifier")
            val modifier=WebOpenModifier(reference(m["productId"]),reference(m["groupId"]),reference(m["optionId"]),text(m["groupName"]),text(m["name"]),quantity,number(m["priceDelta"],true))
            if(modifier.sourceProductId==null || modifier.groupId==null || modifier.optionId==null || quantity==null || modifier.priceDelta==null)b.add("modifierSnapshotIncomplete")
            modifier
        }
        val modifierIds=modifiers.mapNotNull {m->m.optionId?.let {m.groupId to it}};if(modifierIds.distinct().size!=modifierIds.size)backupBad("openModifier")
        val net=if(qty!=null && price!=null && (discountId==null || discount!=null)) {
            val q=qty.toBigDecimal();val gross=price.toBigDecimal()*q
            val reduction=when(discount?.type) {"percent"->(gross*discount.value.toBigDecimal().min(BigDecimal("100"))).movePointLeft(2);"fixed"->(discount.value.toBigDecimal()*q).min(gross);else->BigDecimal.ZERO}
            cents(gross-reduction)
        } else null
        return WebOpenLine(i,reference(p["cartLineId"]),product,name,qty,price,text(p["category"]),text(p["unit"]),number(p["basePrice"]),p["manualPrice"]?.takeUnless {it===BackupJson.Nil}?.let {WebValues.bool(it)},text(p["comment"]),discount,modifiers,net,b.toSet())
    }
    private fun number(v: BackupJson?,signed: Boolean=false): String?=v?.takeUnless {it===BackupJson.Nil}?.let {WebValues.decimal(it,signed=signed).stripTrailingZeros().toPlainString()}
    private fun text(v: BackupJson?): String?=v?.takeUnless {it===BackupJson.Nil}?.let {WebValues.text(it)}
    private fun reference(v: BackupJson?): String?=v?.takeUnless {it===BackupJson.Nil || it is BackupJson.Str && it.value.isEmpty()}?.let(WebIdentity::source)
    private fun cents(v: BigDecimal): Long=try {v.setScale(2,RoundingMode.HALF_UP).movePointRight(2).longValueExact()} catch(_:ArithmeticException) {backupBad("openMoney")}
    private fun add(a: Long,b: Long): Long=try {Math.addExact(a,b)} catch(_:ArithmeticException) {backupBad("openMoney")}
    private fun <T> List<T>.associateUnique(key: (T)->String): Map<String,T> {
        val map=associateBy(key);if(map.size!=size)backupBad("openIdentity");return map
    }
}
