package com.mendelev.mpos.domain.report

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate
import java.time.ZoneId

class ReportAnalyticsTest {
    private val day=LocalDate.parse("2026-11-01")
    private val period=ReportPeriod(day,day,ZoneId.of("Europe/Minsk"))
    private fun report(sales: List<ReportSale>,refunds: List<ReportRefund> = emptyList(),p: ReportPeriod=period)=SalesReports.build(p,period.endExclusive,sales,refunds)
    @Test fun emptyAndUnknownTypeAreExplicit() {
        val empty=report(emptyList())
        assertNull(ReportAnalytics.margin(empty))
        assertTrue(ReportAnalytics.payments(empty).all {it.percent==null})
        assertEquals("here",ReportAnalytics.fulfillment(null))
        assertThrows(Exception::class.java) {ReportAnalytics.fulfillment("invalid")}
    }
    @Test fun mixedPaymentsFullRefundAndFulfillmentReconcile() {
        val sales=listOf(ReportSale("a",period.start,10000,4000,6000,500,500,"delivery"),ReportSale("b",period.start,2000,2000,0,0,0))
        val refund=ReportRefund("r","b",period.start+1,2000,2000,0,0,0)
        val r=report(sales,listOf(refund))
        assertEquals(BigInteger("10000"),r.net.total)
        assertEquals(listOf(BigInteger("4000"),BigInteger("6000")),ReportAnalytics.payments(r).map {it.amount})
        val types=ReportAnalytics.fulfillment(r)
        assertEquals(BigInteger("10000"),types.single {it.label=="Доставка"}.amount)
        assertEquals(1L,types.single {it.label=="На месте"}.checks)
        assertEquals(r.net.total,types.fold(BigInteger.ZERO) {sum,it->sum+it.amount})
    }
    @Test fun returnUsesOriginalTypeInAnotherPeriodAndNoNegativeBars() {
        val sale=ReportSale("a",period.start,10000,4000,6000,0,0,"takeaway")
        val refund=ReportRefund("r","a",period.endExclusive,10000,4000,6000,0,0)
        val r=report(listOf(sale),listOf(refund),ReportPeriod(day.plusDays(1),day.plusDays(1),period.zone))
        val types=ReportAnalytics.fulfillment(r)
        assertEquals(BigInteger("-10000"),types.single {it.label=="С собой"}.amount)
        assertTrue(types.all {it.percent==null && it.checks==0L})
    }
    @Test fun grossProfitDoesNotSubtractLegacyCourierAndEmployeeChecksAreSalesOnly() {
        val r=report(listOf(ReportSale("s",period.start,2500,2500,0,500,500,"delivery")))
        val detail=ReportDetailSale("s","anna","Анна",listOf(ReportDetailLine("l","p","Товар","c","Категория","piece","2".toBigDecimal(),2000,800.toBigInteger())))
        val full=r.copy(details=ReportGroups.build(r,listOf(detail)))
        assertEquals(1200.toBigInteger(),full.details!!.profit)
        assertEquals(1700.toBigInteger(),ReportAnalytics.grossProfit(full))
        assertEquals(2500.toBigInteger(),full.details!!.employees.single().average)
        assertEquals(1L,full.details!!.employees.single().checks)
        assertEquals(2000.toBigInteger(),full.details!!.categories.single().sales)
    }
    @Test fun searchAndSortPreserveStableIds() {
        fun row(id: String,n: String,amount: Int)=ReportGroup(id,listOf(n),amount.toBigInteger(),BigInteger.ZERO,mapOf("piece" to amount.toBigDecimal()),emptyMap())
        val rows=listOf(row("a","Кофе",1),row("b","Кофе",2),row("c","Чай",3))
        assertEquals(listOf("b","a"),ReportAnalytics.products(rows,"коф",true).map {it.id})
        assertEquals(3,ReportAnalytics.products(rows,"",false).size)
    }
    @Test fun specificationPizzaAndDirectCheeseDataset() {
        val sales=listOf(ReportSale("a",period.start,2000,0,2000,0,0,"takeaway"),ReportSale("b",period.start,500,500,0,0,0))
        val r=report(sales,listOf(ReportRefund("ra","a",period.start+1,2000,0,2000,0,0)))
        assertEquals(500.toBigInteger(),r.net.total)
        assertEquals(500.toBigInteger(),ReportAnalytics.payments(r)[0].amount)
        assertEquals(BigInteger.ZERO,ReportAnalytics.payments(r)[1].amount)
        assertEquals(500.toBigInteger(),ReportAnalytics.fulfillment(r).single {it.label=="На месте"}.amount)
        assertEquals(BigInteger.ZERO,ReportAnalytics.fulfillment(r).single {it.label=="С собой"}.amount)
        val facts=mapOf("a" to listOf(ProductUsage("cheese","Сыр","g","300".toBigDecimal())),"b" to listOf(ProductUsage("cheese","Сыр","kg","0.5".toBigDecimal())))
        val usage=ConsumptionReports.build(r.events,facts)
        assertEquals(1,usage.size)
        assertEquals(0,"0.5".toBigDecimal().compareTo(usage.single().netConsumption))
    }

}
