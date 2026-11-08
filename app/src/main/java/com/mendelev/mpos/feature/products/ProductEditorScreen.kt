package com.mendelev.mpos.feature.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.recipeSnapshot
import com.mendelev.mpos.domain.catalog.*
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

internal fun productEditorCost(d: ProductDraft,state: CatalogUi): BigDecimal {
    if(d.type=="simple") {
        val perDisplay=d.cost.replace(',','.').toBigDecimal()
        val factor=quantityUnit(d.displayUnit).convert(BigDecimal.ONE,quantityUnit(d.unit))
        return perDisplay.divide(factor,MathContext.DECIMAL128)
    }
    if(d.components.isEmpty()) return BigDecimal.ZERO
    val graph=recipeSnapshot(state.products.map {it.product}.filter {it.id!=d.id},state.components)
    val candidate=RecipeProduct.Composite(d.id,quantityUnit(d.unit),d.recipeYield.replace(',','.').toBigDecimal(),d.components.map {
        RecipeComponent(it.productId,it.quantity.replace(',','.').toBigDecimal(),quantityUnit(it.unit))
    })
    val calculator=RecipeCalculator(graph+candidate)
    val request=listOf(RecipeRequest(d.id,if(d.details?.sellingBasis=="recipe-batch" || d.details?.sellingBasis==null && d.id in state.recipeBatchIds) candidate.yield else BigDecimal.ONE,quantityUnit(d.unit)))
    calculator.consumption(request)
    return calculator.cost(request)
}
private fun displayPrice(value: BigDecimal?)=value?.setScale(2,RoundingMode.HALF_UP)?.toPlainString()?.replace('.',',')?.plus(" BYN") ?: "—"

