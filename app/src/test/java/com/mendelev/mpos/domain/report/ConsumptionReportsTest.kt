package com.mendelev.mpos.domain.report

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class ConsumptionReportsTest {
    private fun n(s: String)=BigDecimal(s)
    private val names=mapOf("flour" to "Мука","milk" to "Молоко","syrup" to "Сироп")
    private val graph=listOf(
        RecipeProduct.Simple("flour",QuantityUnit.KG,n("1"),noStockTracking=true),
        RecipeProduct.Simple("milk",QuantityUnit.L,n("1")),
        RecipeProduct.Simple("syrup",QuantityUnit.L,n("1"),noStockTracking=true),
        RecipeProduct.Composite("base",QuantityUnit.PIECE,n("2"),listOf(RecipeComponent("flour",n("0.1"),QuantityUnit.KG),RecipeComponent("milk",n("200"),QuantityUnit.ML))),
        RecipeProduct.Composite("dish",QuantityUnit.PIECE,n("1"),listOf(RecipeComponent("base",n("1"),QuantityUnit.PIECE))))
    private fun cart()=CurrentCart(graph=graph,lines=listOf(
        CartLine("recipe","dish","Блюдо","10",false,"10","3","piece",listOf(ChosenOption("extra","Сироп","50","1")),
            listOf(RecipeRequest("dish",n("1"),QuantityUnit.PIECE),RecipeRequest("syrup",n("50"),QuantityUnit.ML)),emptyMap()),
        CartLine("direct","flour","Мука","1",false,"1","1","kg",emptyList(),listOf(RecipeRequest("flour",n("0.2"),QuantityUnit.KG)),emptyMap())))
    private fun equal(value: String,actual: BigDecimal)=assertEquals(0,n(value).compareTo(actual))
    private fun event(kind: String,id: String,receipt: String)=ReportEvent(kind,id,receipt,0,0,0,0,0,0)
    @Test fun allSimpleIngredientsIncludeNestedYieldModifiersAndDirectUntrackedSales() {
        val cart=cart();val usage=ConsumptionReports.usage(cart,names).associateBy {it.productId}
        assertEquals(setOf("flour","milk","syrup"),usage.keys)
        equal("0.35",usage.getValue("flour").quantity)
        equal("0.3",usage.getValue("milk").quantity);equal("0.15",usage.getValue("syrup").quantity)
        val physical=RecipeCalculator(graph).consumption(CartEngine.requests(cart))
        assertEquals(setOf("milk"),physical.keys);equal("0.3",physical.getValue("milk"))
        val rows=ConsumptionReports.build(listOf(event("sale","s","s")),mapOf("s" to usage.values.toList()))
        assertEquals(3,rows.size);assertFalse(rows.any {it.productId in setOf("dish","base")})
    }
    @Test fun refundUsesOriginalRecipeOnRefundDateWithoutChangingOriginalSaleDay() {
        val day=LocalDate.parse("2026-11-01");val zone=ZoneId.of("Europe/Minsk")
        val period=ReportPeriod(day,day,zone)
        val old=ConsumptionReports.usage(cart(),names)
        val changed=ConsumptionReports.usage(cart().copy(graph=graph.map {if(it.id=="base") (it as RecipeProduct.Composite).copy(yield=n("1")) else it}),names)
        assertNotEquals(old,changed)
        val sale=ReportSale("s",period.start,100,100,0,0,0)
        val refund=ReportRefund("r","s",period.endExclusive,100,100,0,0,0)
        val sold=ConsumptionReports.build(SalesReports.build(period,period.endExclusive,listOf(sale),listOf(refund)).events,mapOf("s" to old))
        val returned=ConsumptionReports.build(SalesReports.build(ReportPeriod(day.plusDays(1),day.plusDays(1),zone),period.endExclusive,listOf(sale),listOf(refund)).events,mapOf("s" to old))
        equal("0.35",sold.single {it.productId=="flour"}.outgoing)
        equal("-0.35",returned.single {it.productId=="flour"}.netConsumption)
        equal("0.35",returned.single {it.productId=="flour"}.returned)
    }
    @Test fun compatibleHistoricalUnitsMergeAndTinyQuantitiesArePreserved() {
        val a=ProductUsage("p","Старое имя","kg",n("0.0004"))
        val b=ProductUsage("p","Новое имя","g",n("1"))
        val rows=ConsumptionReports.build(listOf(event("sale","a","a"),event("sale","b","b")),mapOf("a" to listOf(a),"b" to listOf(b)))
        assertEquals(1,rows.size)
        equal("0.0014",rows.single().netConsumption)
        assertEquals(setOf("Старое имя","Новое имя"),rows.single().names.toSet())
    }
    @Test fun missingHistoricalFactsAndDuplicateEventsFailInsteadOfDisplayingFalseZero() {
        val event=event("sale","s","s")
        assertThrows(CatalogFailure::class.java) {ConsumptionReports.build(listOf(event),emptyMap())}
        assertThrows(IllegalArgumentException::class.java) {ConsumptionReports.build(listOf(event,event),mapOf("s" to emptyList()))}
        assertThrows(CatalogFailure::class.java) {ConsumptionReports.usage(cart(),emptyMap())}
    }
    @Test fun exactThirdsAggregateBeforeDecimalBoundaryAndDimensionsFail() {
        val third=Fraction.of(n("1"))/Fraction.of(n("3"))
        val events=(1..3).map {event("sale","$it","$it")}
        val rows=ConsumptionReports.build(events,(1..3).associate {"$it" to listOf(ProductUsage("p","Товар","kg",third.decimal(),third))})
        equal("1",rows.single().netConsumption)
        assertThrows(CatalogFailure::class.java) {
            ConsumptionReports.build(events.take(2),mapOf("1" to listOf(ProductUsage("p","Товар","kg",n("1"))),"2" to listOf(ProductUsage("p","Товар","l",n("1")))))
        }
    }

    @Test fun volumesConvertAndSearchSortKeepNegativeConsumption() {
        val events=listOf(event("sale","a","a"),event("refund","b","b"))
        val facts=mapOf("a" to listOf(ProductUsage("milk","Молоко","ml",n("1500"))),"b" to listOf(ProductUsage("milk","Молоко","l",n("2"))))
        val rows=ConsumptionReports.build(events,facts)
        equal("-0.5",rows.single().netConsumption)
        assertEquals("l",rows.single().unit)
        assertEquals(rows,ReportAnalytics.consumption(rows,"МОЛ",true))
        assertTrue(ReportAnalytics.consumption(rows,"сыр",false).isEmpty())
    }
    @Test fun cyclicOrMissingRecipeNeverProducesZeroConsumption() {
        val cyclic=cart().copy(graph=listOf(RecipeProduct.Composite("dish",QuantityUnit.PIECE,n("1"),listOf(RecipeComponent("dish",n("1"),QuantityUnit.PIECE)))))
        assertThrows(CatalogFailure::class.java) {ConsumptionReports.usage(cyclic,names)}
        assertThrows(CatalogFailure::class.java) {ConsumptionReports.usage(cart().copy(graph=graph.filterNot {it.id=="milk"}),names)}
    }

}
