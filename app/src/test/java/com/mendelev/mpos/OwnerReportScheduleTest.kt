package com.mendelev.mpos

import com.mendelev.mpos.telegram.*
import com.mendelev.mpos.domain.report.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.*
import java.math.BigDecimal
import java.math.BigInteger

class OwnerReportScheduleTest {
    private val zone=ZoneId.of("Europe/Minsk")
    private fun at(date: String,time: String="12:00")=LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()
    private val settings=TelegramSettings(enabled=true,botToken="123456:"+"X".repeat(32),chatId="",ownerChatId="123456",shiftOpened=false,shiftClosed=false,
        ownerShiftStats=true,ownerMonthReport=true,ownerQuarterReport=true,ownerMonthSince=at("2026-12-01"),ownerQuarterSince=at("2026-12-01"))
    @Test fun everyEndDayClosingSchedulesAllEnabledKindsAndIndependentKeys() {
        val close=at("2026-12-31")
        assertEquals(listOf(OwnerStatsKind.SHIFT,OwnerStatsKind.MONTH,OwnerStatsKind.QUARTER),OwnerReportSchedule.closingKinds(settings,close,zone))
        val month=OwnerReportSchedule.period(OwnerStatsKind.MONTH,LocalDate.parse("2026-12-31"),zone)
        assertNotEquals(OwnerReportSchedule.closeKey(month,"first"),OwnerReportSchedule.closeKey(month,"second"))
        assertEquals(OwnerReportSchedule.finalKey(month),OwnerReportSchedule.finalKey(month))
        assertEquals(listOf(OwnerStatsKind.SHIFT),OwnerReportSchedule.closingKinds(settings,at("2026-12-30"),zone))
    }
    @Test fun januaryOpeningAfterHolidaySchedulesPreviousDecemberAndFourthQuarter() {
        val due=OwnerReportSchedule.completed(settings,at("2027-01-01","09:00"),zone).toList()
        assertEquals(2,due.size)
        assertEquals(LocalDate.parse("2026-12-01"),due[0].from);assertEquals(LocalDate.parse("2026-12-31"),due[0].through)
        assertEquals("2026-Q4",due[1].id);assertEquals(LocalDate.parse("2026-10-01"),due[1].from)
        assertEquals(LocalDate.parse("2026-12-31"),due[1].through)
        assertEquals(due,OwnerReportSchedule.completed(settings,at("2027-01-02"),zone).toList())
    }
    @Test fun anyMonthHolidayLeapFebruaryAndYearRolloverUseCalendarNotThirtyDays() {
        val s=settings.copy(ownerQuarterReport=false,ownerMonthSince=at("2024-02-12"))
        val feb=OwnerReportSchedule.completed(s,at("2024-03-01"),zone).single()
        assertEquals(LocalDate.parse("2024-02-01"),feb.from);assertEquals(LocalDate.parse("2024-02-29"),feb.through)
        val april=OwnerReportSchedule.completed(s.copy(ownerMonthSince=at("2026-04-10")),at("2026-05-03"),zone).single()
        assertEquals(LocalDate.parse("2026-04-30"),april.through)
        val q=OwnerReportSchedule.period(OwnerStatsKind.QUARTER,LocalDate.parse("2026-07-15"),zone)
        assertEquals(LocalDate.parse("2026-07-01"),q.from);assertEquals(LocalDate.parse("2026-09-30"),q.through)
    }
    @Test fun activationDoesNotBackfillUnrequestedOldPeriodsAndOffFlagsAreIndependent() {
        assertTrue(OwnerReportSchedule.completed(settings,at("2026-12-31"),zone).none())
        assertTrue(OwnerReportSchedule.completed(settings.copy(enabled=false),at("2027-01-01"),zone).none())
        assertEquals(listOf(OwnerStatsKind.QUARTER),OwnerReportSchedule.completed(settings.copy(ownerMonthReport=false),at("2027-01-01"),zone).map {it.kind}.toList())
        val absent=settings.copy(ownerMonthSince=0,ownerQuarterSince=0)
        assertTrue(OwnerReportSchedule.completed(absent,at("2027-01-01"),zone).none())
        assertTrue(OwnerReportSchedule.closingKinds(settings.copy(ownerShiftStats=false,ownerMonthReport=false,ownerQuarterReport=false),at("2026-12-31"),zone).isEmpty())
    }
    @Test fun coverageHonorsSuccessfulLaterCloseAndNeverReplaysAmbiguousOutcome() {
        assertEquals(100L,OwnerReportCoverage.latestSent(listOf(100L to "sent",200L to "unknown")))
        assertTrue(OwnerReportCoverage.uncertain(listOf(100L to "sent",200L to "unknown")))
        assertFalse(OwnerReportCoverage.uncertain(listOf(100L to "unknown",200L to "sent")))
        assertFalse(OwnerReportCoverage.uncertain(listOf(100L to "rejected")))
        assertTrue(OwnerReportCoverage.retryNotDue(listOf(100L to "retry:200"),199))
        assertFalse(OwnerReportCoverage.retryNotDue(listOf(100L to "retry:200"),200))
    }
    @Test fun journalReopenSuppressesRepeatedFinalReportButAllowsNewEndDayClosing() {
        val state=mutableMapOf<String,String>();var sends=0
        val journal=object: TelegramDeliveryJournal {override fun state(key: String)=state[key];override fun put(key: String,s: String) {state[key]=s}}
        fun delivery()=TelegramDelivery(journal,TelegramTransport {s,m->assertEquals(settings.ownerChatId,s.chatId);assertEquals("",s.threadId);assertEquals("application/pdf",m.attachment!!.mediaType);sends++})
        val period=OwnerReportSchedule.period(OwnerStatsKind.MONTH,LocalDate.parse("2026-12-31"),zone)
        val recipient=OwnerStatsRouting.recipient(settings)
        fun payload()=TelegramMessage("fixture",TelegramAttachment("report.pdf","application/pdf","%PDF-fixture".toByteArray()))
        assertTrue(delivery().send(OwnerReportSchedule.closeKey(period,"a"),recipient,message=::payload).sent)
        assertTrue(delivery().send(OwnerReportSchedule.closeKey(period,"b"),recipient,message=::payload).sent)
        assertTrue(delivery().send(OwnerReportSchedule.finalKey(period),recipient,message=::payload).sent)
        assertTrue(delivery().send(OwnerReportSchedule.finalKey(period),recipient) {error("Must not render again")}.skipped)
        assertEquals(3,sends)
    }
    @Test fun codecRoundTripAndOwnerValidationCoverNewIndependentFlags() {
        assertEquals(settings,TelegramSettingsCodec.decode(TelegramSettingsCodec.encode(settings)))
        val old=JSONObject(TelegramSettingsCodec.encode(settings));listOf("ownerMonthReport","ownerQuarterReport","ownerMonthSince","ownerQuarterSince").forEach {old.remove(it)}
        val restored=TelegramSettingsCodec.decode(old.toString());assertFalse(restored.ownerMonthReport);assertFalse(restored.ownerQuarterReport)
        assertThrows(IllegalArgumentException::class.java) {settings.copy(ownerChatId="").validated()}
        assertThrows(IllegalArgumentException::class.java) {settings.copy(ownerMonthSince=-1).validated()}
        settings.validated()
    }
    @Test fun sameDayOriginalSaleOnlyValidatesRefundAndIsNotCountedInSelectedShift() {
        val day=LocalDate.parse("2026-12-31");val period=ReportPeriod(day,day,zone)
        val sales=listOf(ReportSale("a",period.start,100,100,0,0,0),ReportSale("b",period.start+1,200,0,200,0,0))
        val refunds=listOf(ReportRefund("r","a",period.start+2,100,100,0,0,0))
        val selected=SalesReports.build(period,period.endExclusive,sales,refunds,setOf("b"),setOf("r"))
        assertEquals(1L,selected.saleCount);assertEquals(100.toBigInteger(),selected.net.total)
        assertEquals(listOf("b","a"),selected.events.map {it.receiptId})
        assertThrows(Exception::class.java) {SalesReports.build(period,period.endExclusive,sales,refunds,setOf("missing"),emptySet())}
    }
}
