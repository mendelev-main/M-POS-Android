package com.mendelev.mpos.feature.products

import com.mendelev.mpos.data.local.recipeSnapshot
import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

/** Editor quantities stay physical; a composite ingredient is not a count of its recipes. */
internal object ProductStockRules {
    fun inferYield(d: ProductDraft): ProductDraft {
        if(d.type!="composite" || d.details?.recipeYieldManual==true || d.expectedRevision!=null && d.details?.recipeYieldManual==null || d.components.isEmpty()) return d
        return runCatching {
            val dimensions=d.components.map {quantityUnit(it.unit).dimension}.distinct()
            if(dimensions.size!=1) return d
            val unit=when(quantityUnit(d.components.first().unit)) {
                QuantityUnit.KG,QuantityUnit.G->"g"
                QuantityUnit.L,QuantityUnit.ML->"ml"
                else->"piece"
            }
            val total=d.components.fold(BigDecimal.ZERO) {sum,c->
                val q=c.quantity.replace(',','.').toBigDecimal();require(q.signum()>0)
                sum+quantityUnit(c.unit).convert(q,quantityUnit(unit))
            }
            require(total.stripTrailingZeros().scale()<=12)
            d.copy(unit=unit,displayUnit=unit,recipeYield=CatalogRules.canonical(total))
        }.getOrDefault(d)
    }
    fun add(d: ProductDraft,id: String,state: CatalogUi): ProductDraft {
        if(id==d.id) throw CatalogFailure("cycle","Рецептура содержит цикл")
        val p=state.products.single {it.product.id==id}.product
        val children=state.components.groupBy {it.ownerId}
        val completed=mutableSetOf<String>()
        fun visit(current: String,path: Set<String>) {
            if(current==d.id || current in path || path.size>=64) throw CatalogFailure("cycle","Рецептура содержит цикл")
            if(current in completed) return
            children[current].orEmpty().forEach {visit(it.productId,path+current)}
            completed.add(current)
        }
        visit(id,emptySet())
        val index=d.components.indexOfFirst {it.productId==id}
        val rows=if(index<0) d.components+IngredientInput(id,"1",p.unit) else d.components.mapIndexed {i,c->
            if(i!=index) c else c.copy(quantity=CatalogRules.canonical(c.quantity.replace(',','.').toBigDecimal()+quantityUnit(p.unit).convert(BigDecimal.ONE,quantityUnit(c.unit))))
        }
        return inferYield(d.copy(components=rows))
    }
    fun changeUnit(c: IngredientInput,next: String)=c.copy(unit=next,quantity=CatalogRules.canonical(quantityUnit(c.unit).convert(c.quantity.replace(',','.').toBigDecimal(),quantityUnit(next))))
    fun lineCost(c: IngredientInput,state: CatalogUi): BigDecimal = RecipeCalculator(recipeSnapshot(state.products.map {it.product},state.components))
        .cost(listOf(RecipeRequest(c.productId,c.quantity.replace(',','.').toBigDecimal(),quantityUnit(c.unit))))
    fun totalCost(d: ProductDraft,state: CatalogUi): BigDecimal = RecipeCalculator(recipeSnapshot(state.products.map {it.product},state.components))
        .cost(d.components.map {RecipeRequest(it.productId,it.quantity.replace(',','.').toBigDecimal(),quantityUnit(it.unit))})
    fun packInput(d: ProductDraft): String?=if(d.packSize.isBlank()) null else CatalogRules.canonical(
        quantityUnit(d.details?.purchaseContentUnit ?: d.displayUnit).convert(CatalogRules.decimal(d.packSize,12,"Упаковка").toBigDecimal(),quantityUnit(d.displayUnit)))
}
