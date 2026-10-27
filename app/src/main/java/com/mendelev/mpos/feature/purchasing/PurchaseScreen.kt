package com.mendelev.mpos.feature.purchasing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PurchaseScreen(model: PurchaseViewModel,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle();val form=ui.form;val snapshot=ui.snapshot
    val locked=ui.pending || ui.unresolved!=null
    val editable=!locked && !ui.blocked
    var expanded by rememberSaveable {mutableStateOf(false)}
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(model) {model.refresh()}
    LaunchedEffect(snapshot?.draft?.id) {if(snapshot?.draft!=null) expanded=true}
    fun back() {if(!locked) {if(ui.dirty) dialog="back" else onBack()}}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Заказы поставщикам",navigationEnabled=!locked && !ui.dirty && dialog==null) {}
        Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MposButton("Назад",::back,enabled=!locked,style=ActionStyle.Secondary)
                MposButton("История",{dialog="soon"},enabled=!locked,style=ActionStyle.Secondary)
                MposButton("Новый заказ",{expanded=!expanded},enabled=!locked && !ui.blocked,modifier=Modifier.testTag("purchaseNew"))
                MposButton("Перечитать",{if(ui.dirty) dialog="reload" else model.refresh()},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseReload"))
            }
            Text("Заказы поставщикам",style=MaterialTheme.typography.titleLarge)
            if(ui.message!=null) MposNotice(ui.message!!,ui.blocked || ui.unresolved!=null)
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("purchaseRetry"))
            if(expanded && snapshot!=null) {
                MposSelect("Поставщик",form?.supplierId ?: "",(if(form==null) listOf("" to "Выберите поставщика") else emptyList())+snapshot.suppliers.map {it.id to it.name},model::supplier,
                    enabled=!locked && !ui.blocked && form?.lines.orEmpty().isEmpty(),modifier=Modifier.testTag("purchaseSupplier"))
                if(form!=null) {
                    val supplier=snapshot.suppliers.find {it.id==form.supplierId}
                    val available=snapshot.products.filter {it.id in supplier?.productIds.orEmpty()}
                    val missing=form.lines.filter {l->available.none {it.id==l.productId}}
                    if(supplier?.revision!=form.supplierRevision) Text("Поставщик изменился. Обновите ссылки; ввод останется прежним.",color=MaterialTheme.colorScheme.error)
                    Text(if(ui.dirty) "Есть несохранённые изменения" else "Сохранённый черновик",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("purchaseDraftState"))
                    LazyColumn(Modifier.weight(1f).testTag("purchaseLines"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(available,key={it.id}) {p->
                            val line=form.lines.find {it.productId==p.id} ?: p.blankLine()
                            Surface(shape=MaterialTheme.shapes.large) {
                                Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text(line.productName,style=MaterialTheme.typography.titleMedium)
                                    if(line.productRevision!=p.revision) Text("Карточка товара изменилась. Обновите ссылки.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                    Text("Остаток: ${p.stock.replace('.',',')} ${unitLabel(p.unit)}",style=MaterialTheme.typography.bodySmall)
                                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        MposField(line.quantity,{v->model.line(p.id) {it.copy(quantity=v)}},"Количество",Modifier.width(170.dp).testTag("purchaseQty:${p.id}"),editable,numeric=true)
                                        MposSelect("Единица заказа",line.unit,units(p.unit)+PurchaseDraftRules.packs.map {it to purchaseUnitLabel(it)},
                                            {v->model.line(p.id) {it.copy(unit=v)}},editable,Modifier.width(190.dp).testTag("purchaseUnit:${p.id}"))
                                        if(line.unit in PurchaseDraftRules.packs) {
                                            MposField(line.packSize,{v->model.line(p.id) {it.copy(packSize=v)}},"В единице",Modifier.width(170.dp).testTag("purchasePack:${p.id}"),editable,numeric=true)
                                            MposSelect("Единица внутри",line.contentUnit,units(p.unit),{v->model.line(p.id) {it.copy(contentUnit=v)}},editable,
                                                Modifier.width(190.dp).testTag("purchaseContent:${p.id}"))
                                        }
                                    }
                                    Text(expectation(line),style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("purchaseExpected:${p.id}"))
                                    if(form.lines.any {it.productId==p.id}) MposButton("Убрать строку",{model.remove(p.id)},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseRemove:${p.id}"))
                                }
                            }
                        }
                        items(missing,key={"missing:${it.productId}"}) {l->
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("${l.productName}: товар недоступен у поставщика. Введено ${l.quantity} ${purchaseUnitLabel(l.unit)}")
                                MposButton("Убрать строку",{model.remove(l.productId)},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseRemove:${l.productId}"))
                            }
                        }
                        if(available.isEmpty() && missing.isEmpty()) item {Text("Закрепите простые товары за поставщиком в Настройках → Поставщики")}
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        MposButton("Сохранить черновик",model::save,enabled=!locked && !ui.blocked,modifier=Modifier.testTag("purchaseSave"))
                        MposButton("Обновить ссылки",model::revalidate,enabled=!locked && !ui.blocked,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseRevalidate"))
                        MposButton("Удалить черновик",{dialog="clear"},enabled=!locked && !ui.blocked,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseClear"))
                        MposButton("Сформировать заказ",{dialog="soon"},enabled=!locked,style=ActionStyle.Secondary)
                    }
                } else Text("Выберите поставщика. Остатки при сохранении черновика не меняются.")
            }
        }
    }
    if(dialog!=null) MposDialog(if(dialog=="soon") "Скоро будет подключено" else if(dialog=="clear") "Удалить черновик?" else "Закрыть без сохранения?",{if(!locked) dialog=null},!locked) {
        when(dialog) {
            "soon" -> Text("Формирование заказа и его история будут подключены в следующей части. Приёмка пока недоступна.")
            "clear" -> {
                Text("Сохранённый черновик и текущие изменения будут удалены. Остатки и себестоимость не изменятся.")
                MposButton("Удалить",{dialog=null;model.clear()},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseClearConfirm"))
            }
            else -> {
                Text("Несохранённые изменения будут потеряны. Сохранённый черновик останется.")
                MposButton("Продолжить редактирование",{dialog=null},style=ActionStyle.Secondary)
                MposButton("Закрыть без сохранения",{val action=dialog;dialog=null;model.discard();if(action=="back") onBack() else model.refresh()},style=ActionStyle.Danger)
            }
        }
    }
}
private fun units(base: String)=QuantityUnit.entries.filter {it.dimension==quantityUnit(base).dimension}.map {
    val code=when(it) {QuantityUnit.PIECE->"piece";QuantityUnit.KG->"kg";QuantityUnit.G->"g";QuantityUnit.L->"l";QuantityUnit.ML->"ml"};code to unitLabel(code)
}
private fun expectation(line: PurchaseLine): String=try {
    val qty=PurchaseDraftRules.number(line.quantity)
    val expected=PurchaseDraftRules.expected(line)
    when {
        qty==null -> "Укажите количество"
        qty.signum()==0 -> "Не включён в заказ"
        expected==null -> "Количество внутри уточняется при приёмке"
        else -> "На склад поступит: ${CatalogRules.canonical(expected).replace('.',',')} ${unitLabel(line.stockUnit)}"
    }
} catch(e:CatalogFailure) {e.message ?: "Проверьте строку"}
