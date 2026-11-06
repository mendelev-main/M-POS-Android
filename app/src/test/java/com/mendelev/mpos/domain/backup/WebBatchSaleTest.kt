package com.mendelev.mpos.domain.backup

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class WebBatchSaleTest {
    @Test fun newImportedRecipeSaleUsesBatchPriceAndPhysicalYieldWithoutChangingNativeUnitSales() {
        val graph=listOf(RecipeProduct.Simple("ingredient",QuantityUnit.KG,BigDecimal("2")),
            RecipeProduct.Composite("recipe",QuantityUnit.KG,BigDecimal("3"),listOf(RecipeComponent("ingredient",BigDecimal("3"),QuantityUnit.KG))))
        val products=listOf(WorkspaceProduct("ingredient","Ingredient","","2",1,"kg"),WorkspaceProduct("recipe","Recipe","","10",1,"kg",sellingBasis="recipe-batch"))
        fun added(ps: List<WorkspaceProduct>)=CartEngine.apply(CurrentCart(),CartAction.Add("recipe",1,emptySet(),null,"line"),ps,graph,mapOf("ingredient" to BigDecimal("10")))
        val imported=added(products)
        assertEquals("10",imported.lines.single().unitPrice)
        assertEquals("recipe-batch",imported.lines.single().sellingBasis)
        assertEquals(0,CartEngine.reserved(imported).getValue("ingredient").compareTo(BigDecimal("3")))
        val native=added(products.map {it.copy(sellingBasis="stock-unit")})
        assertEquals(0,CartEngine.reserved(native).getValue("ingredient").compareTo(BigDecimal.ONE))
    }
}
