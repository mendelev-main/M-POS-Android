package com.mendelev.mpos.domain.catalog

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ModifierRulesTest {
    private val option=ModifierOptionInput("option","milk",quantity="0.05",priceDelta="1,50")
    private val group=ModifierGroupInput("group","Молоко","1","1",listOf(option))
    private fun rejects(g: ModifierGroupInput) { assertThrows(CatalogFailure::class.java) { ModifierRules.validate("coffee",listOf(g),setOf("milk")) } }
    @Test fun rawLimitsDuplicatesAndBrokenReferencesAreRejectedBeforeNormalization() {
        rejects(group.copy(minimum="2"));rejects(group.copy(minimum="2",maximum="1"))
        rejects(group.copy(minimum="-1"));rejects(group.copy(maximum="0"));rejects(group.copy(minimum="1.5"))
        rejects(group.copy(options=emptyList()));rejects(group.copy(options=listOf(option,option.copy(id="another"))))
        rejects(group.copy(options=listOf(option.copy(productId="coffee"))))
        rejects(group.copy(options=listOf(option.copy(productId="gone"))))
        rejects(group.copy(options=listOf(option.copy(quantity="0"))))
        rejects(group.copy(options=listOf(option.copy(priceDelta="1.001"))))
        assertThrows(CatalogFailure::class.java) { ModifierRules.validate("coffee",listOf(group,group),setOf("milk")) }
    }
    @Test fun selectionChecksCardinalityAndFinalPriceWhilePreservingSignedDecimalDelta() {
        val valid=ModifierRules.validate("coffee",listOf(group.copy(options=listOf(option.copy(priceDelta="-1.50")))),setOf("milk"))
        assertEquals("-1.5",valid.single().options.single().priceDelta)
        assertEquals(0,BigDecimal("0.5").compareTo(ModifierRules.selectionPrice(BigDecimal("2"),valid,setOf("option"))))
        assertThrows(CatalogFailure::class.java) { ModifierRules.selectionPrice(BigDecimal("1"),valid,setOf("option")) }
        assertThrows(CatalogFailure::class.java) { ModifierRules.selectionPrice(BigDecimal("2"),valid,emptySet()) }
        assertThrows(CatalogFailure::class.java) { ModifierRules.selectionPrice(BigDecimal("2"),valid,setOf("missing")) }
    }
    @Test fun selectedCompositeModifierExpandsWithMainProductAndPerIngredientFlags() {
        val products=listOf(RecipeProduct.Simple("coffee",QuantityUnit.PIECE,BigDecimal.ONE,noStockTracking=true),
            RecipeProduct.Simple("milk",QuantityUnit.L,BigDecimal("2")),
            RecipeProduct.Composite("foam",QuantityUnit.PIECE,BigDecimal("2"),listOf(RecipeComponent("milk",BigDecimal("0.1"),QuantityUnit.L))))
        val groups=listOf(group.copy(options=listOf(option.copy(productId="foam",quantity="1"))))
        val configured=configureModifiers("coffee",BigDecimal("3"),groups,setOf("option"),products)
        assertEquals(0,BigDecimal("4.5").compareTo(configured.price))
        val consumption=RecipeCalculator(products).consumption(configured.requests)
        assertEquals(setOf("milk"),consumption.keys)
        assertEquals(0,BigDecimal("0.05").compareTo(consumption["milk"]))
    }
    @Test fun photoAndModifierChangesHaveDistinctFingerprintsButDefaultsKeepPriorCommands() {
        val i=ProductInput("coffee",null,"Кофе",null,"3","1","0",null,false,"cmd")
        assertEquals(i.fingerprint(),i.copy(modifierGroups=emptyList(),photoId=null).fingerprint())
        assertNotEquals(i.fingerprint(),i.copy(modifierGroups=listOf(group)).fingerprint())
        assertNotEquals(i.fingerprint(),i.copy(photoId="00000000-0000-0000-0000-000000000001.jpg").fingerprint())
        assertNotEquals(i.copy(modifierGroups=listOf(group)).fingerprint(),i.copy(modifierGroups=listOf(group.copy(options=listOf(option.copy(priceDelta="2"))))).fingerprint())
    }
    @Test fun incompleteSelectionCanShowExactPreviewWhileCommitStillRequiresGroupMinimum() {
        val valid=ModifierRules.validate("coffee",listOf(group),setOf("milk"))
        assertEquals(BigDecimal("2"),ModifierRules.selectionPrice(BigDecimal("2"),valid,emptySet(),requireComplete=false))
        assertThrows(CatalogFailure::class.java) {ModifierRules.selectionPrice(BigDecimal("2"),valid,emptySet())}
        assertEquals(BigDecimal("3.5"),ModifierRules.selectionPrice(BigDecimal("2"),valid,setOf("option"),requireComplete=false))
        assertThrows(CatalogFailure::class.java) {ModifierRules.selectionPrice(BigDecimal("2"),valid,setOf("unknown"),requireComplete=false)}
    }

}
