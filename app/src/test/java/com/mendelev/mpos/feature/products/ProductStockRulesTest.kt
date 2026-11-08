package com.mendelev.mpos.feature.products

import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.catalog.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ProductStockRulesTest {
    private val flour=ProductRow(ProductEntity("flour",null,"Мука","мука","5","2",1,unit="kg",displayUnit="g"),"10",1,"Сырьё")
    private val sugar=ProductRow(ProductEntity("sugar",null,"Сахар","сахар","5","0.003",1,unit="g"),"1000",1,"Сырьё")
    private val state=CatalogUi(products=listOf(flour,sugar))
    private fun draft()=ProductDraft(id="recipe",type="composite",details=ProductDetails(sellingBasis="recipe-batch",recipeYieldManual=false))
    @Test fun homogeneousIngredientsInferPhysicalYieldAndCostForOneWholeRecipe() {
        val d=ProductStockRules.inferYield(draft().copy(components=listOf(IngredientInput("flour","0.2","kg"),IngredientInput("sugar","50","g"))))
        assertEquals("g",d.unit);assertEquals("250",d.recipeYield)
        assertEquals(0,BigDecimal("0.55").compareTo(productEditorCost(d,state)))
        assertEquals(0,BigDecimal("0.55").compareTo(ProductStockRules.totalCost(d,state)))
        assertEquals(0,BigDecimal("0.4").compareTo(ProductStockRules.lineCost(d.components.first(),state)))
    }
    @Test fun addAgainMergesQuantitiesAndDisplayUnitConversionKeepsTinyPhysicalAmount() {
        val first=ProductStockRules.add(draft(),"flour",state)
        val next=ProductStockRules.add(first,"flour",state)
        assertEquals(1,next.components.size);assertEquals("2",next.components.single().quantity)
        assertEquals("2000",next.recipeYield)
        val tiny=ProductStockRules.changeUnit(IngredientInput("flour","0.0004","kg"),"g")
        assertEquals("0.4",tiny.quantity)
        assertEquals(0,BigDecimal("0.0008").compareTo(ProductStockRules.lineCost(tiny,state)))
    }
    @Test fun manualYieldMixedDimensionsAndCycleRefusalDoNotRewriteDraft() {
        val manual=draft().copy(recipeYield="3",details=draft().details!!.copy(recipeYieldManual=true),components=listOf(IngredientInput("flour","1","kg")))
        assertEquals(manual,ProductStockRules.inferYield(manual))
        assertEquals(0,BigDecimal("2").compareTo(ProductStockRules.totalCost(manual.copy(details=null),state)))
        val mixed=draft().copy(components=listOf(IngredientInput("flour","1","kg"),IngredientInput("x","1","ml")))
        assertEquals(mixed,ProductStockRules.inferYield(mixed))
        val bad=state.copy(components=listOf(RecipeComponentEntity("flour",0,"recipe","1","piece")))
        assertThrows(CatalogFailure::class.java) {ProductStockRules.add(draft(),"flour",bad)}
        assertThrows(CatalogFailure::class.java) {ProductStockRules.add(draft(),"recipe",state)}
    }
    @Test fun nestedRecipeQuantityIsPhysicalOutputRatherThanNumberOfBatches() {
        val base=ProductRow(ProductEntity("base",null,"Основа","основа","5","0",1,unit="g",type="composite",recipeYield="200"),"0",0,null)
        val s=state.copy(products=state.products+base,components=listOf(RecipeComponentEntity("base",0,"flour","0.2","kg")))
        val d=ProductStockRules.add(draft(),"base",s)
        assertEquals("1",d.components.single().quantity);assertEquals("g",d.components.single().unit)
        assertEquals(0,BigDecimal("0.002").compareTo(productEditorCost(d,s)))
    }
    @Test fun packageUnitAndRecipePreferencesSurviveDraftAndHaveDistinctFingerprints() {
        val d=ProductDraft(unit="kg",displayUnit="kg",packSize="500,0",details=ProductDetails(purchaseContentUnit="g"))
        assertEquals("0.5",d.input().packSize)
        assertEquals(d,ProductDraft.parse(d.json()))
        val encoded=ProductDetailsCodec.encode(d.id,d.details!!)
        assertEquals(2,org.json.JSONObject(encoded).getInt("version"))
        assertEquals(d.details,ProductDetailsCodec.decode(encoded,d.id))
        assertThrows(IllegalArgumentException::class.java) {ProductDetailsCodec.decode(org.json.JSONObject(encoded).put("version",1).toString(),d.id)}
        assertEquals("500",d.copy(displayUnit="g").input().packSize)
        val old=ProductInput("p",null,"Товар",null,"2","1","0",null,true,"cmd",details=ProductDetails("SKU","note","M"))
        assertEquals(CatalogRules.fingerprint("product.details.v1",old.copy(details=null).fingerprint(),"SKU","note","M"),old.fingerprint())
        assertNotEquals(old.fingerprint(),old.copy(details=old.details!!.copy(recipeYieldManual=true)).fingerprint())
        assertThrows(CatalogFailure::class.java) {d.copy(packSize="bad").input()}
    }
}
