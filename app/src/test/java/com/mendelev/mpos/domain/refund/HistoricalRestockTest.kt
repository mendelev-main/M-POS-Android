package com.mendelev.mpos.domain.refund

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class HistoricalRestockTest {
    private val flour=RecipeProduct.Simple("flour",QuantityUnit.KG,BigDecimal.ONE)
    private fun reject(block: ()->Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun savedConsumptionIgnoresChangedRecipeCostAndCurrentTrackingFlags() {
        val root=RecipeProduct.Composite("dish",QuantityUnit.PIECE,BigDecimal.ONE,listOf(RecipeComponent("flour",BigDecimal("0.0004"),QuantityUnit.KG)))
        val changed=root.copy(components=listOf(RecipeComponent("flour",BigDecimal.TEN,QuantityUnit.KG)))
        val sold=listOf(flour,root);val current=listOf(flour.copy(cost=BigDecimal("99"),noStockTracking=true),changed)
        val consumption=RecipeCalculator(sold).consumption(listOf(RecipeRequest("dish",BigDecimal.ONE,QuantityUnit.PIECE))).mapValues {CatalogRules.canonical(it.value)}
        val balances=mapOf("flour" to RestockBalance("9.9996",7))
        assertEquals(listOf(RestockChange("flour","0.0004","9.9996","10",7,8)),HistoricalRestock.plan(consumption,sold,current,balances))
        assertEquals("9.9996",balances["flour"]!!.quantity)
        assertEquals("0.0004",consumption["flour"])
    }
    @Test fun emptyHistoricalConsumptionCannotCreateStockWhenTrackingIsEnabledLater() {
        assertTrue(HistoricalRestock.plan(emptyMap(),listOf(flour.copy(noStockTracking=true)),listOf(flour),mapOf("flour" to RestockBalance("10",1))).isEmpty())
    }
    @Test fun negativeBalanceIsIncreasedExactlyWithoutClampingOrNewPolicyRefusal() {
        val result=HistoricalRestock.plan(mapOf("flour" to "0.0004"),listOf(flour.copy(allowNegativeStock=true)),listOf(flour),mapOf("flour" to RestockBalance("-2",4))).single()
        assertEquals("-1.9996",result.after);assertEquals("0.0004",result.delta)
    }
    @Test fun separateTinyReturnsDoNotRoundEachMovementToDisplayPrecision() {
        var balance=RestockBalance("0",0)
        repeat(1000) {
            val next=HistoricalRestock.plan(mapOf("flour" to "0.000000000001"),listOf(flour),listOf(flour),mapOf("flour" to balance)).single()
            balance=RestockBalance(next.after,next.nextRevision)
        }
        assertEquals("0.000000001",balance.quantity);assertEquals(1000L,balance.revision)
    }
    @Test fun missingTargetsUnitsOrBalanceAndRevisionOverflowRefuseWholePlan() {
        val second=flour.copy(id="other");val products=listOf(flour,second)
        val consumption=mapOf("flour" to "1","other" to "2");val balances=mapOf("flour" to RestockBalance("10",3))
        reject {HistoricalRestock.plan(consumption,products,products,balances)}
        assertEquals("10",balances["flour"]!!.quantity)
        reject {HistoricalRestock.plan(mapOf("flour" to "1"),emptyList(),products,balances)}
        reject {HistoricalRestock.plan(mapOf("flour" to "1"),products,emptyList(),balances)}
        reject {HistoricalRestock.plan(mapOf("flour" to "1"),products,listOf(flour.copy(unit=QuantityUnit.G)),balances)}
        reject {HistoricalRestock.plan(mapOf("flour" to "1"),products,listOf(RecipeProduct.Composite("flour",QuantityUnit.KG,BigDecimal.ONE,emptyList())),balances)}
        reject {HistoricalRestock.plan(mapOf("flour" to "1"),products,listOf(flour,flour),balances)}
        for(revision in listOf(-1L,Long.MAX_VALUE)) reject {HistoricalRestock.plan(mapOf("flour" to "1"),products,products,mapOf("flour" to RestockBalance("10",revision)))}
    }
    @Test fun malformedOrNonPositiveHistoricalQuantitiesNeverProduceRestock() {
        val products=listOf(flour)
        for(raw in listOf("0","-1","NaN","1e2","0.0000000000001","1000000000000000000"," 1","1,2"))
            reject {HistoricalRestock.plan(mapOf("flour" to raw),products,products,mapOf("flour" to RestockBalance("10",1)))}
        for(raw in listOf("NaN","1e2","","--1","1.0000000000001"))
            reject {HistoricalRestock.plan(mapOf("flour" to "1"),products,products,mapOf("flour" to RestockBalance(raw,1)))}
        reject {HistoricalRestock.plan(mapOf("" to "1"),products,products,emptyMap())}
    }
}
