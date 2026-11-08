package com.mendelev.mpos.domain.catalog

/** Editable local metadata; never modifies the immutable Web import sidecar. */
data class ProductDetails(val sku: String="",val note: String="",val symbol: String="",val sellingBasis: String?=null,val recipeYieldManual: Boolean?=null,val purchaseContentUnit: String?=null) {
    fun validated(): ProductDetails {
        val code=sku.trim();val text=note.trim()
        if(code.length>80 || code.any {it.isISOControl()}) throw CatalogFailure("sku","Артикул: до 80 символов без переносов")
        if(text.length>2000 || text.any {it.isISOControl() && it!='\n' && it!='\t'}) throw CatalogFailure("note","Заметка: до 2000 символов")
        if(sellingBasis!=null && sellingBasis !in setOf("stock-unit","recipe-batch")) throw CatalogFailure("basis","Некорректная единица продажи")
        purchaseContentUnit?.let(::quantityUnit)
        return copy(sku=code,note=text,symbol=CategoryAppearance.symbol(symbol))
    }
}
