package com.mendelev.mpos.telegram

import com.mendelev.mpos.domain.report.ReportPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class OwnerReportPeriod(val kind: OwnerStatsKind,val from: LocalDate,val through: LocalDate,val zone: ZoneId) {
    val range get()=ReportPeriod(from,through,zone)
    val id get()=when(kind) {OwnerStatsKind.SHIFT->"shift";OwnerStatsKind.MONTH->"${from.year}-${from.monthValue.toString().padStart(2,'0')}";OwnerStatsKind.QUARTER->"${from.year}-Q${(from.monthValue-1)/3+1}"}
}
object OwnerReportSchedule {
    fun date(at: Long,zone: ZoneId)=Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    fun period(kind: OwnerStatsKind,day: LocalDate,zone: ZoneId): OwnerReportPeriod {
        require(kind!=OwnerStatsKind.SHIFT)
        val first=when(kind) {OwnerStatsKind.MONTH->day.withDayOfMonth(1);else->LocalDate.of(day.year,((day.monthValue-1)/3)*3+1,1)}
        val months=if(kind==OwnerStatsKind.MONTH) 1L else 3L
        return OwnerReportPeriod(kind,first,first.plusMonths(months).minusDays(1),zone)
    }
    fun closingKinds(s: TelegramSettings,at: Long,zone: ZoneId): List<OwnerStatsKind> {
        if(!s.enabled || s.botToken.isBlank() || s.ownerChatId.isBlank()) return emptyList()
        val day=date(at,zone)
        return buildList {
            if(s.ownerShiftStats) add(OwnerStatsKind.SHIFT)
            if(s.ownerMonthReport && (s.ownerMonthSince==0L || at>=s.ownerMonthSince) && day==period(OwnerStatsKind.MONTH,day,zone).through) add(OwnerStatsKind.MONTH)
            if(s.ownerQuarterReport && (s.ownerQuarterSince==0L || at>=s.ownerQuarterSince) && day==period(OwnerStatsKind.QUARTER,day,zone).through) add(OwnerStatsKind.QUARTER)
        }
    }
    /** Start with the activation period, never unsolicited pre-activation historical mail. */
    fun completed(s: TelegramSettings,at: Long,zone: ZoneId): Sequence<OwnerReportPeriod> = sequence {
        if(!s.enabled || s.botToken.isBlank() || s.ownerChatId.isBlank()) return@sequence
        val today=date(at,zone)
        for(kind in listOf(OwnerStatsKind.MONTH,OwnerStatsKind.QUARTER)) {
            val enabled=if(kind==OwnerStatsKind.MONTH) s.ownerMonthReport else s.ownerQuarterReport
            val since=if(kind==OwnerStatsKind.MONTH) s.ownerMonthSince else s.ownerQuarterSince
            if(!enabled) continue
            var p=period(kind,date(if(since>0) since else at,zone),zone)
            val step=if(kind==OwnerStatsKind.MONTH) 1L else 3L
            while(p.through<today) {yield(p);p=period(kind,p.from.plusMonths(step),zone)}
        }
    }
    fun closeKey(period: OwnerReportPeriod,shiftId: String)="owner-panorama:v1:${period.kind}:${period.id}:close:$shiftId"
    fun finalKey(period: OwnerReportPeriod)="owner-panorama:v1:${period.kind}:${period.id}:final"
}

/** A later successful report covers prior uncertain closes; an unresolved newer send is never replayed. */
object OwnerReportCoverage {
    fun latestSent(closings: List<Pair<Long,String?>>)=closings.filter {it.second=="sent"}.maxOfOrNull {it.first}
    fun uncertain(closings: List<Pair<Long,String?>>): Boolean {
        val sent=latestSent(closings) ?: Long.MIN_VALUE
        return closings.any {it.first>sent && it.second in setOf("sending","unknown")}
    }
    fun retryNotDue(closings: List<Pair<Long,String?>>,now: Long)=closings.any {(_,state)->state?.startsWith("retry:")==true && (state.substringAfter(":").toLongOrNull() ?: Long.MAX_VALUE)>now}
}
