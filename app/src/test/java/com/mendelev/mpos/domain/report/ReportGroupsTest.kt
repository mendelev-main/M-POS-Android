package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

class ReportGroupsTest {
    private val date=LocalDate.parse("2026-10-30")
    private val period=ReportPeriod(date,date,ZoneId.of("Europe/Minsk"))
    private val sale=ReportSale("s",period.start,2500,1000,1500,500)
    private val history=ReportDetailSale("s","anna","Анна",listOf(
        ReportDetailLine("line","p","Кофе","c","Напитки","piece",BigDecimal("2"),2000,800.toBigInteger())))
    private fun summary(refundOnly: Boolean=false)=SalesReports.build(if(refundOnly) ReportPeriod(date.plusDays(1),date.plusDays(1),period.zone) else period,
        period.endExclusive+1,listOf(sale),listOf(ReportRefund("r","s",period.endExclusive,2500,1000,1500,500)))
    @Test fun originalSellerReceivesRefundAndProfitReversesOnRefundDate() {
        val a=ReportGroups.build(summary(),listOf(history));val b=ReportGroups.build(summary(true),listOf(history))
        assertEquals(1200.toBigInteger(),a.profit);assertEquals((-1200).toBigInteger(),b.profit)
        assertEquals("anna",b.employees.single().id);assertEquals(2500.toBigInteger(),b.employees.single().refunds)
        assertEquals(2000.toBigInteger(),a.products.single().sales);assertEquals(500.toBigInteger(),a.courierOut)
        assertEquals(800.toBigInteger(),b.refundCost);assertEquals(500.toBigInteger(),b.courierCredit)
        assertTrue(b.products.single().sold.isEmpty());assertEquals(BigDecimal("2"),b.products.single().returned["piece"])
    }
    @Test fun historicalLabelsIdsAndUnitsRemainSeparateAndGroupsReconcile() {
        val other=sale.copy(id="second",total=1000,cash=1000,card=0,delivery=0)
        val facts=listOf(history,history.copy(receiptId="second",employeeId="other",employeeName="Анна",lines=listOf(
            history.lines.single().copy(id="kg",name="Новое имя",unit="kg",quantity=BigDecimal("1.5"),netMinor=1000))))
        val report=SalesReports.build(period,period.start,listOf(sale,other),emptyList())
        val d=ReportGroups.build(report,facts)
        assertEquals(2,d.employees.size);assertEquals(listOf("Кофе","Новое имя"),d.products.single().names)
        assertEquals(setOf("piece","kg"),d.categories.single().sold.keys)
        assertEquals(report.sales.total,d.employees.fold(BigInteger.ZERO) {a,r->a+r.sales})
        assertEquals(report.sales.total-report.sales.delivery,d.products.single().sales)
        assertEquals(d.products.single().sales,d.categories.single().sales)
    }
    @Test fun invalidHistoryNeverBecomesAnEmptyOrPartialReport() {
        for(facts in listOf(emptyList(),listOf(history,history),listOf(history.copy(lines=emptyList())),
            listOf(history.copy(lines=listOf(history.lines.single().copy(netMinor=1999)))))) {
            assertThrows(CatalogFailure::class.java) {ReportGroups.build(summary(),facts)}
        }
    }
    @Test fun exactCostRoundsOnlyOnceIncludingUntrackedIngredientsAndRecipeYield() {
        fun calc(cost: String)=RecipeCalculator(listOf(RecipeProduct.Simple("a",QuantityUnit.PIECE,BigDecimal(cost),noStockTracking=true),
            RecipeProduct.Composite("third",QuantityUnit.PIECE,BigDecimal("3"),listOf(RecipeComponent("a",BigDecimal.ONE,QuantityUnit.PIECE)))))
        val req=listOf(RecipeRequest("third",BigDecimal.ONE,QuantityUnit.PIECE))
        assertEquals(BigInteger.ZERO,calc("0.01499999999999").costMinor(req))
        assertEquals(BigInteger.ONE,calc("0.015").costMinor(req))
        assertEquals(BigInteger.TWO,List(2) {calc("0.015").costMinor(req)}.reduce(BigInteger::add))
        assertEquals(BigInteger.ONE,calc("0.015").costMinor(req+req))
    }
}
