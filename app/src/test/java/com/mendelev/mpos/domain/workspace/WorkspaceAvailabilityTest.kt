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
}
