package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.shift.*
import org.json.JSONObject

/** Cashier-visible shift read model; one read transaction, no report-admin authority or writes. */
internal suspend fun CatalogDatabase.shiftDashboard(before: ShiftHistoryCursor?=null): ShiftDashboard=withTransaction {
    val state=ShiftRepository(this).snapshot()
    val page=shifts().historyPage(before?.closedAt,before?.id,11)
    val closed=page.take(10)
    val selected=(closed+listOfNotNull(state.active?.id?.let {shifts().shift(it)})).distinctBy {it.id}
    val ids=selected.map {it.id}
    val sales=sales().inShifts(ids).map {r->
        val saved=ReceiptCodec.decode(r.payload)
        require(saved.quote.total==r.total && saved.payment.cash==r.cash && saved.payment.card==r.card && saved.payment.deliveryCashOut==r.deliveryCashOut)
        ShiftSaleFact(r.id,r.shiftId,r.createdAt,r.total,r.cash,r.card,r.deliveryCashOut,saved.quote.discount)
    }
    val refunds=refunds().inShifts(ids).map {ShiftRefundFact(it.id,it.shiftId,it.createdAt,it.total,it.cash,it.card,it.deliveryCashCredit)}
    val imported=workspace().document(WebCandidateDatabase.BASELINE)?.let {doc->val j=JSONObject(doc.payload).getJSONObject("cashDeltas");j.keys().asSequence().toSet()}.orEmpty()
    val data=ShiftDashboardRules.build(state,selected.map {it.domain()},sales,refunds,ShiftCashCodec.all(this).filter {it.command.shiftId in ids},imported,SplitPaymentRepository(this).load()?.pendingCash ?: 0)
    val numbers=ShiftDashboardRules.numbers(shifts().sequence())
    data.copy(summaries=data.summaries.map {summary->
        val numbered=summary.copy(number=numbers.getValue(summary.shift.id))
        workspace().document(WebShiftSummary.PREFIX+summary.shift.id)?.let {WebShiftSummary.merge(numbered,it.payload)} ?: numbered
    },
        nextHistory=closed.lastOrNull()?.takeIf {page.size>10}?.let {ShiftHistoryCursor(it.closedAt!!,it.id)})
}
