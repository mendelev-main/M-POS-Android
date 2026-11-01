package com.mendelev.mpos.domain.report

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class WarehouseReportsTest {
    private val period=ReportPeriod(LocalDate.parse("2026-10-01"),LocalDate.parse("2026-10-01"),ZoneId.of("Europe/Minsk"))
    private val p=WarehouseProduct("p","Мука","kg",BigDecimal("8"),BigDecimal("3"))
    private fun move(id: String,delta: String,source: String,at: Long=period.start+1,unit: String?="kg")=WarehouseMovement(id,"p",at,source,BigDecimal(delta),unit)
    private val events=listOf(move("open","10","opening",period.start-1),move("sale","-3","sale"),move("refund","1","refund"),
        move("receive","2","receiving"),move("edit","-1","product_card"),move("after","-1","sale",period.endExclusive))
    private fun equal(n: String,value: BigDecimal?)=assertEquals(0,BigDecimal(n).compareTo(requireNotNull(value)))
    @Test fun exactBoundariesIncludeManualCorrectionsAndReturnByItsOwnDate() {
        val row=WarehouseReports.build(period,listOf(p),events,emptyList()).rows.single()
        equal("10",row.start);equal("9",row.end);equal("2",row.incoming);equal("3",row.outgoing);equal("1",row.returned);equal("-1",row.adjustments)
        equal("8",row.product.current);assertTrue(row.warnings.isEmpty())
    }
    @Test fun unknownUnitInvalidSignsAndIncompleteLedgerNeverBecomeFalseZeroBalances() {
        val unknown=events.map {if(it.id=="sale") it.copy(unit=null) else it}
        var row=WarehouseReports.build(period,listOf(p),unknown,emptyList()).rows.single()
        assertNull(row.start);equal("9",row.end);assertNull(row.outgoing);assertTrue(row.warnings.isNotEmpty())
        row=WarehouseReports.build(period,listOf(p.copy(current=BigDecimal("999"))),events,emptyList()).rows.single()
        assertNull(row.start);assertNull(row.end)
        row=WarehouseReports.build(period,listOf(p),events.map {if(it.id=="sale") it.copy(unit="g") else it},emptyList()).rows.single()
        assertNull(row.start);assertNull(row.outgoing)
    }
    @Test fun untrackedPurchasesKeepActualInvoiceFactsWithoutPretendingToKnowStock() {
        val row=WarehouseReports.build(period,listOf(p.copy(current=null)),emptyList(),listOf(WarehousePurchase("p",period.start,BigDecimal("2"),"kg",501))).rows.single()
        assertNull(row.start);assertNull(row.end);equal("2",row.invoiceQuantity);assertEquals(501.toBigInteger(),row.invoiceValue)
    }
    @Test fun tinyQuantitiesAndDstDaysAreNotRoundedOrAssumed24Hours() {
        val period=ReportPeriod(LocalDate.parse("2026-03-29"),LocalDate.parse("2026-03-29"),ZoneId.of("Europe/Berlin"))
        assertEquals(23*3600000L,period.endExclusive-period.start)
        val row=WarehouseReports.build(period,listOf(p.copy(current=BigDecimal("0.0004"))),listOf(move("tiny","0.0004","opening",period.start)),emptyList()).rows.single()
        equal("0",row.start);equal("0.0004",row.end);equal("0.0004",row.adjustments)
    }
}
