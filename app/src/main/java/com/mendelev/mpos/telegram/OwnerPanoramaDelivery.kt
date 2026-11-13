package com.mendelev.mpos.telegram

import android.content.Context
import com.mendelev.mpos.data.local.OwnerPanoramaReport
import com.mendelev.mpos.feature.report.AnalyticsPdf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object OwnerPanoramaDelivery {
    fun label(kind: OwnerStatsKind)=when(kind) {OwnerStatsKind.SHIFT->"Отчёт за смену";OwnerStatsKind.MONTH->"Отчёт за месяц";OwnerStatsKind.QUARTER->"Отчёт за квартал"}
    private fun date(at: Long,zone: ZoneId)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone).format(Instant.ofEpochMilli(at))
    fun caption(value: OwnerPanoramaReport): String {
        val shift=value.shift;val zone=ZoneId.of(shift.zoneId);val report=value.report
        if(value.kind==OwnerStatsKind.SHIFT) return "🧾 Смена: №${shift.number}\n🟢 Открыта: ${date(shift.openedAt,zone)}\n🔴 Закрыта: ${date(shift.closedAt,zone)}\n👤 Сотрудник: ${TelegramMessages.escape(OwnerReportCaption.employeeName(shift.employee))}"
        val f=DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val title=if(value.kind==OwnerStatsKind.MONTH) "📅 Отчёт за месяц" else "📊 Отчёт за ${quarter(report.period.from.monthValue)} квартал ${report.period.from.year}"
        return "$title\n🗓 ${report.period.from.format(f)} — ${report.period.through.format(f)}"+
            if(value.finalPeriod) "" else "\n🕐 По состоянию на ${date(report.generatedAt,zone)}"
    }
    private fun quarter(month: Int)=listOf("I","II","III","IV")[(month-1)/3]
    fun message(context: Context,value: OwnerPanoramaReport): TelegramMessage {
        val report=value.report;val zone=ZoneId.of(value.shift.zoneId)
        val title=when(value.kind) {OwnerStatsKind.SHIFT->"Отчёт за смену №${value.shift.number}";OwnerStatsKind.MONTH->"Отчёт за месяц";OwnerStatsKind.QUARTER->"Отчёт за ${quarter(report.period.from.monthValue)} квартал ${report.period.from.year}"}
        val subtitle=if(value.kind==OwnerStatsKind.SHIFT) "${date(value.shift.openedAt,zone)} — ${date(value.shift.closedAt,zone)} · ${OwnerReportCaption.employeeName(value.shift.employee)}"
            else if(value.finalPeriod) "Итог за завершённый период" else "По состоянию на ${date(report.generatedAt,zone)}"
        val dark=context.getSharedPreferences("mpos-appearance",Context.MODE_PRIVATE).getString("theme","light")=="dark"
        val file=AnalyticsPdf.newFile(context,report)
        try {
            AnalyticsPdf.write(context,file,report,AnalyticsPdf.Options(dark,title=title,subtitle=subtitle))
            require(file.length() in 1..50_000_000L) {"PDF превышает ограничение Telegram"}
            val suffix=when(value.kind) {OwnerStatsKind.SHIFT->"Shift_${value.shift.number}";OwnerStatsKind.MONTH->"Month_${report.period.from.year}-${report.period.from.monthValue.toString().padStart(2,'0')}";OwnerStatsKind.QUARTER->"Quarter_${report.period.from.year}-Q${(report.period.from.monthValue-1)/3+1}"}
            return TelegramMessage(caption(value),TelegramAttachment("M-POS_$suffix.pdf","application/pdf",file.readBytes()))
        } finally {file.delete()}
    }
}
