package com.mendelev.mpos.feature.purchasing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun PurchaseScreen(model: PurchaseViewModel,onReceive: (String)->Unit,onBack: ()->Unit) {
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle();val form=ui.form;val snapshot=ui.snapshot
    val locked=ui.pending || ui.unresolved!=null || ui.orderUnresolved!=null
    val editable=!locked && !ui.blocked && ui.canEdit
    var expanded by rememberSaveable(administration.generation) {mutableStateOf(false)}
    var draftMenu by remember {mutableStateOf(false)}
    var history by rememberSaveable {mutableStateOf(false)}
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var dialog by rememberSaveable(administration.generation) {mutableStateOf<String?>(null)}
    LaunchedEffect(model,administration.generation) {model.refresh()}
    LaunchedEffect(ui.lastPlacedId) {ui.lastPlacedId?.let {dialog="order:$it";history=true}}
    LaunchedEffect(snapshot?.draft?.id) {if(snapshot?.draft!=null) expanded=true}
    fun back() {if(!locked) {if(ui.dirty) dialog="back" else onBack()}}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Заказы поставщикам",navigationEnabled=!locked && !ui.dirty && dialog==null && ui.editing==null) {}
        Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                PurchaseAction("История",{history=!history},Modifier.fillMaxWidth().testTag("purchaseHistory"),!locked,ActionStyle.Outline)
                PurchaseAction("Новый заказ",{expanded=!expanded},Modifier.fillMaxWidth().testTag("purchaseNew"),!locked && !ui.blocked && ui.canEdit)
            }
            if(ui.blocked) PurchaseAction("Перечитать",model::refresh,Modifier.testTag("purchaseReload"),!locked,ActionStyle.Secondary)
            if(ui.message!=null) MposNotice(ui.message!!,ui.blocked || ui.unresolved!=null)
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.orderUnresolved!=null && ui.editing==null) PurchaseAction("Проверить результат",model::retryOrder,enabled=!ui.pending,modifier=Modifier.testTag("purchaseOrderRetry"))
            if(ui.unresolved!=null) PurchaseAction("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("purchaseRetry"))
            if(history && snapshot!=null) {
                LazyColumn(Modifier.weight(1f).testTag("purchaseHistoryList"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(snapshot.orders.isEmpty()) item {Text("Заказов пока нет")}
                    items(snapshot.orders,key={it.id}) {o->
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Surface(onClick={dialog="order:${o.id}"},enabled=!locked,modifier=Modifier.weight(1f).testTag("purchaseOrder:${o.id}"),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().heightIn(min=88.dp).padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(o.source.supplierName,fontSize=15.sp,fontWeight=FontWeight.Bold)
                                        Text("${purchaseDate(o.createdAt)} · ${o.source.lines.size} позиций",Modifier.padding(top=7.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(100.dp),color=if(o.status=="pending") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer) {Text(purchaseOrderStatus(o),Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=12.sp)}
                                }
                            }
                            if(o.status=="pending" && ui.canEdit) MposPosControl("",{model.openEdit(o)},Modifier.size(46.dp).testTag("purchaseHistoryEdit:${o.id}"),!locked,PosControl.Outline,height=46.dp,radius=13.dp,icon="edit")
                        }
                    }
                }
            }
            if(expanded && snapshot!=null) {
                MposSelect("Поставщик",form?.supplierId ?: "",(if(form==null) listOf("" to "Выберите поставщика") else emptyList())+snapshot.suppliers.map {it.id to it.name},model::supplier,
                    enabled=!locked && !ui.blocked && ui.canEdit && form?.lines.orEmpty().isEmpty(),modifier=Modifier.testTag("purchaseSupplier"))
                if(form!=null) {
                    val supplier=snapshot.suppliers.find {it.id==form.supplierId}
                    val available=snapshot.products.filter {it.id in supplier?.productIds.orEmpty()}
                    val missing=form.lines.filter {l->available.none {it.id==l.productId}}
                    if(supplier?.revision!=form.supplierRevision) Text("Поставщик изменился. Обновите ссылки; ввод останется прежним.",color=MaterialTheme.colorScheme.error)
                    LazyColumn(Modifier.weight(1f).testTag("purchaseLines"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        items(available,key={it.id}) {p->
                            val line=form.lines.find {it.productId==p.id} ?: p.blankLine()
                            PurchaseRequestRow(p,line,editable) {next->if(line.quantity.isNotBlank() && next.quantity.isBlank()) model.remove(p.id) else model.line(p.id) {next}}
                        }
                        items(missing,key={"missing:${it.productId}"}) {l->
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("${l.productName}: товар недоступен у поставщика. Введено ${l.quantity} ${purchaseUnitLabel(l.unit)}")
                                PurchaseAction("Убрать строку",{model.remove(l.productId)},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseRemove:${l.productId}"))
                            }
                        }
                        if(available.isEmpty() && missing.isEmpty()) item {Text("Закрепите простые товары за поставщиком в Настройках → Поставщики")}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PurchaseAction("Сформировать заказ",model::saveAndPlace,Modifier.weight(1f).testTag("purchasePlace"),editable && form.lines.isNotEmpty())
                        Box {
                            PurchaseAction("⋯",{draftMenu=true},Modifier.width(54.dp),editable,ActionStyle.Outline)
                            DropdownMenu(draftMenu,{draftMenu=false}) {
                                DropdownMenuItem(text={Text("Сохранить черновик")},onClick={draftMenu=false;model.save()},modifier=Modifier.testTag("purchaseSave"))
                                DropdownMenuItem(text={Text("Обновить ссылки")},onClick={draftMenu=false;model.revalidate()},modifier=Modifier.testTag("purchaseRevalidate"))
                                DropdownMenuItem(text={Text("Удалить черновик")},onClick={draftMenu=false;dialog="clear"},modifier=Modifier.testTag("purchaseClear"))
                            }
                        }
                    }
                } else Text("Выберите поставщика.")
            }
        }
    }
    val order=if(ui.orderReloadNeeded) null else dialog?.takeIf {it.startsWith("order:")}?.substringAfter("order:")?.let {id->snapshot?.orders?.find {it.id==id}}
    val orderDialog=dialog?.startsWith("order:")==true
    if(dialog!=null && (administration.active || orderDialog)) MposDialog(when {orderDialog->"Заказ поставщику";dialog?.startsWith("cancel:")==true->"Отменить заказ?";dialog=="place"->"Сформировать заказ?";dialog=="soon"->"Скоро будет подключено";dialog=="clear"->"Удалить черновик?";else->"Закрыть без сохранения?"},
        {if(!locked) {dialog=null;model.dismissPlaced()}},!locked,wide=orderDialog) {
        when {
            orderDialog -> if(order==null) {
                Text(ui.message ?: "Перечитайте историю, чтобы открыть карточку заказа")
                PurchaseAction("Перечитать историю",model::refreshOrders,enabled=!locked,modifier=Modifier.testTag("purchaseOrderReload"))
            } else {
                Text(order.source.supplierName,style=MaterialTheme.typography.titleMedium)
                Text("${purchaseDate(order.createdAt)} · ${purchaseOrderStatus(order)}",modifier=Modifier.testTag("purchaseOrderStatus"))
                order.importedOrigin?.let {Text("Из Web: исходная версия ${it.requestVersion ?: "не сохранена"}. Версии native считаются отдельно",style=MaterialTheme.typography.bodySmall)}
                Column(Modifier.weight(1f,fill=false).heightIn(max=450.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SelectionContainer {Text(PurchaseOrderRules.text(order),modifier=Modifier.testTag("purchaseOrderText"))}
                    order.source.lines.forEachIndexed {i,l->Text("${l.productName}: ожидается ${order.expectedQuantities[i]?.replace('.',',')?.let {"$it ${unitLabel(l.stockUnit)}"} ?: "уточнить при приёмке"}")}
                    if(order.cancelledAt!=null) Text("Отменён ${purchaseDate(order.cancelledAt)}. Заказ сохранён в истории",modifier=Modifier.testTag("purchaseCancelled"))
                    order.history.asReversed().forEach {h->
                        Text("Предыдущая версия ${h.requestVersion} · ${purchaseDate(h.at)}",style=MaterialTheme.typography.titleSmall)
                        h.source.lines.forEach {Text("${it.productName} — ${PurchaseOrderRules.requested(it)}")}
                    }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    PurchaseAction("Поделиться",{
                        val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply {type="text/plain";putExtra(android.content.Intent.EXTRA_SUBJECT,"Заказ поставщику — ${order.source.supplierName}");putExtra(android.content.Intent.EXTRA_TEXT,PurchaseOrderRules.text(order))}
                        context.startActivity(android.content.Intent.createChooser(intent,"Поделиться заказом"))
                    },style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseShare"))
                    PurchaseAction("Готово",{dialog=null;model.dismissPlaced()},style=ActionStyle.Primary,modifier=Modifier.testTag("purchaseDone"))
                    PurchaseAction("Скопировать текст",{clipboard.setText(AnnotatedString(PurchaseOrderRules.text(order)))},modifier=Modifier.testTag("purchaseCopy"))
                    PurchaseAction("Приёмка",{dialog=null;model.dismissPlaced();onReceive(order.id)},enabled=editable && !ui.dirty && order.status=="pending",style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseReceiving"))
                    if(order.status=="pending") {
                        PurchaseAction("Изменить заказ",{dialog=null;model.openEdit(order)},enabled=editable,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseEditOpen"))
                        PurchaseAction("Отменить заказ",{model.dismissPlaced();dialog="cancel:${order.id}"},enabled=editable,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseCancelOpen"))
                    }
                }
            }
            dialog?.startsWith("cancel:")==true -> {
                Text("Заказ останется в истории со статусом «Отменён». Остатки и себестоимость не изменятся; отмена не отправляет сообщение поставщику.")
                val target=snapshot?.orders?.find {it.id==dialog?.substringAfter("cancel:")}
                PurchaseAction("Отменить заказ",{if(target!=null) {dialog=null;model.cancelOrder(target)}},enabled=editable && target!=null,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseCancelConfirm"))
            }
            dialog=="soon" -> Text("Приёмка будет подключена в следующей части. Отправка поставщику пока не выполняется.")
            dialog=="place" -> {
                Text("Будет создан заказ со статусом «Ожидается приёмка». Сохранённый черновик очистится, единицы закупки запомнятся в карточках товаров. Остатки и себестоимость не изменятся.")
                PurchaseAction("Сформировать",{dialog=null;model.place()},enabled=editable,modifier=Modifier.testTag("purchasePlaceConfirm"))
            }
            dialog=="clear" -> {
                Text("Сохранённый черновик и текущие изменения будут удалены. Остатки и себестоимость не изменятся.")
                PurchaseAction("Удалить",{dialog=null;model.clear()},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseClearConfirm"))
            }
            else -> {
                Text("Несохранённые изменения будут потеряны. Сохранённый черновик останется.")
                PurchaseAction("Продолжить редактирование",{dialog=null},style=ActionStyle.Secondary)
                PurchaseAction("Закрыть без сохранения",{val action=dialog;dialog=null;model.discard();if(action=="back") onBack() else model.refresh()},style=ActionStyle.Danger)
            }
        }
    }
    PurchaseOrderEditDialog(ui,model)
}
internal fun purchaseDate(timestamp: Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

internal fun units(base: String)=QuantityUnit.entries.filter {it.dimension==quantityUnit(base).dimension}.map {
    val code=when(it) {QuantityUnit.PIECE->"piece";QuantityUnit.KG->"kg";QuantityUnit.G->"g";QuantityUnit.L->"l";QuantityUnit.ML->"ml"};code to unitLabel(code)
}
internal fun expectation(line: PurchaseLine): String=try {
    val qty=PurchaseDraftRules.number(line.quantity)
    val expected=PurchaseDraftRules.expected(line)
    when {
        qty==null -> "Укажите количество"
        qty.signum()==0 -> "Не включён в заказ"
        expected==null -> "Количество внутри уточняется при приёмке"
        else -> "На склад поступит: ${CatalogRules.canonical(expected).replace('.',',')} ${unitLabel(line.stockUnit)}"
    }
} catch(e:CatalogFailure) {e.message ?: "Проверьте строку"}
