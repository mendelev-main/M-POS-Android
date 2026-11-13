package com.mendelev.mpos.data.local

import androidx.room.withTransaction
import com.mendelev.mpos.domain.report.*
import com.mendelev.mpos.domain.shift.ShiftDashboardRules
import com.mendelev.mpos.telegram.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

data class OwnerPanoramaReport(val report: SalesReport,val shift: OwnerShiftInfo,val kind: OwnerStatsKind,val finalPeriod: Boolean)
/** Trusted event-driven reader; a persisted committed shift is required. Never impersonates an administrator. */
internal suspend fun CatalogDatabase.ownerPanorama(triggerId: String,kind: OwnerStatsKind,completed: OwnerReportPeriod?=null): OwnerPanoramaReport=withTransaction {
    val trigger=shifts().shift(triggerId) ?: error("Не найдена смена для отчёта")
    val zone=ZoneId.of(trigger.zoneId)
    val cutoff: Long;val period: ReportPeriod;val window: ReportReadWindow
    if(kind==OwnerStatsKind.SHIFT) {
        require(completed==null)
        cutoff=trigger.closedAt ?: error("Смена ещё открыта")
        period=ReportPeriod(OwnerReportSchedule.date(trigger.openedAt,zone),OwnerReportSchedule.date(cutoff,zone),zone)
        window=ReportReadWindow(trigger.openedAt,cutoff,trigger.id)
    } else if(completed!=null) {
        require(completed.kind==kind && completed.zone==zone)
        require(completed==OwnerReportSchedule.period(kind,completed.from,zone))
        period=completed.range
        require(maxOf(trigger.openedAt,trigger.closedAt ?: trigger.openedAt)>=period.endExclusive) {"Период ещё не завершён"}
        cutoff=period.endExclusive-1
        window=ReportReadWindow(period.start,cutoff)
    } else {
        cutoff=trigger.closedAt ?: error("Смена ещё открыта")
        val day=OwnerReportSchedule.date(cutoff,zone)
        val calendar=OwnerReportSchedule.period(kind,day,zone)
        require(day==calendar.through) {"Не последний день отчётного периода"}
        period=calendar.range;window=ReportReadWindow(period.start,cutoff)
    }
    val number=ShiftDashboardRules.numbers(shifts().sequence()).getValue(trigger.id)
    val info=OwnerShiftInfo(number,trigger.openedAt,trigger.closedAt ?: trigger.openedAt,trigger.actorName,zone.id)
    OwnerPanoramaReport(readSalesReport(period,Clock.fixed(Instant.ofEpochMilli(cutoff),zone),window),info,kind,completed!=null)
}
/** Whether a full period acquired reportable facts after its latest successful end-day closing report. */
internal suspend fun CatalogDatabase.ownerPeriodChanged(period: OwnerReportPeriod,after: Long): Boolean=withTransaction {
    val range=period.range
    sales().history().any {it.createdAt>after && range.contains(it.createdAt)} ||
        refunds().history().any {it.createdAt>after && range.contains(it.createdAt)} ||
        PostedReceivingStore(this@ownerPeriodChanged).all().any {it.at>after && range.contains(it.at)}
}
