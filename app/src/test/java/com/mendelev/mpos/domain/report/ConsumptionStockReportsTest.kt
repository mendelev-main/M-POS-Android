package com.mendelev.mpos.domain.report

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class ConsumptionStockReportsTest {
    private val period=ReportPeriod(LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-01"),ZoneId.of("Europe/Minsk"))
    private fun n(value: String)=BigDecimal(value)
    private fun equal(value: String,actual: BigDecimal?)=assertEquals(0,n(value).compareTo(requireNotNull(actual)))
    @Test fun endOfSelectedDayNotCurrentBalanceAndGrossUsageDoesNotSubtractRefund() {
        val p=WarehouseProduct("p","Сыр","kg",n("9"),n("1"))
        fun move(id: String,delta: String,source: String,at: Long)=WarehouseMovement(id,"p",at,source,n(delta),"kg")
        val warehouse=WarehouseReports.build(period,listOf(p),listOf(
            move("opening","20","opening",period.start-1),move("sale","-10","sale",period.start),
            move("refund","2","refund",period.start+1),move("receive","3","receiving",period.start+2),
            move("next-day","-6","sale",period.endExclusive)),listOf(WarehousePurchase("p",period.start,n("3"),"kg",300)))
        val row=ConsumptionStockReports.build(listOf(ConsumptionRow("p",listOf("Сыр"),"kg",n("10"),n("2"))),warehouse).single()
        equal("10",row.consumed);equal("3",row.received);equal("15",row.available)
        equal("9",warehouse.rows.single().product.current)
    }
    @Test fun includesReceivedAndStockOnlyProductsWithoutInventingSales() {
        val warehouse=WarehouseReports.build(period,listOf(WarehouseProduct("p","Молоко","ml",n("1500"),n("0.01"))),
            listOf(WarehouseMovement("r","p",period.start,"receiving",n("1500"),"ml")),
            listOf(WarehousePurchase("p",period.start,n("1500"),"ml",500)))
        val row=ConsumptionStockReports.build(emptyList(),warehouse).single()
        assertEquals("l",row.unit);equal("1.5",row.received);equal("1.5",row.available);equal("0",row.consumed)
    }
    @Test fun untrackedIngredientHasInvoiceQuantityAndUsageButUnknownStock() {
        val warehouse=WarehouseReports.build(period,listOf(WarehouseProduct("p","Соус","g",null,n("1"))),emptyList(),
            listOf(WarehousePurchase("p",period.start,n("500"),"g",100)))
        val row=ConsumptionStockReports.build(listOf(ConsumptionRow("p",listOf("Соус"),"kg",n("0.2"),n("0.1"))),warehouse).single()
        equal("0.5",row.received);equal("0.2",row.consumed);assertNull(row.available)
    }
    @Test fun missingOrIncompatibleStockIsUnknownRatherThanZeroAndUsageSortsDescending() {
        val usage=listOf(ConsumptionRow("a",listOf("А"),"kg",n("1"),n("0")),ConsumptionRow("b",listOf("Б"),"kg",n("3"),n("2")))
        val rows=ConsumptionStockReports.build(usage,null)
        assertEquals(listOf("b","a"),rows.map {it.productId});assertTrue(rows.all {it.available==null && it.received==null})
        val warehouse=WarehouseReports.build(period,listOf(WarehouseProduct("a","А","l",n("2"),n("1"))),
            listOf(WarehouseMovement("opening","a",period.start,"opening",n("2"),"l")),emptyList())
        val a=ConsumptionStockReports.build(usage,warehouse).single {it.productId=="a"}
        assertNull(a.available);assertNull(a.received);equal("1",a.consumed)
    }
}
