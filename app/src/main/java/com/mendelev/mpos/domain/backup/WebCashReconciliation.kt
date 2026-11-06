package com.mendelev.mpos.domain.backup

/** A comparison of source evidence, never an opening balance, cash command or authorization to activate. */
data class WebShiftCashComparison(val sourceShiftId: String,val openingCash: Long?,val completedCash: Long?,
    val activeReceiptCash: Long?,val deposits: Long,val withdrawals: Long,val refundWithdrawals: Long,val deliveryWithdrawals: Long,
    val sourceDisplayedCash: Long?,val cashFromRecordedFacts: Long?,val pendingCash: Long?,val pendingCard: Long?,
    val includingPendingCash: Long?,val sourceCompensationDifference: Long?,val countedDifference: Long?,
    val pendingOrigins: List<String>,val blockers: Set<String>)
data class WebCashComparison(val sourceSha256: String,val shifts: List<WebShiftCashComparison>,
    val unassignedOrigins: List<String>,val unassignedPaidCash: Long,val unassignedPaidCard: Long,val blockers: Set<String>) {
    override fun toString()="WebCashComparison(shifts=${shifts.size}, unassigned=${unassignedOrigins.size}, blockers=${blockers.size})"
}

/** 07.4d.2a. Uses recorded refund withdrawals exactly once; never synthesizes a second refund/courier credit. */
object WebCashReconciliation {
    private data class RefundKey(val shift: String,val time: Long,val cash: Long)
    fun compare(document: WebBackupDocument): WebCashComparison {
        fun byn(fields: Map<String,BackupJson>) {fields["currency"]?.takeUnless {it===BackupJson.Nil}?.let {if(WebValues.text(it)!="BYN")backupBad("cashReconciliationCurrency")}}
        byn(document.root.fields)
        WebValues.rows(document.root.fields["shifts"]).forEach {v->val s=WebValues.obj(v);byn(s);WebValues.rows(s["cashMovements"]).forEach {byn(WebValues.obj(it))}}
        val staff=WebStaffMapping.map(document);val history=WebReceiptHistory.map(document);val operations=WebOpenOperations.map(document)
        val byShift=staff.shifts.associateBy {it.sourceId};val byNativeId=staff.shifts.associateBy {it.id}
        val issues=staff.shifts.associate {it.sourceId to linkedSetOf<String>()}
        val global=linkedSetOf("nativePaidProvenancePending")
        val expectedRefunds=linkedMapOf<RefundKey,Int>();val actualRefunds=linkedMapOf<RefundKey,Int>()
        fun problem(shift: String?,reason: String) {issues[shift]?.add(reason) ?: global.add(reason)}
        for(r in history) {
            val saleShift=byShift[r.sourceShiftId]
            if(saleShift==null)global.add("receiptShiftUnknown")
            if(r.timestamp==null || saleShift!=null && outside(r.timestamp,saleShift))problem(r.sourceShiftId,"receiptShiftTimeUnproven")
            if(r.returnedAt!=null) {
                val returnShift=byShift[r.sourceReturnShiftId]
                if(returnShift==null)global.add("refundShiftUnknown")
                else if(outside(r.returnedAt,returnShift))problem(returnShift.sourceId,"refundShiftTimeUnproven")
                val cash=cash(r)
                if(cash==null || r.total==null || r.returnAmount!=r.total)problem(r.sourceReturnShiftId,"refundCashEvidenceIncomplete")
                else if(cash>0 && returnShift!=null)expectedRefunds.count(RefundKey(returnShift.sourceId,r.returnedAt,cash))
                // Zero-card-only refund expects no cash withdrawal. Recorded extra withdrawals remain visible below.
            }
        }
        val movementOwners=mutableMapOf<String,String>()
        for(s in staff.shifts)for(m in s.movements) {
            m.sourceId?.let {id->movementOwners.putIfAbsent(id,s.sourceId)?.let {first->global.add("cashMovementIdentityAcrossShifts");problem(first,"cashMovementIdentityAcrossShifts");problem(s.sourceId,"cashMovementIdentityAcrossShifts")}}
            if(m.timestamp==null || outside(m.timestamp,s))problem(s.sourceId,"cashMovementShiftTimeUnproven")
            if(m.type=="withdrawal" && m.subtype=="refund" && m.amount>0) {
                if(m.timestamp==null)problem(s.sourceId,"refundMovementTimeUnknown")
                else actualRefunds.count(RefundKey(s.sourceId,m.timestamp,m.amount))
            }
        }
        for(key in expectedRefunds.keys+actualRefunds.keys) {
            val expected=expectedRefunds[key] ?: 0;val actual=actualRefunds[key] ?: 0
            if(expected!=actual)problem(key.shift,"refundMovementMismatch")
            if(expected>1 || actual>1)problem(key.shift,"refundMovementLinkAmbiguous")
        }
        val paid=operations.filter {it.payment!=null}
        val unsafe=setOf("openShiftUnavailable","sessionShiftTimeUnproven","paymentShiftTimeUnproven",
            "allPartsPaidReceiptReconciliation","possibleCompletedReceiptOverlap","parkedPaidDraftNotProducedByPinnedWeb",
            "paidSessionHasNoItems","shift:actorMissing","shift:activeCashierUnavailable","shift:openedAtUnknown")
        val unassigned=paid.filter {byNativeId[it.candidateShiftId]==null || it.blockers.any(unsafe::contains)}
        if(unassigned.isNotEmpty())global.add("paidShiftAssignmentUnproven")
        val unassignedKeys=unassigned.map {it.origin}.toSet()
        val bound=paid.filter {it.origin !in unassignedKeys}.groupBy {byNativeId.getValue(it.candidateShiftId!!).sourceId}
        val unassignedCash=sum(unassigned.map {it.payment!!.paidCash});val unassignedCard=sum(unassigned.map {it.payment!!.paidCard})
        val receipts=history.groupBy {it.sourceShiftId}
        val result=staff.shifts.map {s->
            val b=issues.getValue(s.sourceId);b.addAll(s.blockers)
            val sales=receipts[s.sourceId].orEmpty()
            val completed=optionalSum(sales.map(::cash));val active=optionalSum(sales.filter {it.returnedAt==null}.map(::cash))
            if(completed==null)b.add("receiptCashUnknown")
            val deposits=sum(s.movements.filter {it.type=="deposit"}.map {it.amount})
            val withdrawals=sum(s.movements.filter {it.type=="withdrawal"}.map {it.amount})
            val refunds=sum(s.movements.filter {it.type=="withdrawal" && it.subtype=="refund"}.map {it.amount})
            val delivery=sum(s.movements.filter {it.type=="withdrawal" && it.subtype=="delivery"}.map {it.amount})
            val expectedDelivery=optionalSum(sales.map {it.delivery})
            if(expectedDelivery==null)b.add("deliveryCashEvidenceIncomplete") else if(expectedDelivery!=delivery)b.add("deliveryMovementMismatch")
            val recorded=if(s.openingCash!=null && completed!=null)subtract(add(add(s.openingCash,completed),deposits),withdrawals) else null
            // Exact pinned cashDrawerBalance: returned receipts excluded, refund withdrawals compensated.
            val displayed=if(s.openingCash!=null && active!=null)add(subtract(add(add(s.openingCash,active),deposits),withdrawals),refunds) else null
            val difference=if(recorded!=null && displayed!=null)subtract(recorded,displayed) else null
            if(difference!=null && difference!=0L)b.add("sourceRefundCompensationDifference")
            val pending=bound[s.sourceId].orEmpty();pending.forEach {b.addAll(it.blockers)}
            val pendingCash=if(s.status=="open" && unassignedCash>0)null else sum(pending.map {it.payment!!.paidCash})
            val pendingCard=if(s.status=="open" && unassignedCard>0)null else sum(pending.map {it.payment!!.paidCard})
            val included=if(recorded!=null && pendingCash!=null)add(recorded,pendingCash) else null
            if(pendingCash==null || pendingCard==null)b.add("unassignedPaidEvidence")
            if(recorded!=null && recorded<0 || included!=null && included<0)b.add("negativeRecordedCash")
            if(s.expectedCash!=null && recorded!=null && s.expectedCash!=recorded)b.add("savedExpectedCashMismatch")
            val counted=if(s.countedCash!=null && included!=null)subtract(s.countedCash,included) else null
            WebShiftCashComparison(s.sourceId,s.openingCash,completed,active,deposits,withdrawals,refunds,delivery,
                displayed,recorded,pendingCash,pendingCard,included,difference,counted,pending.map {it.origin},b.toSet())
        }
        return WebCashComparison(document.preview.sha256,result,unassigned.map {it.origin},unassignedCash,unassignedCard,global.toSet())
    }
    private fun cash(r: WebHistoricalReceipt): Long?=r.payments?.let {sum(it.filter {p->p.method=="cash"}.map {p->p.amount})}
    private fun outside(t: Long,s: WebShiftFacts)=s.openedAt==null || t<s.openedAt || s.closedAt?.let {t>it}==true
    private fun optionalSum(values: List<Long?>): Long?=if(values.any {it==null})null else sum(values.map {it!!})
    private fun sum(values: List<Long>)=values.fold(0L,::add)
    private fun add(a: Long,b: Long): Long=try {Math.addExact(a,b)} catch(_:ArithmeticException) {backupBad("cashReconciliationMoney")}
    private fun subtract(a: Long,b: Long): Long=try {Math.subtractExact(a,b)} catch(_:ArithmeticException) {backupBad("cashReconciliationMoney")}
    private fun <T> MutableMap<T,Int>.count(key: T) {this[key]=(this[key] ?: 0)+1}
}
