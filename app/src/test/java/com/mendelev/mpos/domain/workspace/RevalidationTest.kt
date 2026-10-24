package com.mendelev.mpos.domain.workspace

import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class RevalidationTest {
    private val products=listOf(WorkspaceProduct("p","Чай","","2",1,"piece"))
    private val graph=listOf(RecipeProduct.Simple("p",QuantityUnit.PIECE,BigDecimal.ONE))
    private val balances=mapOf("p" to BigDecimal.TEN)
    private fun cart(ps: List<WorkspaceProduct> = products,manual: String? = null)=CartEngine.apply(CurrentCart(sessionId="owner",createdAt=123,details=OrderDetails("Стол")),
        CartAction.Add("p",1,emptySet(),manual,"line"),ps,graph,balances)
    private fun reject(block: () -> Unit) {try {block();fail("Expected refusal")} catch(_:CatalogFailure) {}}
    @Test fun previewPreservesIdentityQuantityAndMetadataWithoutChangingOriginal() {
        val old=cart().let {it.copy(lines=it.lines.map {line->line.copy(quantity="0.5")})}
        val next=products.map {it.copy(name="Новый чай",price="3",revision=2)}
        val plan=CartRevalidator.preview(old,next,graph,balances,emptyMap())
        assertEquals(BigDecimal("1.5"),CartEngine.total(plan.after));assertEquals(BigDecimal("1.0"),CartEngine.total(old))
        assertEquals("line",plan.after.lines.single().id);assertEquals("0.5",plan.after.lines.single().quantity)
        assertEquals(old.details,plan.after.details);assertEquals(old.sessionId,plan.after.sessionId);assertEquals(123L,plan.after.createdAt)
        assertFalse(CartEngine.stale(plan.after.lines.single(),next));assertEquals("Чай",old.lines.single().name)
    }
    @Test fun manualPriceIsRetainedOnlyWhileManualModeIsStillApplicable() {
        val manualProducts=products.map {it.copy(price="0")};val old=cart(manualProducts,"7")
        assertEquals("7",CartRevalidator.preview(old,manualProducts,graph,balances,emptyMap()).after.lines.single().unitPrice)
        assertEquals("2",CartRevalidator.preview(old,products,graph,balances,emptyMap()).after.lines.single().unitPrice)
        reject {CartRevalidator.preview(cart(),manualProducts,graph,balances,emptyMap())}
        reject {CartRevalidator.preview(cart(),products.map {it.copy(unit="kg")},graph,balances,emptyMap())}
    }
    @Test fun stockAndParkedReserveAreCheckedTogetherAndTokenChangesAfterRelevantEdits() {
        val old=cart();val first=CartRevalidator.preview(old,products,graph,balances,emptyMap())
        reject {CartRevalidator.preview(old,products,graph,balances,mapOf("p" to BigDecimal.TEN))}
        val changed=CartRevalidator.preview(old,products,graph,balances,mapOf("p" to BigDecimal.ONE))
        assertNotEquals(first.token,changed.token)
        assertNotEquals(first.token,CartRevalidator.preview(old,products.map {it.copy(revision=2)},graph,balances,emptyMap()).token)
        val negative=graph.map {(it as RecipeProduct.Simple).copy(allowNegativeStock=true)}
        CartRevalidator.preview(old,products,negative,mapOf("p" to BigDecimal.ZERO),mapOf("p" to BigDecimal.TEN))
    }
    @Test fun missingSelectedOptionOrNewRequiredGroupNeverSilentlyChangesConfiguration() {
        val old=cart().let {it.copy(lines=it.lines.map {line->line.copy(options=listOf(ChosenOption("gone","Молоко","1","1")))})}
        reject {CartRevalidator.preview(old,products,graph,balances,emptyMap())}
        reject {CartRevalidator.preview(cart(),emptyList(),graph,balances,emptyMap())}
    }
    @Test fun recipeAndModifierChangesRecomputePhysicalConsumptionWithoutRoundingToThousandths() {
        val ps=products+WorkspaceProduct("milk","Молоко","","1",1,"kg")
        val groups=listOf(ModifierGroupInput("g","Добавки","0","1",listOf(ModifierOptionInput("o","milk",quantity="0.0004",priceDelta="0.5"))))
        val configured=ps.map {if(it.id=="p") it.copy(groups=groups) else it}
        val gs=listOf(RecipeProduct.Composite("p",QuantityUnit.PIECE,BigDecimal.ONE,listOf(RecipeComponent("milk",BigDecimal("0.0004"),QuantityUnit.KG))),RecipeProduct.Simple("milk",QuantityUnit.KG,BigDecimal.ONE))
        val stock=mapOf("milk" to BigDecimal.ONE)
        val old=CartEngine.apply(CurrentCart(sessionId="owner"),CartAction.Add("p",1,setOf("o"),null,"line"),configured,gs,stock)
        val changed=gs.map {if(it is RecipeProduct.Composite) it.copy(components=listOf(RecipeComponent("milk",BigDecimal("0.0008"),QuantityUnit.KG))) else it}
        val plan=CartRevalidator.preview(old,configured.map {it.copy(revision=2)},changed,stock,emptyMap())
        assertEquals(0,BigDecimal("0.0008").compareTo(plan.oldConsumption.getValue("milk")))
        assertEquals(0,BigDecimal("0.0012").compareTo(plan.newConsumption.getValue("milk")))
        assertEquals("2.5",plan.after.lines.single().unitPrice)
        val untracked=changed.map {if(it is RecipeProduct.Simple) it.copy(noStockTracking=true) else it}
        val untrackedPlan=CartRevalidator.preview(old,configured,untracked,emptyMap(),emptyMap())
        assertEquals(plan.newConsumption,untrackedPlan.newConsumption)
        assertTrue(CartEngine.reserved(untrackedPlan.after).isEmpty())
    }

}
