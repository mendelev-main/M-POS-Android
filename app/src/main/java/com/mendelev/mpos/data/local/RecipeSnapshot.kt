package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.*

fun recipeSnapshot(products: List<ProductEntity>, components: List<RecipeComponentEntity>): List<RecipeProduct> = products.map { p ->
    if (p.type == "simple") RecipeProduct.Simple(p.id, quantityUnit(p.unit), p.cost.toBigDecimal(), p.noStockTracking, p.allowNegativeStock)
    else RecipeProduct.Composite(p.id, quantityUnit(p.unit), p.recipeYield.toBigDecimal(), components.filter { it.ownerId == p.id }.map {
        RecipeComponent(it.productId, it.quantity.toBigDecimal(), quantityUnit(it.unit))
    })
}
