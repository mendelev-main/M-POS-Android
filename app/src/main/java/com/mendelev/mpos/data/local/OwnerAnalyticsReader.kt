package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.telegram.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Trusted automatic close-report read, bounded by an immutable closed shift. No admin session impersonation. */
internal suspend fun CatalogDatabase.ownerAnalytics(shiftId: String,month: Boolean,requireLastDay: Boolean=true): OwnerAnalyticsReport=withTransaction {
    val closing=shifts().shift(shiftId) ?: error("Не найдена закрытая смена")
    val cutoff=closing.closedAt ?: error("Смена ещё не закрыта")
    val zone=ZoneId.of(closing.zoneId)
    require(!month || !requireLastDay || OwnerAnalyticsRules.lastDay(cutoff,zone)) {"Месячная статистика отправляется в последний день месяца"}
    val ym=YearMonth.from(Instant.ofEpochMilli(cutoff).atZone(zone))
    val start=ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val shiftNumber=com.mendelev.mpos.domain.shift.ShiftDashboardRules.numbers(shifts().sequence()).getValue(closing.id)
    val ids=listOf(closing.id)
    val candidates=if(month) sales().analyticsRange(start,cutoff) else sales().inShifts(ids).filter {it.createdAt<=cutoff}
    val refundCandidates=if(month) refunds().analyticsRange(start,cutoff) else refunds().inShifts(ids).filter {it.createdAt<=cutoff}
    val originals=refundCandidates.map {r->sales().receipt(r.receiptId) ?: error("Не найден чек возврата")}
    val records=(candidates+originals).distinctBy {it.id}
    val snapshots=records.associate {it.id to ReceiptCodec.decode(it.payload)}
    val details=reportHistory(records,snapshots).associateBy {it.receiptId}
    val facts=records.map {r->
        val saved=snapshots.getValue(r.id)
        require(saved.quote.total==r.total && saved.payment.cash==r.cash && saved.payment.card==r.card && saved.payment.deliveryCashOut==r.deliveryCashOut && r.cash.toBigInteger()+r.card.toBigInteger()==r.total.toBigInteger()) {"Повреждены суммы чека"}
        OwnerSale(r.id,r.shiftId,r.createdAt,r.total,r.deliveryCashOut,details.getValue(r.id).lines)
    }.associateBy {it.id}
    val returns=refundCandidates.map {r->
        val original=records.single {it.id==r.receiptId}
        require(r.receiptHash==CatalogRules.fingerprint("refund.receipt.v1",original.payload) && r.cash==original.cash && r.card==original.card) {"Повреждён снимок возврата"}
        OwnerRefund(r.id,r.receiptId,r.shiftId,r.createdAt,r.total,r.deliveryCashCredit)
    }
    val current=OwnerAnalyticsRules.build(candidates.map {facts.getValue(it.id)},returns,facts,zone)
    val archived=workspace().document(WebOrderDefaults.KEY)?.let {WebOrderDefaults.historyCount(it.payload)} ?: 0
    val warnings=if(archived>0) listOf("Архивная история Web, не подключённая к native-отчётам, не включена") else emptyList()
    OwnerAnalyticsReport(if(month) "Коммерческая аналитика · месяц" else "Коммерческая аналитика",if(month) "$ym · по ${com.mendelev.mpos.print.PrintDocuments.date(cutoff)}" else "Смена · ${closing.actorName} · ${com.mendelev.mpos.print.PrintDocuments.date(closing.openedAt)} — ${com.mendelev.mpos.print.PrintDocuments.date(cutoff)}",cutoff,current,null,"",warnings,OwnerShiftInfo(shiftNumber,closing.openedAt,cutoff,closing.actorName,zone.id),month)
}
