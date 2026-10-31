package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

class SalesReportsTest {
    private val zone=ZoneId.of("Europe/Minsk")
    private fun period(from: String="2026-10-30",to: String=from,zone: ZoneId=this.zone)=ReportPeriod(LocalDate.parse(from),LocalDate.parse(to),zone)
    private fun at(day: String)=LocalDate.parse(day).atTime(12,0).atZone(zone).toInstant().toEpochMilli()
    private fun sale(id: String="s",day: String="2026-10-30",total: Long=10000,cash: Long=4000,delivery: Long=0)=ReportSale(id,at(day),total,cash,total-cash,delivery)
    private fun refund(s: ReportSale,day: String="2026-10-31")=ReportRefund("r-${s.id}",s.id,at(day),s.total,s.cash,s.card,s.delivery)
    private fun build(p: ReportPeriod,s: List<ReportSale>,r: List<ReportRefund> = emptyList())=SalesReports.build(p,at("2026-11-01"),s,r)
    private fun rejected(block: ()->Unit) {try {block();fail("Corrupt report accepted")} catch(e: CatalogFailure) {assertEquals("report",e.kind)}}
    @Test fun originalSaleRemainsYesterdayAndRefundIsTodayIncludingNegativeNet() {
        val s=sale();val r=refund(s)
        val yesterday=build(period(),listOf(s),listOf(r));assertEquals(BigInteger.valueOf(10000),yesterday.net.total);assertEquals(1L,yesterday.saleCount);assertEquals(0L,yesterday.refundCount)
        val today=build(period("2026-10-31"),listOf(s),listOf(r));assertEquals(BigInteger.valueOf(-10000),today.net.total);assertEquals(BigInteger.ZERO,today.averageSale);assertEquals(0L,today.saleCount);assertEquals(1L,today.refundCount)
        val both=build(period("2026-10-30","2026-10-31"),listOf(s),listOf(r));assertEquals(BigInteger.ZERO,both.net.total);assertEquals(BigInteger.ZERO,both.net.cash);assertEquals(BigInteger.ZERO,both.net.card)
    }
    @Test fun averageUsesOnlyNewSalesWhileOldRefundAndCourierFlowsStaySeparate() {
        val old=sale("old","2026-10-29");val a=sale("a",total=10000,delivery=500);val b=sale("b")
        val result=build(period(),listOf(old,a,b),listOf(refund(old,"2026-10-30")))
        assertEquals(BigInteger.valueOf(20000),result.sales.total);assertEquals(BigInteger.valueOf(10000),result.refunds.total)
        assertEquals(BigInteger.valueOf(10000),result.net.total);assertEquals(BigInteger.valueOf(10000),result.averageSale);assertEquals(2L,result.saleCount)
        assertEquals(BigInteger.valueOf(500),result.sales.delivery)
    }
    @Test fun deliveryIsIncludedOnceAndTenderChangeNeverBecomesReportIncome() {
        val s=sale(total=2500,cash=1000,delivery=500)
        val result=build(period("2026-10-30","2026-10-31"),listOf(s),listOf(refund(s)))
        assertEquals(BigInteger.valueOf(2500),result.sales.total);assertEquals(BigInteger.valueOf(500),result.sales.delivery)
        assertEquals(BigInteger.valueOf(1000),result.sales.cash);assertEquals(BigInteger.valueOf(1500),result.sales.card)
        assertEquals(BigInteger.ZERO,result.net.delivery);assertEquals(BigInteger.ZERO,result.net.cash)
    }
    @Test fun halfOpenCalendarRangeAndDstUseLocalDayRatherThanFixed24Hours() {
        for((day,hours) in listOf("2026-03-29" to 23L,"2026-10-25" to 25L)) {
            val p=period(day,zone=ZoneId.of("Europe/Berlin"))
            assertEquals(hours*3600000,p.endExclusive-p.start)
            val s=ReportSale("start",p.start,1,1,0,0)
            val end=s.copy(id="end",at=p.endExclusive);val before=s.copy(id="before",at=p.start-1)
            assertEquals(listOf("start"),build(p,listOf(end,before,s)).events.map {it.id})
        }
        assertEquals(Instant.parse("2026-10-29T21:00:00Z").toEpochMilli(),period().start)
    }
    @Test fun emptyPeriodAndAverageHalfUpAreDefined() {
        val empty=build(period(),emptyList());assertEquals(BigInteger.ZERO,empty.averageSale);assertEquals(BigInteger.ZERO,empty.net.total);assertTrue(empty.events.isEmpty())
        val result=build(period(),listOf(sale("a",total=1,cash=1),sale("b",total=2,cash=2)))
        assertEquals(BigInteger.valueOf(2),result.averageSale)
    }
    @Test fun aggregateCanExceedLongWithoutOverflowAndInputOrderDoesNotChangeSnapshot() {
        val a=sale("a",total=Long.MAX_VALUE,cash=Long.MAX_VALUE);val b=a.copy(id="b")
        val expected=Long.MAX_VALUE.toBigInteger()*BigInteger.TWO
        assertEquals(expected,build(period(),listOf(a,b)).net.total)
        assertEquals(build(period(),listOf(a,b)),build(period(),listOf(b,a)))
    }
    @Test fun missingDuplicateAndMismatchedRefundsFailEvenOutsideRequestedPeriod() {
        val s=sale();val r=refund(s)
        rejected {build(period("2027-01-01"),emptyList(),listOf(r))}
        rejected {build(period(),listOf(s,s))};rejected {build(period(),listOf(s),listOf(r,r.copy(id="another")))}
        rejected {build(period(),listOf(s),listOf(r.copy(cash=r.cash+1,card=r.card-1)))}
        rejected {build(period(),listOf(s),listOf(r.copy(at=s.at-1)))}
    }
    @Test fun invalidAmountsAndPeriodsNeverProduceMisleadingZeroTotals() {
        rejected {build(period(),listOf(sale().copy(total=-1)))}
        rejected {build(period(),listOf(sale().copy(cash=Long.MAX_VALUE,card=Long.MAX_VALUE)))}
        rejected {build(period(),listOf(sale().copy(delivery=10001)))}
        rejected {period("2026-10-31","2026-10-30")}
    }
}
