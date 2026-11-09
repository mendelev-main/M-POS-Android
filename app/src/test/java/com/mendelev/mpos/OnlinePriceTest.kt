package com.mendelev.mpos

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class OnlinePriceTest {
    private val products=listOf(WorkspaceProduct("p","Кофе","","12",2,"piece"))
    private val graph=listOf(RecipeProduct.Simple("p",QuantityUnit.PIECE,BigDecimal("3")))
    @Test fun websitePriceSurvivesCatalogRefreshWithoutChangingStockRequirements() {
        val line=CartEngine.quoted("p","10","line",products,graph).copy(quantity="2")
        val cart=CurrentCart(listOf(line),graph,"order",123,OrderDetails("Веб 1"))
        val refreshed=CartRevalidator.refresh(cart,products.map {it.copy(price="15",revision=3)},graph)
        assertEquals("10",refreshed.lines.single().unitPrice)
        assertEquals("10",refreshed.lines.single().quotedPrice)
        assertEquals(3L,refreshed.lines.single().references.getValue("p"))
        assertEquals(BigDecimal("20"),CartEngine.total(refreshed))
        assertEquals(CartEngine.reserved(cart),CartEngine.reserved(refreshed))
    }
    @Test fun addingRegularProductDoesNotMergeWithQuotedLine() {
        val quoted=CartEngine.quoted("p","12","quoted",products,graph)
        val cart=CurrentCart(listOf(quoted),graph,"order")
        val next=CartEngine.apply(cart,CartAction.Add("p",2,emptySet(),null,"regular"),products,graph,mapOf("p" to BigDecimal.TEN))
        assertEquals(2,next.lines.size)
        assertEquals(1,next.lines.count {it.quotedPrice!=null})
    }
    @Test fun requiredModifiersAreRefusedInsteadOfInventingSelection() {
        val group=ModifierGroupInput("g","Молоко","1","1",listOf(ModifierOptionInput("m","p2")))
        try {CartEngine.quoted("p","10","l",products.map {it.copy(groups=listOf(group))},graph);fail()} catch(_:CatalogFailure) {}
    }
}
