package com.mendelev.mpos.feature.products

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.recipeSnapshot
import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal

private fun units(base: String? = null) = QuantityUnit.entries.filter {
    base == null || it.dimension == quantityUnit(base).dimension
}.map { quantityCode(it) to unitLabel(quantityCode(it)) }

@Composable fun ProductConfiguration(model: CatalogViewModel, state: CatalogUi, d: ProductDraft) {
    val enabled = !state.busy
    if (d.expectedRevision == null) {
        MposSelect("Тип товара", d.type, listOf("simple" to "Простой", "composite" to "Составной"), { next ->
            model.change { it.copy(type=next, unit="piece", displayUnit="piece", stock="0", cost="0", stockEdited=false,
                noStockTracking=false, allowNegativeStock=false, minimumStock="", purchaseLabel="", packSize="", recipeYield="1", components=emptyList()) }
        }, enabled, Modifier.testTag("productType"))
        MposSelect(if(d.type=="simple") "Базовая единица" else "Единица выхода", d.unit, units(), { next ->
            model.change { it.copy(unit=next, displayUnit=next) }
        }, enabled, Modifier.testTag("baseUnit"))
    } else Text("${if(d.type=="simple") "Базовая единица" else "Единица выхода"}: ${unitLabel(d.unit)} · закреплена", style=MaterialTheme.typography.bodySmall)
    if(d.type=="simple") {
        MposSelect("Отображать количество в", d.displayUnit, units(d.unit), model::changeDisplayUnit, enabled, Modifier.testTag("displayUnit"))
        MposField(d.cost, { v -> model.change { it.copy(cost=v) } }, "Себестоимость, BYN за ${unitLabel(d.displayUnit)}", Modifier.testTag("productCost"), enabled, true)
        MposField(d.stock, { v -> model.change { it.copy(stock=v,stockEdited=true) } }, "Остаток, ${unitLabel(d.displayUnit)}", Modifier.testTag("productStock"), enabled, true,
            state.message.takeIf { state.field=="Остаток" })
        Text("Остаток можно изменить без указания причины. История изменений сохраняется автоматически.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        MposFlag("Не учитывать остаток", d.noStockTracking, { v -> model.change { it.copy(noStockTracking=v) } }, enabled)
        MposFlag("Разрешить продажу в минус", d.allowNegativeStock, { v -> model.change { it.copy(allowNegativeStock=v) } }, enabled)
        MposField(d.minimumStock, { v -> model.change { it.copy(minimumStock=v) } }, "Минимальный остаток, ${unitLabel(d.displayUnit)} (необязательно)", enabled=enabled, numeric=true)
        MposSelect("Единица закупки", d.purchaseLabel,
            listOf("" to "Не задано") + units(d.unit) + listOf("pack" to "Упаковка", "box" to "Коробка", "bottle" to "Бутылка"),
            { v -> model.change { it.copy(purchaseLabel=v) } }, enabled)
        MposField(d.packSize, { v -> model.change { it.copy(packSize=v) } }, "Количество в упаковке, ${unitLabel(d.displayUnit)} (необязательно)", enabled=enabled, numeric=true)
    } else {
        MposField(d.recipeYield, { v -> model.change { it.copy(recipeYield=v) } }, "Выход рецепта, ${unitLabel(d.unit)}", Modifier.testTag("recipeYield"), enabled, true)
        Text("Состав", style=MaterialTheme.typography.titleMedium)
        d.components.forEachIndexed { index, c ->
            val product=state.products.find { it.product.id==c.productId }?.product
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(product?.name ?: "Ингредиент больше не существует")
                MposField(c.quantity, { v -> model.change { draft -> draft.copy(components=draft.components.mapIndexed { i, row -> if(i==index) row.copy(quantity=v) else row }) } },
                    "Количество ингредиента ${index+1}", Modifier.testTag("ingredientQuantity$index"), enabled, true)
                MposSelect("Единица ингредиента ${index+1}", c.unit, units(product?.unit), { v ->
                    try {
                        val converted=CatalogRules.canonical(quantityUnit(c.unit).convert(c.quantity.replace(',', '.').toBigDecimal(),quantityUnit(v)))
                        model.change { draft -> draft.copy(components=draft.components.mapIndexed { i, row -> if(i==index) row.copy(unit=v, quantity=converted) else row }) }
                    } catch(_: Exception) { model.invalidNumber() }
                }, enabled)
                MposButton("Убрать ингредиент ${index+1}", { model.change { it.copy(components=it.components.filterIndexed { i, _ -> i!=index }) } }, enabled=enabled, style=ActionStyle.Secondary)
                HorizontalDivider()
            }
        }
        val choices=state.products.filter { it.product.id!=d.id }.map { it.product.id to it.product.name }
        if(choices.isEmpty()) MposNotice("Сначала сохраните товары для ингредиентов")
        else MposSelect("Добавить ингредиент", "", listOf("" to "Выбрать товар")+choices, { id ->
            state.products.find { it.product.id==id }?.product?.let { p ->
                model.change { it.copy(components=it.components+IngredientInput(p.id,"1",p.displayUnit)) }
            }
        }, enabled)
        val preview=runCatching {
            val graph=recipeSnapshot(state.products.map { it.product }.filter { it.id!=d.id },state.components)
            val candidate=RecipeProduct.Composite(d.id,quantityUnit(d.unit),d.recipeYield.replace(',', '.').toBigDecimal(),d.components.map {
                RecipeComponent(it.productId,it.quantity.replace(',', '.').toBigDecimal(),quantityUnit(it.unit))
            })
            val calculator=RecipeCalculator(graph+candidate)
            val request=listOf(RecipeRequest(d.id,BigDecimal.ONE,quantityUnit(d.unit)))
            calculator.consumption(request)
            CatalogRules.canonical(calculator.cost(request))
        }
        preview.fold(onSuccess={ Text("Себестоимость: $it BYN за ${unitLabel(d.unit)}", style=MaterialTheme.typography.titleMedium) },
            onFailure={ MposNotice((it as? CatalogFailure)?.message ?: "Заполните состав и выход рецепта", true) })
        Text("Остаток составного товара определяется ингредиентами. Собственного складского остатка нет.", style=MaterialTheme.typography.bodySmall)
    }
}
