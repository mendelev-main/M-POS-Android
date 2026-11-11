package com.mendelev.mpos.feature.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import java.math.RoundingMode

private fun stockUnits(base: String?=null)=QuantityUnit.entries.filter {base==null || it.dimension==quantityUnit(base).dimension}.map {quantityCode(it) to unitLabel(quantityCode(it))}
private fun costLabel(value: BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.',',')?.plus(" BYN") ?: "—"
@Composable private fun StockCard(title: String,content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Text(title,fontSize=14.sp,fontWeight=FontWeight.Bold);content()}
    }
}
@Composable private fun StockPair(left: @Composable (Modifier)->Unit,right: @Composable (Modifier)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth>=470.dp) Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {left(Modifier.weight(1f));right(Modifier.weight(1f))}
        else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {left(Modifier.fillMaxWidth());right(Modifier.fillMaxWidth())}
    }
}
@Composable internal fun ProductStockPanels(model: CatalogViewModel,state: CatalogUi,d: ProductDraft,showTypeSelector: Boolean) {
    val enabled=!state.busy
    fun change(block: (ProductDraft)->ProductDraft) {
        try {model.change(block)} catch(e:CatalogFailure) {model.configurationProblem(e.message ?: "Проверьте состав")} catch(_:Exception) {model.invalidNumber()}
    }
    if(showTypeSelector && d.expectedRevision==null) ProductEditorSelect("Тип товара",d.type,listOf("simple" to "Простой","composite" to "Составной"),{changeProductType(model,it)},enabled,Modifier.testTag("productType"))
    if(d.type=="simple") {
        ProductEditorSwitch("Разрешить продажу при отрицательном остатке",d.allowNegativeStock,{v->change {it.copy(allowNegativeStock=v)}},enabled,Modifier.testTag("productAllowNegativeStock"))
        ProductEditorSwitch("Не вести учёт остатков",d.noStockTracking,{v->change {it.copy(noStockTracking=v)}},enabled,Modifier.testTag("productNoStockTracking"))
        ProductEditorSelect("Единица ввода остатка и себестоимости",d.displayUnit,stockUnits(d.unit.takeIf {d.expectedRevision!=null}),{next->
            if(d.expectedRevision==null && quantityUnit(next).dimension!=quantityUnit(d.unit).dimension)
                change {it.copy(unit=next,displayUnit=next,details=(it.details ?: ProductDetails()).copy(purchaseContentUnit=null))}
            else model.changeDisplayUnit(next)
        },enabled,Modifier.testTag("displayUnit"))
        ProductEditorNote("При смене кг ↔ г или л ↔ мл значения карточки пересчитываются. Приёмки и продажи используют базовую единицу: ${unitLabel(d.unit)}. Цена продажи не пересчитывается.")
        StockPair({modifier->Box(modifier) {ProductEditorField(d.cost,{v->change {it.copy(cost=v)}},"Себестоимость за 1 ${unitLabel(d.displayUnit)}",Modifier.testTag("productCost"),enabled,true,readOnly=d.expectedRevision!=null)}},
            {modifier->Box(modifier) {ProductEditorField(d.stock,{v->change {it.copy(stock=v,stockEdited=true)}},"${if(d.expectedRevision==null) "Начальный остаток" else "Текущий остаток"}, ${unitLabel(d.displayUnit)}",Modifier.testTag("productStock"),enabled && !d.noStockTracking,true)}})
        ProductEditorNote(if(d.expectedRevision!=null) "Себестоимость изменяется только при приёмке по средневзвешенной стоимости." else "Укажите начальную себестоимость. После создания её будут изменять приёмки.")
        StockCard("Как обычно закупаем") {
            ProductEditorNote("Эти значения автоматически подставятся в следующий заказ, но их всегда можно изменить.")
            val contentUnit=d.details?.purchaseContentUnit ?: d.displayUnit
            StockPair({modifier->Box(modifier) {ProductEditorSelect("Единица закупки",d.purchaseLabel,listOf("" to "Не задано")+stockUnits(d.unit)+listOf("pack" to "Упаковка","box" to "Коробка","bottle" to "Бутылка"),{v->change {it.copy(purchaseLabel=v)}},enabled,Modifier.testTag("purchaseUnit"))}},
                {modifier->Box(modifier) {ProductEditorField(d.packSize,{v->change {it.copy(packSize=v)}},"Количество внутри",Modifier.testTag("purchasePackSize"),enabled,true,placeholder="Например, 12")}})
            ProductEditorSelect("Единица внутри",contentUnit,stockUnits(d.unit),{v->change {it.copy(details=(it.details ?: ProductDetails()).copy(purchaseContentUnit=v))}},enabled,Modifier.testTag("purchaseContentUnit"))
        }
        ProductEditorField(d.minimumStock,{v->change {it.copy(minimumStock=v)}},"Минимальный остаток, ${unitLabel(d.displayUnit)}",Modifier.testTag("minimumStock"),enabled,true,placeholder="Не задан")
        ProductEditorNote("Подсказка в карточке; не блокирует продажи и не создаёт заказ поставщику.")
    } else {
        StockCard("Выход готового состава") {
            StockPair({modifier->Box(modifier) {ProductEditorField(d.recipeYield,{v->change {it.copy(recipeYield=v,details=(it.details ?: ProductDetails()).copy(recipeYieldManual=true))}},"Вес / объём",Modifier.testTag("recipeYield"),enabled,true)}},
                {modifier->Box(modifier) {ProductEditorSelect("Единица",d.unit,stockUnits(),{v->change {it.copy(unit=v,displayUnit=v,details=(it.details ?: ProductDetails()).copy(recipeYieldManual=true))}},enabled,Modifier.testTag("recipeUnit"))}})
        }
        ProductSectionHeading("Состав товара")
        val batch=d.details?.sellingBasis=="recipe-batch" || d.details?.sellingBasis==null && d.id in state.recipeBatchIds
        ProductEditorNote((if(batch) "Расход на одну продаваемую позицию." else "Состав на ${d.recipeYield} ${unitLabel(d.unit)}; продажа по 1 ${unitLabel(d.unit)}.")+" Выберите г/кг или мл/л рядом с количеством.")
        Text("Себестоимость: ${costLabel(runCatching {ProductStockRules.totalCost(d,state)}.getOrNull())}",Modifier.testTag("recipeTotalCost"),fontSize=14.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
        if(d.components.isEmpty()) ProductEditorNote("Состав пока пуст. Найдите товар ниже и нажмите «Добавить».",Modifier.padding(vertical=16.dp))
        d.components.forEachIndexed {index,c->
            val row=state.products.find {it.product.id==c.productId}
            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp)) {
                    val showCost=maxWidth>=450.dp
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(row?.product?.name ?: "Ингредиент не найден",fontSize=13.sp,lineHeight=17.5.sp,fontWeight=FontWeight.Bold)
                            ProductEditorNote("${row?.categoryName ?: "Без категории"} · ${unitLabel(row?.product?.unit ?: c.unit)}")
                        }
                        if(showCost) Text(costLabel(runCatching {ProductStockRules.lineCost(c,state)}.getOrNull()),fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Box(Modifier.width(88.dp)) {ProductEditorField(c.quantity,{v->change {draft->ProductStockRules.inferYield(draft.copy(components=draft.components.mapIndexed {i,item->if(i==index) item.copy(quantity=v) else item}))}},"Количество: ${row?.product?.name.orEmpty()}",Modifier.testTag("ingredientQuantity$index"),enabled,true,compact=true)}
                        ProductEditorSelect("Единица ингредиента",c.unit,stockUnits(row?.product?.unit),{v->change {draft->ProductStockRules.inferYield(draft.copy(components=draft.components.mapIndexed {i,item->if(i==index) ProductStockRules.changeUnit(item,v) else item}))}},enabled,Modifier.width(82.dp).testTag("ingredientUnit$index"),compact=true)
                        Surface(onClick={change {ProductStockRules.inferYield(it.copy(components=it.components.filterIndexed {i,_->i!=index}))}},enabled=enabled,
                            modifier=Modifier.size(44.dp).testTag("removeIngredient$index").semantics {contentDescription="Удалить компонент"},shape=RoundedCornerShape(10.dp),contentColor=MaterialTheme.colorScheme.error) {
                            Box(contentAlignment=Alignment.Center) {MposPosIcon("close",Modifier.size(18.dp))}
                        }
                    }
                }
            }
        }
        var query by rememberSaveable(d.sessionId) {mutableStateOf("")}
        var category by rememberSaveable(d.sessionId) {mutableStateOf<String?>(null)}
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {ProductEditorField(query,{query=it},"Добавить в состав",Modifier.testTag("ingredientSearch"),enabled,placeholder="Поиск товара или ингредиента…")}
            Surface(onClick={query=""},enabled=enabled,modifier=Modifier.size(48.dp).testTag("ingredientSearchClear").semantics {contentDescription="Очистить поиск"},shape=RoundedCornerShape(10.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {Box(contentAlignment=Alignment.Center) {MposPosIcon("close",Modifier.size(18.dp))}}
        }
        val choices=state.products.filter {it.product.id!=d.id}
        val categories=choices.map {it.categoryName ?: "Без категории"}.distinct().sortedWith {a,b->CatalogPresentationRules.compareNames(a,b)}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (listOf<String?>(null)+categories).forEach {value->FilterChip(selected=category==value,onClick={category=value},enabled=enabled,modifier=Modifier.heightIn(min=44.dp),shape=RoundedCornerShape(999.dp),label={Text(value ?: "Все")})}
        }
        val filtered=choices.filter {(category==null || (it.categoryName ?: "Без категории")==category) && CatalogPresentationRules.matches(it.product.name,it.categoryName ?: "Без категории",query)}
            .sortedWith {a,b->CatalogPresentationRules.compareNames(a.product.name,b.product.name)}
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp).testTag("ingredientOptions"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(filtered.isEmpty()) item {ProductEditorNote("Нет подходящих товаров.",Modifier.padding(vertical=18.dp))}
            items(filtered,key={it.product.id}) {row->
                val selected=d.components.any {it.productId==row.product.id}
                Surface(onClick={change {ProductStockRules.add(it,row.product.id,state)}},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp).testTag("ingredientAdd:${row.product.id}"),shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                    Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        val cost=remember(row,state.products,state.components) {runCatching {ProductStockRules.lineCost(IngredientInput(row.product.id,if(row.product.type=="composite") row.product.recipeYield else "1",row.product.unit),state)}.getOrNull()}
                        Column(Modifier.weight(1f)) {Text(row.product.name,fontSize=13.sp,fontWeight=FontWeight.Bold);ProductEditorNote("${row.categoryName ?: "Без категории"} · себестоимость ${costLabel(cost)}${if(selected) " · уже добавлен" else ""}")}
                        Text(if(selected) "+ ещё" else "Добавить",fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
