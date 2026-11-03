package com.mendelev.mpos.domain.backup

/** Evidence only. These IDs are not sale/refund command IDs; importing never posts stock or money. */
data class WebHistoricalPayment(val method: String,val amount: Long,val given: Long?,val change: Long?)
data class WebHistoricalLine(val position: Int,val sourceProductId: String?,val name: String?,val category: String?,
    val quantity: String?,val price: String?,val cost: String?,val unit: String?,val deferredFields: Set<String>)
data class WebHistoricalConsumption(val sourceProductId: String,val quantity: String,val unit: String?)
data class WebHistoricalReceipt(val id: String,val sourceId: String,val timestamp: Long?,val sourceShiftId: String?,
    val sourceActorId: String?,val actorName: String?,val total: Long?,val delivery: Long?,
    val method: String?,val payments: List<WebHistoricalPayment>?,val lines: List<WebHistoricalLine>,val consumption: List<WebHistoricalConsumption>?,
    val returnedAt: Long?,val sourceReturnShiftId: String?,val returnAmount: Long?,val coverage: Set<String>,val deferredFields: Set<String>) {
    override fun toString()="WebHistoricalReceipt(lines=${lines.size}, coverage=${coverage.size})"
}
data class WebHistoricalEvent(val receiptId: String,val kind: String,val timestamp: Long,val amount: Long)
data class WebHistoricalGap(val receiptId: String,val kind: String,val reason: String)
data class WebHistoricalEvents(val events: List<WebHistoricalEvent>,val gaps: List<WebHistoricalGap>)

