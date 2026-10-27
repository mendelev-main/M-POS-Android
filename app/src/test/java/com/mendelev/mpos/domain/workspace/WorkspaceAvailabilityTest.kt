package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WorkspaceAvailabilityTest {
    @Test fun fiveThousandProductsAreEvaluatedOnDemandAndReuseTheirResult() {
        val ps=(0 until 5000).map {WorkspaceProduct("p$it","Товар $it","","2",1,"piece")}
        val graph=ps.map {RecipeProduct.Simple(it.id,QuantityUnit.PIECE,BigDecimal.ONE)}
        val stock=ps.associate {it.id to BigDecimal.TEN}
        val index=WorkspaceAvailability(ps,graph,CurrentCart(),stock,emptyList())
        assertEquals(0,index.evaluatedProducts)
        repeat(100) {assertEquals(ProductAvailability.AVAILABLE,index.status("p4999"))}
        assertEquals(1,index.evaluatedProducts)
        assertEquals(ProductAvailability.INVALID,index.status("gone"));assertEquals(1,index.evaluatedProducts)
        assertEquals(ProductAvailability.SHORTAGE,WorkspaceAvailability(ps,graph,CurrentCart(),stock+("p4999" to BigDecimal.ZERO),emptyList()).status("p4999"))
    }
    @Test fun labelsKeepAggregateCartParkedFlagsAndStaleRules() {
        val ps=listOf(WorkspaceProduct("p","Чай","","2",1,"piece"))
        val graph=listOf(RecipeProduct.Simple("p",QuantityUnit.PIECE,BigDecimal.ONE))
        val stock=mapOf("p" to BigDecimal("2"))
        val cart=CartEngine.apply(CurrentCart(sessionId="a"),CartAction.Add("p",1,emptySet(),null,"line"),ps,graph,stock)
        val parked=cart.copy(sessionId="b")
        assertEquals(ProductAvailability.AVAILABLE,WorkspaceAvailability(ps,graph,cart,stock,emptyList()).status("p"))
        assertEquals(ProductAvailability.SHORTAGE,WorkspaceAvailability(ps,graph,cart,stock,listOf(parked)).status("p"))
        assertEquals(ProductAvailability.STALE,WorkspaceAvailability(ps.map {it.copy(revision=2)},graph,cart,stock,emptyList()).status("p"))
        val negative=graph.map {it.copy(allowNegativeStock=true)}
        assertEquals(ProductAvailability.AVAILABLE,WorkspaceAvailability(ps,negative,CurrentCart(),mapOf("p" to BigDecimal.ZERO),listOf(parked)).status("p"))
        val untracked=graph.map {it.copy(noStockTracking=true)}
        assertEquals(ProductAvailability.AVAILABLE,WorkspaceAvailability(ps,untracked,CurrentCart(),emptyMap(),emptyList()).status("p"))
    }
    @Test fun physicalLabelsKeepFractionsNegativeBalancesAndUntrackedInfinity() {
        val ps=listOf(WorkspaceProduct("p","Весовой","","2",1,"kg"))
        val tracked=RecipeProduct.Simple("p",QuantityUnit.KG,BigDecimal.ONE,allowNegativeStock=true)
        fun label(recipe: RecipeProduct,balance: String)=WorkspaceAvailability(ps,listOf(recipe),CurrentCart(),mapOf("p" to BigDecimal(balance)),emptyList()).physicalStockLabel("p")
        assertEquals("0,0004",label(tracked,"0.0004"))
        assertEquals("-0,0004",label(tracked,"-0.0004"))
        assertEquals("∞",label(tracked.copy(noStockTracking=true),"0"))
    }
    @Test fun physicalRecipeLabelUsesConvertedIngredientsAndFloorsNegativePortions() {
        val ps=listOf(WorkspaceProduct("drink","Напиток","","2",1,"piece"))
        val ingredient=RecipeProduct.Simple("milk",QuantityUnit.L,BigDecimal.ONE)
        val recipe=RecipeProduct.Composite("drink",QuantityUnit.PIECE,BigDecimal("2"),listOf(RecipeComponent("milk",BigDecimal("500"),QuantityUnit.ML)))
        fun label(balance: String)=WorkspaceAvailability(ps,listOf(ingredient,recipe),CurrentCart(),mapOf("milk" to BigDecimal(balance)),emptyList()).physicalStockLabel("drink")
        assertEquals("5",label("1.3"))
        val thirds=recipe.copy(yield=BigDecimal("3"),components=listOf(RecipeComponent("milk",BigDecimal.ONE,QuantityUnit.L)))
        assertEquals("3",WorkspaceAvailability(ps,listOf(ingredient,thirds),CurrentCart(),mapOf("milk" to BigDecimal.ONE),emptyList()).physicalStockLabel("drink"))
        assertEquals("-1",label("-0.01"))
        assertEquals("∞",WorkspaceAvailability(ps,listOf(ingredient.copy(noStockTracking=true),recipe),CurrentCart(),emptyMap(),emptyList()).physicalStockLabel("drink"))
    }
    @Test fun physicalLabelDoesNotSubtractCartOrParkedReservationsAndKeepsLazyAvailability() {
        val ps=listOf(WorkspaceProduct("p","Чай","","2",1,"piece"))
        val graph=listOf(RecipeProduct.Simple("p",QuantityUnit.PIECE,BigDecimal.ONE))
        val stock=mapOf("p" to BigDecimal("2"))
        val cart=CartEngine.apply(CurrentCart(sessionId="a"),CartAction.Add("p",1,emptySet(),null,"line"),ps,graph,stock)
        val index=WorkspaceAvailability(ps,graph,cart,stock,listOf(cart.copy(sessionId="b")))
        assertEquals("2",index.physicalStockLabel("p"))
        assertEquals(0,index.evaluatedProducts)
        assertEquals(ProductAvailability.SHORTAGE,index.status("p"))
        assertEquals("2",index.physicalStockLabel("p"))
        assertEquals("—",index.physicalStockLabel("missing"))
    }
}
