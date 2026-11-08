package com.mendelev.mpos.feature.products

import androidx.compose.runtime.Composable

@Composable fun ProductConfiguration(model: CatalogViewModel,state: CatalogUi,d: ProductDraft,showTypeSelector: Boolean=true) {
    ProductStockPanels(model,state,d,showTypeSelector)
}