object WebReceiptHistory {
    private val known=setOf("id","timestamp","shiftId","employeeId","employeeName","total","method","payments","cashGiven","change",
        "deliveryFee","items","stockConsumption","returnedAt","returnedShiftId","returnAmount","currency")
    private fun reference(value: BackupJson?): String?=value?.takeUnless {it===BackupJson.Nil || it is BackupJson.Str && it.value.isEmpty()}?.let(WebIdentity::source)
    private fun text(value: BackupJson?): String?=value?.takeUnless {it===BackupJson.Nil}?.let {WebValues.text(it)}
    private fun number(value: BackupJson?): String?=value?.takeUnless {it===BackupJson.Nil}?.let {com.mendelev.mpos.domain.catalog.CatalogRules.canonical(WebValues.decimal(it))}
    private fun money(value: BackupJson?)=WebValues.minor(value)
    private fun sum(values: List<Long>): Long=try {values.fold(0L,Math::addExact)} catch(_:ArithmeticException) {backupBad("historyMoney")}
    fun map(document: WebBackupDocument): List<WebHistoricalReceipt> {
        val result=WebValues.rows(document.root.fields["orders"]).map {value->
            val p=WebValues.obj(value);val coverage=linkedSetOf<String>()
            val currency=text(p["currency"])
            if(currency!=null && currency!="BYN") backupBad("historyCurrency")
            if(currency==null) coverage.add("currencyFromImportProfile")
            val source=WebIdentity.source(p["id"]);val total=money(p["total"])
            if(total==null) coverage.add("totalUnknown")
            val time=WebValues.time(p["timestamp"]);if(time==null) coverage.add("saleTimeUnknown")
            val shift=reference(p["shiftId"]);if(shift==null) coverage.add("saleShiftUnknown")
            val actor=reference(p["employeeId"]);if(actor==null) coverage.add("saleActorUnknown")
            val actorName=text(p["employeeName"]);if(actorName.isNullOrBlank()) coverage.add("historicalActorNameUnknown")
            val delivery=money(p["deliveryFee"]);if(delivery==null) coverage.add("deliveryUnknown")
            if(total!=null && delivery!=null && delivery>total) backupBad("historyMoney")
            fun payment(row: Map<String,BackupJson>): WebHistoricalPayment {
                if(row["paid"]!=null && (row["paid"] as? BackupJson.Bool)?.value!=true) backupBad("historyPayment")
                val method=WebValues.text(row["method"]);if(method !in setOf("cash","card")) backupBad("historyPayment")
                val amount=money(row["amount"]) ?: backupBad("historyPayment")
                val given=money(row["cashGiven"]);val change=money(row["change"])
                if(method=="card" && (given!=null || change!=null) || given!=null && given<amount || given!=null && change!=null && given-amount!=change) backupBad("historyPayment")
                if(method=="cash" && (given==null || change==null)) coverage.add("cashTenderUnknown")
                if((row.keys-setOf("method","amount","cashGiven","change","paid")).isNotEmpty()) coverage.add("paymentMetadataNotMapped")
                return WebHistoricalPayment(method,amount,given,change)
            }
            val payments=when(val rows=p["payments"]) {
                null,BackupJson.Nil->{
                    val method=WebValues.text(p["method"])
                    if(method in setOf("cash","card") && total!=null) {
                        coverage.add("paymentFromLegacyHeader")
                        listOf(payment(mapOf("method" to BackupJson.Str(method),"amount" to BackupJson.Str(java.math.BigDecimal.valueOf(total,2).toPlainString()),
                            "cashGiven" to (p["cashGiven"] ?: BackupJson.Nil),"change" to (p["change"] ?: BackupJson.Nil))))
                    } else {coverage.add("paymentAllocationsUnknown");null}
                }
                is BackupJson.Arr->{if(rows.values.size !in 1..10) backupBad("historyPayment");rows.values.map {payment(WebValues.obj(it))}}
                else->backupBad("historyPayment")
            }
            if(payments!=null && total!=null && sum(payments.map {it.amount})!=total) backupBad("historyPayment")
            val method=text(p["method"])
            if(method !in setOf("cash","card","split")) coverage.add("paymentHeaderUnknown")
            if(payments!=null && (method in setOf("cash","card") && payments.any {it.method!=method} || method=="split" && payments.size<2)) backupBad("historyPayment")
            if(p["items"]==null || p["items"]===BackupJson.Nil) coverage.add("linesUnknown")
            if(WebValues.rows(p["items"]).isEmpty()) coverage.add("linesUnknown")
            val lines=WebValues.rows(p["items"]).mapIndexed {index,row->val f=WebValues.obj(row)
                val quantity=number(f["qty"]);if(quantity!=null && quantity.toBigDecimal().signum()<=0) backupBad("historyLine")
                if(quantity==null) coverage.add("lineQuantityUnknown")
                val price=number(f["price"]);val cost=number(f["cost"])
                if(price==null) coverage.add("linePriceUnknown")
                if(cost==null) coverage.add("lineCostUnknown") else coverage.add("sourceUnitCostOnly")
                if(listOfNotNull(quantity,price,cost).any {it.toBigDecimal().stripTrailingZeros().scale()>12}) coverage.add("legacyDecimalPrecision")
                val unit=text(f["unit"]);if(unit.isNullOrBlank()) coverage.add("lineUnitUnknown")
                if(unit!=null && unit.isNotEmpty() && unit !in setOf("piece","kg","g","l","ml")) coverage.add("legacyLineUnit")
                // No lookup of current category/name/cost/recipe, including deleted products.
                WebHistoricalLine(index,reference(f["productId"]),text(f["name"]),text(f["category"]),quantity,price,cost,unit,f.keys-setOf("productId","name","category","qty","price","cost","unit"))
            }
            coverage.add("nativeReceiptGraphUnavailable")
            coverage.add("lineDiscountAndModifierReconciliationPending")
            val consumption=p["stockConsumption"]?.takeUnless {it===BackupJson.Nil}?.let {snap->
                val f=WebValues.obj(snap)
                if(WebValues.time(f["version"])!=1L || f["items"] !is BackupJson.Arr) backupBad("historyConsumption")
                if((f.keys-setOf("version","items")).isNotEmpty()) coverage.add("consumptionMetadataNotMapped")
                val rows=WebValues.rows(f["items"]).map {r->val c=WebValues.obj(r)
                    if((c.keys-setOf("productId","qty","unit")).isNotEmpty()) coverage.add("consumptionMetadataNotMapped")
                    val qty=number(c["qty"]) ?: backupBad("historyConsumption")
                    if(qty.toBigDecimal().signum()<=0) backupBad("historyConsumption")
                    if(qty.toBigDecimal().stripTrailingZeros().scale()>12) coverage.add("legacyDecimalPrecision")
                    val unit=text(c["unit"]);if(unit.isNullOrBlank()) coverage.add("consumptionUnitUnknown")
                    WebHistoricalConsumption(WebIdentity.source(c["productId"]),qty,unit)
                }
                if(rows.map {it.sourceProductId}.distinct().size!=rows.size) backupBad("historyConsumption")
                rows
            }
            if(consumption==null) coverage.add("consumptionUnknown")
            val returned=WebValues.time(p["returnedAt"])?.takeUnless {it==0L}
            if(time!=null && returned!=null && returned<time) backupBad("historyRefund")
            val returnAmount=money(p["returnAmount"]);val returnShift=reference(p["returnedShiftId"])
            if(returned==null && (returnAmount!=null || returnShift!=null)) backupBad("historyRefund")
            if(returned!=null) {
                if(returnAmount==null) coverage.add("refundAmountUnknown")
                if(returnShift==null) coverage.add("refundShiftUnknown")
                if(returnAmount!=null && total!=null && returnAmount!=total) coverage.add("partialOrMismatchedRefund")
                coverage.add("refundCashMovementReconciliationPending")
                if(delivery!=null && delivery>0) coverage.add("deliveryRefundReconciliationPending")
            }
            WebHistoricalReceipt(WebIdentity.mapped("receipt",source),source,time,shift,actor,actorName,total,delivery,method,payments,lines,consumption,
                returned,returnShift,returnAmount,coverage,p.keys-known)
        }
        if(result.map {it.id}.distinct().size!=result.size) backupBad("identity")
        return result
    }
    /** Owner-approved event-date policy; gaps travel with events and must not become a complete zero report. */
    fun events(receipts: List<WebHistoricalReceipt>): WebHistoricalEvents {
        if(receipts.map {it.id}.distinct().size!=receipts.size) backupBad("historyIdentity")
        val events=mutableListOf<WebHistoricalEvent>();val gaps=mutableListOf<WebHistoricalGap>()
        receipts.forEach {r->
            if(r.timestamp!=null && r.total!=null) events.add(WebHistoricalEvent(r.id,"sale",r.timestamp,r.total))
            else gaps.add(WebHistoricalGap(r.id,"sale","timeOrAmountUnknown"))
            if(r.returnedAt!=null) {
                if(r.returnAmount!=null) events.add(WebHistoricalEvent(r.id,"refund",r.returnedAt,-r.returnAmount))
                else gaps.add(WebHistoricalGap(r.id,"refund","amountUnknown"))
            }
        }
        return WebHistoricalEvents(events.toList(),gaps.toList())
    }
}
