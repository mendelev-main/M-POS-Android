package com.mendelev.mpos.domain.report

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

class ReportTrendsTest {
    private val day=LocalDate.parse("2026-11-01")
    private val period=ReportPeriod(day,day.plusDays(6),ZoneId.of("Europe/Minsk"))
    private fun history(id: String,cost: Int)=ReportDetailSale(id,"a","Анна",listOf(ReportDetailLine("l","p","Сыр","c","Еда","piece",BigDecimal.ONE,2000,cost.toBigInteger())))
    @Test fun dailyRevenueAndProfitUseOriginalCostAndRefundDateWithoutCourierSubtraction() {
        val sale=ReportSale("s",period.start,2500,1000,1500,500,500,"delivery")
        val refund=ReportRefund("r","s",day.plusDays(1).atStartOfDay(period.zone).toInstant().toEpochMilli(),2500,1000,1500,500,500)
        val facts=listOf(history("s",800))
        val base=SalesReports.build(period,period.endExclusive,listOf(sale),listOf(refund))
        val detailed=base.copy(details=ReportGroups.build(base,facts));val rows=ReportTrends.build(detailed,facts)
        assertEquals(7,rows.size)
        assertEquals(2500.toBigInteger(),rows[0].revenue);assertEquals(1700.toBigInteger(),rows[0].profit)
        assertEquals((-2500).toBigInteger(),rows[1].revenue);assertEquals((-1700).toBigInteger(),rows[1].profit)
        assertEquals(BigInteger.ZERO,rows.fold(BigInteger.ZERO) {s,p->s+p.profit})
        assertTrue(rows.drop(2).all {it.revenue==BigInteger.ZERO && it.profit==BigInteger.ZERO})
    }
    @Test fun refundOfOlderSaleMakesCurrentPeriodNegative() {
        val sale=ReportSale("s",period.start-1,2000,2000,0,0,0)
        val refund=ReportRefund("r","s",period.start,2000,2000,0,0,0)
        val rows=ReportTrends.build(SalesReports.build(period,period.endExclusive,listOf(sale),listOf(refund)),listOf(history("s",500)))
        assertEquals((-2000).toBigInteger(),rows.first().revenue);assertEquals((-1500).toBigInteger(),rows.first().profit)
    }
    @Test fun longPeriodBinsRemainBoundedInclusiveAndReconcileExactlyBeyondLong() {
        val p=ReportPeriod(day.minusYears(10),day,period.zone)
        val sales=listOf(ReportSale("a",p.start,Long.MAX_VALUE,Long.MAX_VALUE,0,0,0),ReportSale("b",p.endExclusive-1,Long.MAX_VALUE,0,Long.MAX_VALUE,0,0))
        val report=SalesReports.build(p,p.endExclusive,sales,emptyList())
        val rows=ReportTrends.build(report,listOf(history("a",1),history("b",1)))
        assertTrue(rows.size<=31);assertEquals(p.from,rows.first().from);assertEquals(p.through,rows.last().through)
        assertEquals(report.net.total,rows.fold(BigInteger.ZERO) {s,r->s+r.revenue})
        assertEquals(report.net.total-2.toBigInteger(),rows.fold(BigInteger.ZERO) {s,r->s+r.profit})
        rows.zipWithNext().forEach {(a,b)->assertEquals(a.through.plusDays(1),b.from)}
    }
    @Test fun emptyPeriodIsZeroAndLocalDstBoundaryIsNotFixed24Hours() {
        val p=ReportPeriod(LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-26"),ZoneId.of("Europe/Berlin"))
        assertEquals(2,ReportTrends.build(SalesReports.build(p,p.endExclusive,emptyList(),emptyList()),emptyList()).size)
        val at=p.from.plusDays(1).atStartOfDay(p.zone).toInstant().toEpochMilli()
        assertEquals(25*3600000L,at-p.start)
        val report=SalesReports.build(p,p.endExclusive,listOf(ReportSale("s",at,1,1,0,0,0)),emptyList())
        val rows=ReportTrends.build(report,listOf(history("s",0)))
        assertEquals(BigInteger.ZERO,rows[0].revenue);assertEquals(BigInteger.ONE,rows[1].revenue)
    }
    @Test fun missingHistoryAndMismatchedTotalsNeverBecomeFalseZeroGraphs() {
        val report=SalesReports.build(period,period.endExclusive,listOf(ReportSale("s",period.start,2000,2000,0,0,0)),emptyList())
        assertThrows(Exception::class.java) {ReportTrends.build(report,emptyList())}
        assertThrows(Exception::class.java) {ReportTrends.build(report,listOf(history("s",0),history("s",0)))}
        assertThrows(Exception::class.java) {ReportTrends.build(report.copy(net=report.net.copy(total=1.toBigInteger())),listOf(history("s",0)))}
    }
}
