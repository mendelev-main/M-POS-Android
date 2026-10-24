package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WorkspaceRulesTest {
    private val products=listOf(WorkspaceProduct("tea","Чай","c","2",1,"piece"),WorkspaceProduct("milk","Молоко","c","0",1,"l"))
    private val graph=listOf(RecipeProduct.Simple("tea",QuantityUnit.PIECE,BigDecimal.ONE),RecipeProduct.Simple("milk",QuantityUnit.L,BigDecimal.ONE))
    private fun add(id: String, manual: String? = null,selected: Set<String> = emptySet())=CartAction.Add(id,1,selected,manual,"line-${java.util.UUID.randomUUID()}")
    private fun reject(block: () -> Unit) { try {block();fail("Expected refusal")} catch(_:CatalogFailure) {} }
    @Test fun mergeUsesCanonicalManualPriceAndOptionOrder() {
        val groups=listOf(ModifierGroupInput("g","Добавки","0","2",listOf(ModifierOptionInput("a","milk",quantity="0.1",priceDelta="0.5"),ModifierOptionInput("b","milk",quantity="0.2",priceDelta="0.3"))))
        // Distinct product references are required within a group.
        val ps=products+WorkspaceProduct("sugar","Сахар","c","1",1,"piece")
        val gs=graph+RecipeProduct.Simple("sugar",QuantityUnit.PIECE,BigDecimal.ONE)
        val configured=ps.map { if(it.id=="tea") it.copy(price="0",groups=groups.map { g -> g.copy(options=g.options.map { o -> if(o.id=="b") o.copy(productId="sugar") else o }) }) else it }
        val balances=mapOf("tea" to BigDecimal.TEN,"milk" to BigDecimal.TEN,"sugar" to BigDecimal.TEN)
        var cart=CartEngine.apply(CurrentCart(),add("tea","2,50",linkedSetOf("b","a")),configured,gs,balances)
        val id=cart.lines.single().id
        cart=CartEngine.apply(cart,add("tea","2.50",linkedSetOf("a","b")),configured,gs,balances)
        assertEquals(1,cart.lines.size);assertEquals("2",cart.lines.single().quantity);assertEquals(id,cart.lines.single().id)
        assertEquals(BigDecimal("6.6"),CartEngine.total(cart))
        cart=CartEngine.apply(cart,add("tea","3",setOf("a","b")),configured,gs,balances)
        assertEquals(2,cart.lines.size)
        reject { CartEngine.apply(cart,add("tea","0"),configured,gs,balances) }
        reject { CartEngine.apply(cart,add("tea","2.501"),configured,gs,balances) }
    }
    @Test fun shortagesAreAggregateAndDecreaseIsAlwaysPossible() {
        val balances=mapOf("tea" to BigDecimal("2"),"milk" to BigDecimal.TEN)
        var cart=CartEngine.apply(CurrentCart(),add("tea"),products,graph,balances)
        cart=CartEngine.apply(cart,add("tea"),products,graph,balances)
        reject { CartEngine.apply(cart,add("tea"),products,graph,balances) }
        val id=cart.lines.single().id
        cart=CartEngine.apply(cart,CartAction.Step(id,-1),products,graph,balances+ ("tea" to BigDecimal.ZERO))
        assertEquals("1",cart.lines.single().quantity)
        reject { CartEngine.apply(cart,CartAction.SetQuantity(id,"0",0),products,graph,balances) }
        assertTrue(CartEngine.apply(cart,CartAction.Step(id,-1),products,graph,balances).lines.isEmpty())
    }
    @Test fun snapshotSurvivesCatalogEditsAndThreeThirdsConsumeOneExactly() {
        val ps=products+WorkspaceProduct("cake","Торт","c","3",1,"piece")
        val gs=graph+RecipeProduct.Composite("cake",QuantityUnit.PIECE,BigDecimal("3"),listOf(RecipeComponent("milk",BigDecimal.ONE,QuantityUnit.L)))
        val balances=mapOf("tea" to BigDecimal.TEN,"milk" to BigDecimal.ONE)
        var cart=CartEngine.apply(CurrentCart(),add("cake"),ps,gs,balances)
        val id=cart.lines.single().id
        cart=CartEngine.apply(cart,CartAction.SetQuantity(id,"3",0),ps,gs,balances)
        assertEquals(BigDecimal.ONE,CartEngine.reserved(cart).getValue("milk"))
        val changed=ps.map { if(it.id=="milk") it.copy(revision=2) else it }
        assertTrue(CartEngine.stale(cart.lines.single(),changed))
        reject { CartEngine.apply(cart,CartAction.Step(id,1),changed,gs,balances) }
        assertEquals("2",CartEngine.apply(cart,CartAction.Step(id,-1),changed,gs,balances).lines.single().quantity)
        assertEquals(BigDecimal.ONE,CartEngine.reserved(cart).getValue("milk"))
    }
    @Test fun stockFlagsAndRequiredModifiersRemainPerIngredient() {
        val ps=products.map { if(it.id=="tea") it.copy(groups=listOf(ModifierGroupInput("g","Молоко","1","1",listOf(ModifierOptionInput("m","milk",quantity="0.0004"))))) else it }
        val gs=graph.map { if(it.id=="tea") (it as RecipeProduct.Simple).copy(noStockTracking=true) else it }
        val balances=mapOf("milk" to BigDecimal("0.0004"))
        reject { CartEngine.apply(CurrentCart(),add("tea"),ps,gs,balances) }
        val cart=CartEngine.apply(CurrentCart(),add("tea",selected=setOf("m")),ps,gs,balances)
        assertEquals(mapOf("milk" to BigDecimal("0.0004")),CartEngine.reserved(cart))
        reject { CartEngine.apply(cart,add("tea",selected=setOf("m")),ps,gs,balances) }
        val negative=gs.map { if(it.id=="milk") (it as RecipeProduct.Simple).copy(allowNegativeStock=true) else it }
        assertEquals(1,CartEngine.apply(CurrentCart(),add("tea",selected=setOf("m")),ps,negative,mapOf("milk" to BigDecimal.ZERO)).lines.size)
    }
    @Test fun foldersReorderWithinParentAndDeletingReturnsProductsToRoot() {
        val cats=setOf("c","")
        var layout=LayoutEngine.apply(emptyList(),products,cats,LayoutAction.SaveFolder("f","c","Популярное"))
        layout=LayoutEngine.apply(layout,products,cats,LayoutAction.MoveProduct("tea","f"))
        layout=LayoutEngine.apply(layout,products,cats,LayoutAction.MoveProduct("milk","f"))
        layout=LayoutEngine.apply(layout,products,cats,LayoutAction.Step("product","milk",-1))
        assertEquals(listOf("milk","tea"),layout.filter { it.folderId=="f" }.map { it.id })
        reject { LayoutEngine.apply(layout,products,cats,LayoutAction.SaveFolder("g","c","  популярное ")) }
        layout=LayoutEngine.apply(layout,products,cats,LayoutAction.DeleteFolder("f"))
        assertTrue(layout.all { it.type=="product" && it.folderId.isEmpty() })
        val moved=products.map { if(it.id=="tea") it.copy(categoryId="new") else it }
        assertEquals("",LayoutEngine.project(layout,moved,cats+"new").first { it.id=="tea" }.folderId)
    }
}
