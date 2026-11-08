package com.mendelev.mpos.feature.products

import com.mendelev.mpos.data.local.ProductEntity
import com.mendelev.mpos.data.local.ProductRow
import com.mendelev.mpos.domain.catalog.IngredientInput
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ProductEditorCostTest {
    @Test fun displayedGramsAreConvertedToTheSellingKilogramWithoutMoneyRounding() {
        val draft=ProductDraft(unit="kg",displayUnit="g",cost="0,002",price="5")
        assertEquals(0,BigDecimal("2").compareTo(productEditorCost(draft,CatalogUi())))
        assertEquals("0,002",draft.cost)
    }
    @Test fun recipeSummaryUsesOneSellingUnitAndRecipeYield() {
        val ingredient=ProductRow(ProductEntity("bean",null,"Основа","основа","5","2",1),"10",1,null)
        val draft=ProductDraft(id="recipe",type="composite",recipeYield="2",components=listOf(IngredientInput("bean","2","piece")))
        assertEquals(0,BigDecimal("2").compareTo(productEditorCost(draft,CatalogUi(products=listOf(ingredient)))))
    }
    @org.junit.Test fun importedRecipeBatchShowsCostForTheWholeYield() {
        val row=com.mendelev.mpos.data.local.ProductRow(com.mendelev.mpos.data.local.ProductEntity("bean",null,"Основа","основа","5","2",1),"10",1,null)
        val d=ProductDraft(id="recipe",type="composite",recipeYield="2",components=listOf(com.mendelev.mpos.domain.catalog.IngredientInput("bean","2","piece")))
        org.junit.Assert.assertEquals(0,productEditorCost(d,CatalogUi(products=listOf(row),recipeBatchIds=setOf("recipe"))).compareTo(java.math.BigDecimal("4")))
    }

}