@Composable internal fun ProductEditorScreen(model: CatalogViewModel,state: CatalogUi,d: ProductDraft,onBack: ()->Unit,onDelete: ()->Unit,onOpenProduct: (String)->Unit={},onPhoto: ()->Unit) {
    var section by rememberSaveable(d.sessionId) {mutableStateOf("main")}
    LaunchedEffect(d.sessionId,section) {if(section=="usage" && d.expectedRevision!=null) model.loadUsage()}
    ProductEditorFrame(d.name,d.type,d.dirty,d.expectedRevision!=null,state.busy,section,{section=it},onBack,model::save,onDelete,
        summary={ProductEditorSummary(d,state)}) {
        if(state.message!=null) {MposNotice(state.message,state.error);Spacer(Modifier.height(16.dp))}
        if(state.field=="conflict") ProductEditorButton("Перечитать карточку",model::reloadProduct,enabled=!state.busy)
        when(section) {
            "main"->ProductMainPanel(model,state,d,onPhoto)
            "stock"->{ProductSectionHeading("Состав и остатки");Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {ProductConfiguration(model,state,d,showTypeSelector=false)}}
            "modifiers"->Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {ProductModifierEditor(model,state,d)}
            "usage"->{
                ProductSectionHeading("Где используется")
                ProductEditorNote("Связи сохранённого товара. Изменение ингредиента влияет на рецептуры, в которых он используется.")
                Spacer(Modifier.height(20.dp))
                val usage=state.usage
                ProductSectionHeading("Входит в состав",usage?.parents?.size?.toString() ?: "—")
                if(d.expectedRevision==null) ProductEditorNote("Связи появятся после сохранения товара.")
                else if(state.usageMessage!=null) {MposNotice(state.usageMessage,true);ProductEditorButton("Повторить",model::loadUsage)}
                else if(usage==null) ProductEditorNote("Загрузка связей…")
                else {
                    usage.parents.forEach {parent->
                        Surface(onClick={onOpenProduct(parent.id)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("productUsage:${parent.id}"),color=MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(vertical=14.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {Text(parent.name,fontWeight=FontWeight.Bold);ProductEditorNote(if(parent.direct) "Прямой состав" else "Через другие составные товары")}
                                Text("→")
                            }
                        }
                        HorizontalDivider()
                    }
                    if(usage.parents.isEmpty()) ProductEditorNote("Этот товар пока не входит в другие рецептуры.")
                    Spacer(Modifier.height(20.dp))
                    ProductEditorNote("Чеков native с товаром или его списанием: ${usage.receipts}")
                    ProductEditorNote("Отложенных заказов с товаром: ${usage.parked}")
                }
            }
            "web"->{
                ProductSectionHeading("Онлайн")
                listOf("Онлайн заказ" to "Товар доступен для добавления в заказ на сайте.","Онлайн меню" to "Товар отображается в меню для просмотра.").forEach {(title,note)->
                    Text(title,fontWeight=FontWeight.Bold);ProductEditorNote(note);ProductEditorNote("Будет подключено",Modifier.padding(top=8.dp,bottom=20.dp));HorizontalDivider()
                }
                Spacer(Modifier.height(20.dp))
                ProductEditorField("",{},"Описание для онлайн-платформы",enabled=false,singleLine=false,placeholder="Будет подключено на этапе онлайн-меню")
                ProductEditorNote("Сайт и синхронизация отложены в TODO.",Modifier.padding(top=12.dp))
            }
        }
    }
}
@Composable private fun ProductMainPanel(model: CatalogViewModel,state: CatalogUi,d: ProductDraft,onPhoto: ()->Unit) {
    val enabled=!state.busy
    ProductSectionHeading("Основная информация",if(d.expectedRevision==null) "Создание" else "Редактирование")
    ProductEditorNote("Название и категория помогают быстро найти товар на кассе.",Modifier.padding(bottom=20.dp))
    Column(verticalArrangement=Arrangement.spacedBy(22.dp)) {
        ProductEditorField(d.name,{v->model.change {it.copy(name=v)}},"Название товара",Modifier.testTag("productName"),enabled,error=state.message.takeIf {state.field=="name"},placeholder="Например, Пицца Маргарита")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            @Composable fun category(modifier: Modifier) {Box(modifier) {
                ProductEditorSelect("Категория",d.categoryId.orEmpty(),listOf("" to "Без категории")+state.categories.map {it.id to it.name},{v->model.change {it.copy(categoryId=v.ifBlank {null})}},enabled,Modifier.testTag("productCategory"))
            }}
            @Composable fun symbol(modifier: Modifier) {Box(modifier) {ProductEditorField(d.details?.symbol.orEmpty(),{v->model.change {it.copy(details=(it.details ?: ProductDetails()).copy(symbol=v))}},"Обозначение на плитке",Modifier.testTag("productSymbol"),enabled=enabled,placeholder="До 3 символов")}}
            if(maxWidth>500.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {category(Modifier.weight(1f));symbol(Modifier.weight(1f))}
            else Column(verticalArrangement=Arrangement.spacedBy(22.dp)) {category(Modifier.fillMaxWidth());symbol(Modifier.fillMaxWidth())}
        }
        ProductEditorField(d.details?.sku.orEmpty(),{v->model.change {it.copy(details=(it.details ?: ProductDetails()).copy(sku=v))}},"Артикул",Modifier.testTag("productSku"),enabled=enabled,placeholder="Внутренний код товара")
        Column {
            ProductEditorField(d.details?.note.orEmpty(),{v->model.change {it.copy(details=(it.details ?: ProductDetails()).copy(note=v))}},"Внутренняя заметка",Modifier.testTag("productNote"),enabled=enabled,singleLine=false)
            ProductEditorNote("Для сотрудников. Не выводится в чек или онлайн-меню.",Modifier.padding(top=8.dp))
        }
        Column(Modifier.testTag("productType")) {
            Text("Тип товара",fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("simple" to "Простой","composite" to "Составной").forEach {(type,label)->
                    Surface(onClick={changeProductType(model,type)},enabled=enabled && d.expectedRevision==null,modifier=Modifier.weight(1f).heightIn(min=44.dp).testTag("productType:$type"),shape=RoundedCornerShape(10.dp),
                        color=if(d.type==type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,if(d.type==type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                        Box(Modifier.padding(12.dp),contentAlignment=androidx.compose.ui.Alignment.Center) {Text(label,fontSize=14.sp,fontWeight=FontWeight.Bold,color=if(d.type==type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
            }
            ProductEditorNote("Простой товар хранится на складе. Составной расходует ингредиенты по рецептуре.",Modifier.padding(top=8.dp))
            if(d.expectedRevision!=null) ProductEditorNote("Тип сохранённого товара закреплён.")
        }
        ProductEditorField(d.price,{v->model.change {it.copy(price=v)}},"Цена продажи, BYN",Modifier.testTag("productPrice"),enabled,true,state.message.takeIf {state.field=="Цена"},placeholder="0,00")
        ProductPhotoEditor(model,d,enabled,onPhoto)
    }
}
internal fun changeProductType(model: CatalogViewModel,type: String) {
    model.change {it.copy(type=type,unit="piece",displayUnit="piece",stock="0",cost="0",stockEdited=false,noStockTracking=false,allowNegativeStock=false,
        minimumStock="",purchaseLabel="",packSize="",recipeYield="1",components=emptyList(),
        details=(it.details ?: ProductDetails()).copy(sellingBasis=if(type=="composite") "recipe-batch" else null,recipeYieldManual=if(type=="composite") false else null,purchaseContentUnit=null))}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ProductEditorSummary(d: ProductDraft,state: CatalogUi) {
    val cost=runCatching {productEditorCost(d,state)}.getOrNull()
    val price=runCatching {d.price.replace(',','.').toBigDecimal()}.getOrNull()
    Surface(shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Цена продажи",fontSize=12.sp,fontWeight=FontWeight.Bold)
                    Text(displayPrice(price),Modifier.padding(top=8.dp),fontSize=30.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold)
                }
            }
            listOf("Себестоимость" to cost,"Цена − себестоимость" to if(price!=null && cost!=null) price-cost else null).forEach {(label,value)->
                FlowRow(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(displayPrice(value),fontSize=12.sp,fontWeight=FontWeight.Bold)
                }
            }
            ProductEditorNote("Для составного товара себестоимость рассчитывается по ингредиентам.",Modifier.padding(20.dp))
            Surface(color=MaterialTheme.colorScheme.background) {ProductEditorNote("Количество в составе указывается на одну продаваемую единицу товара.",Modifier.padding(horizontal=20.dp,vertical=18.dp))}
        }
    }
}
