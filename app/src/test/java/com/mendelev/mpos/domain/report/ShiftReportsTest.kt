package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.shift.Shift
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class ShiftReportsTest {
    private val p=ReportPeriod(LocalDate.parse("2026-10-31"),LocalDate.parse("2026-10-31"),ZoneId.of("Europe/Minsk"))
    private val old=Shift("old","r","anna","Анна",p.start-10000,"Europe/Minsk",10000,null,p.start-1,10500,10500,0)
    private val today=Shift("today","r","boris","Борис",p.start,"Europe/Minsk",10500,"old")
    private val events=listOf(ShiftReportEvent("sale","old",old.openedAt,"sale",1000,1500,500),
        ShiftReportEvent("refund","today",p.start+1,"refund",1000,1500,500),
        ShiftReportEvent("dep","today",p.start+2,"deposit",2000),ShiftReportEvent("wd","today",p.start+3,"withdrawal",500))
    @Test fun crossShiftRefundAndCarryNeverRewriteOriginalSaleOrDoubleCountCash() {
        val row=ShiftReports.build(p,listOf(old,today),events).single()
        assertEquals("boris",row.shift.actorId);assertEquals(0.toBigInteger(),row.sales);assertEquals(2500.toBigInteger(),row.refunds)
        assertEquals(11500.toBigInteger(),row.calculatedCash);assertNull(row.savedCashMatches)
        val all=ShiftReports.build(ReportPeriod(p.from.minusDays(1),p.through,p.zone),listOf(old,today),events)
        assertEquals(2500.toBigInteger(),all.single {it.shift.id=="old"}.sales)
        assertEquals(true,all.single {it.shift.id=="old"}.savedCashMatches)
    }
    @Test fun overlappingShiftUsesWholeShiftAndClosedDiscrepanciesRemainVisible() {
        val s=old.copy(closedAt=p.start+100,expectedCash=10499,countedCash=10501,difference=2)
        val row=ShiftReports.build(p,listOf(s),events.take(1)).single()
        assertEquals(10500.toBigInteger(),row.calculatedCash);assertEquals(false,row.savedCashMatches)
        assertEquals(10499L,row.shift.expectedCash)
    }
    @Test fun missingDuplicateOrOutOfShiftEventsFailAndEmptyIsDefined() {
        assertTrue(ShiftReports.build(p,emptyList(),emptyList()).isEmpty())
        for(e in listOf(events,events.take(1)+events.take(1),listOf(events.first().copy(at=p.start)))) {
            assertThrows(CatalogFailure::class.java) {ShiftReports.build(p,listOf(old),e)}
        }
    }
    @Test fun stockComparisonChecksExactQuantitiesDatesCommandsDuplicatesAndOrphans() {
        val a=ReportStockFact("sale","receipt","p","command",p.start,BigDecimal("-0.0004"))
        assertEquals(1,StockReconciler.compare(listOf(a),listOf(a.copy(delta=BigDecimal("-0.000400")))).checked)
        for(actual in listOf(emptyList(),listOf(a,a),listOf(a.copy(delta=BigDecimal("-0.001"))),listOf(a.copy(at=a.at+1)),listOf(a.copy(command="other")))) {
            val result=StockReconciler.compare(listOf(a),actual);assertEquals(0,result.checked);assertEquals(1,result.issues.size)
        }
        assertEquals("Лишнее движение",StockReconciler.compare(emptyList(),listOf(a)).issues.single().problem)
        assertThrows(CatalogFailure::class.java) {StockReconciler.compare(listOf(a,a),emptyList())}
    }
}
