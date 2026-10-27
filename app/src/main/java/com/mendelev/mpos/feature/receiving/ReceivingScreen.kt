package com.mendelev.mpos.feature.receiving

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.catalog.unitLabel
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.purchaseDate
import com.mendelev.mpos.feature.purchasing.units
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReceivingScreen(model: ReceivingViewModel,posting: ReceivingPostingViewModel,requestedOrder: String?,consumeRequest: ()->Unit,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle();val d=ui.form;val snapshot=ui.snapshot
    // Capture nullable content before Dialog creates a separate composition.
    val preview=ui.preview
    val post by posting.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null || post.pending || post.unresolved!=null
    var showHistory by rememberSaveable {mutableStateOf(false)}
    var detail by rememberSaveable {mutableStateOf<String?>(null)}
    var overage by rememberSaveable {mutableStateOf(false)}
    var search by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(post.committed,post.pending) {
        if(post.committed!=null && !post.pending) {model.dismissPreview();model.refresh();showHistory=true;detail=post.committed}
    }
    val order=snapshot?.orders?.find {it.id==d?.order?.id}
    val editable=!locked && !ui.blocked && (d?.order==null || order?.status=="pending")
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    var switchOrder by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(model) {model.refresh();posting.refresh()}
    LaunchedEffect(requestedOrder,ui.pending,ui.snapshot,locked,ui.blocked) {
        if(requestedOrder!=null && snapshot!=null && !locked && !ui.blocked) {
            if(ui.dirty) {switchOrder=requestedOrder;dialog="switch"} else {posting.acknowledge();showHistory=false;model.open(requestedOrder)}
            consumeRequest()
        }
    }
    fun back() {if(!locked) {if(showHistory) {showHistory=false;detail=null;posting.acknowledge()} else if(ui.dirty) dialog="back" else {model.close();onBack()}}}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}.testTag("receivingScreen")) {
        MposAppBar("Приёмка",navigationEnabled=!locked && !ui.dirty && dialog==null && ui.preview==null) {}
        Column(Modifier.weight(1f).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                MposButton("Назад",::back,enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingBack"))
                MposButton("Перечитать",{if(ui.dirty) dialog="reload" else {model.refresh();if(showHistory) posting.refresh()}},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingReload"))
                if(d!=null) MposButton("К заказам приёмки",{if(ui.dirty) dialog="close" else model.close()},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingClose"))
                MposButton("Приёмка без заказа",{if(ui.dirty) dialog="standalone" else {posting.acknowledge();showHistory=false;model.standalone()}},enabled=!locked && !ui.blocked,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingNew"))
                MposButton("История приходов",{showHistory=true;detail=null;posting.refresh()},enabled=!locked && !ui.dirty,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingHistory"))
            }
            if(post.message!=null) MposNotice(post.message!!,post.unresolved!=null)
            if(post.unresolved!=null) MposButton("Проверить проведение",posting::retry,enabled=!post.pending,modifier=Modifier.testTag("receivingPostRetry"))
            if(ui.message!=null) MposNotice(ui.message!!,ui.blocked || ui.unresolved!=null)
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("receivingRetry"))
            if(showHistory) {
                Text("История приходов",style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    val selected=post.history.find {it.id==detail}
                    if(selected==null) {
                        if(post.history.isEmpty()) Text("Нет проведённых приходов")
                        post.history.forEach {r->MposButton("${r.draft.supplierName} · ${r.draft.invoiceNumber} · ${purchaseDate(r.at)}",{detail=r.id},style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingPosted:${r.id}"))}
                    } else {
                        Text("Приход проведён · ${selected.draft.invoiceNumber}",modifier=Modifier.testTag("receivingPostedTitle"))
                        Text("${selected.draft.supplierName} · ${purchaseDate(selected.at)} · ${if(selected.draft.order==null) "Без заказа" else "заказ закрыт"}")
                        val result=selected.result
                        Text("Итого: ${BigDecimal.valueOf(result.totalMinor,2).toPlainString()} BYN")
                        result.lines.forEach {l->Text("${l.name}: получено ${l.actual}; остаток ${l.oldStock} → ${l.newStock}; себестоимость ${l.oldCost} → ${l.newCost} BYN/ед."+if(selected.draft.order==null) "" else " Недопоставка: ${l.shortage ?: "неизвестно"}; превышение: ${l.overage ?: "неизвестно"}") }
                        Text("Накладная: ${selected.draft.invoiceDate} · Автор: ${selected.actorId}")
                        MposButton("К списку приходов",{detail=null},style=ActionStyle.Secondary)
                    }
                }
            } else if(d==null) {
                Text("К приёмке",style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    val orders=snapshot?.orders.orEmpty().filter {o->o.status=="pending" || snapshot?.records?.any {it.orderId==o.id && it.draft!=null}==true}
                    if(orders.isEmpty()) Text("Нет ожидающих заказов")
                    orders.forEach {o->val saved=snapshot?.records?.any {it.orderId==o.id && it.draft!=null}==true
                        MposButton("${o.source.supplierName} · ${purchaseDate(o.createdAt)} · ${if(o.status!="pending") "Отменён · сохранённый черновик" else if(saved) "Продолжить приёмку" else "Начать приёмку"}",
                            {posting.acknowledge();model.open(o.id)},enabled=!locked && !ui.blocked,style=ActionStyle.Secondary,modifier=Modifier.fillMaxWidth().testTag("receivingOrder:${o.id}"))
                    }
                }
            } else {
                Text("Приёмка: ${d.supplierName}",style=MaterialTheme.typography.titleLarge)
                Text("Черновик. Не поставлено — укажите 0. Склад и себестоимость пока не меняются.")
                if(d.order!=null && order?.status!="pending") MposNotice("Заказ уже не ожидает приёмку. Факты сохранены для просмотра; черновик можно удалить",true)
                else if(d.order!=null && order?.revision!=d.order.revision) MposNotice("Заявка изменилась. Обновите основание, сохранив фактические количества и суммы",true)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("receivingForm"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(d.order==null) {
                        MposSelect("Поставщик",d.supplier?.id ?: "",listOf("" to "Без поставщика")+snapshot?.suppliers.orEmpty().map {it.id to it.name},model::supplier,editable,Modifier.width(320.dp).testTag("receivingSupplier"))
                        MposButton("Добавить товар",{search="";dialog="product"},enabled=editable,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingAdd"))
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        MposField(d.invoiceNumber,{v->model.change {it.copy(invoiceNumber=v)}},"Номер накладной",Modifier.width(320.dp).testTag("receivingInvoice"),editable)
                        MposField(d.invoiceDate,{v->model.change {it.copy(invoiceDate=v)}},"Дата ГГГГ-ММ-ДД",Modifier.width(240.dp).testTag("receivingDate"),editable)
                    }
                    d.lines.forEach {l->val p=l.product
                        Surface(shape=MaterialTheme.shapes.large) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(p.name,style=MaterialTheme.typography.titleMedium)
                                if(d.order==null) MposButton("Убрать строку",{model.remove(p.id)},enabled=editable,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingRemove:${p.id}"))
                                Text("Основание расчёта: остаток ${p.stock} ${unitLabel(p.unit)}, себестоимость ${p.cost} BYN/${unitLabel(p.unit)}")
                                if(d.order!=null && d.order.source.lines.none {it.productId==p.id}) Text("Строка убрана из заявки; ранее введённые факты сохранены")
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    MposField(l.quantity,{v->model.line(p.id) {it.copy(quantity=v)}},"Получено",Modifier.width(160.dp).testTag("receivingQty:${p.id}"),editable,numeric=true)
                                    MposSelect("Единица накладной",l.unit,units(p.unit)+PurchaseDraftRules.packs.map {it to purchaseUnitLabel(it)},
                                        {v->model.unit(p.id,v)},editable,Modifier.width(190.dp).testTag("receivingUnit:${p.id}"))
                                    if(l.unit in PurchaseDraftRules.packs) {
                                        MposField(l.packSize,{v->model.line(p.id) {it.copy(packSize=v)}},"В единице",Modifier.width(160.dp).testTag("receivingPack:${p.id}"),editable,numeric=true)
                                        MposSelect("Единица внутри",l.contentUnit,units(p.unit),{v->model.line(p.id) {it.copy(contentUnit=v)}},editable,Modifier.width(190.dp).testTag("receivingContent:${p.id}"))
                                    }
                                    val zero=l.quantity.trim().replace(',','.').toBigDecimalOrNull()?.signum()==0
                                    MposField(l.total,{v->model.line(p.id) {it.copy(total=v)}},"Сумма строки BYN",Modifier.width(190.dp).testTag("receivingTotal:${p.id}"),editable && !zero,numeric=true)
                                }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposButton("Сохранить черновик",model::save,enabled=editable,modifier=Modifier.testTag("receivingSave"))
                    MposButton("Обновить основание",model::rebase,enabled=editable,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingRebase"))
                    MposButton("Предварительный расчёт",model::preview,enabled=editable,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingPreview"))
                    MposButton("Удалить черновик",{dialog="clear"},enabled=!locked && !ui.blocked,style=ActionStyle.Danger,modifier=Modifier.testTag("receivingClear"))
                }
            }
        }
    }
    if(preview!=null && dialog==null && !showHistory) {
        // Posting revalidates the saved invoice and the entire economic baseline.
        MposDialog("Предварительный расчёт",{model.dismissPreview()},!locked,wide=true) {
            Text("Итого: ${BigDecimal.valueOf(preview.totalMinor,2).toPlainString()} BYN. Это расчёт, приход ещё не проведён.",modifier=Modifier.testTag("receivingPreviewTotal"))
            Column(Modifier.weight(1f,fill=false).heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                preview.lines.forEach {l->
                    Text("${l.name}: получено ${l.actual}; остаток ${l.oldStock} → ${l.newStock}; себестоимость ${l.oldCost} → ${l.newCost} BYN/ед.",modifier=Modifier.testTag("receivingPreviewLine:${l.productId}"))
                    if(l.expected==null && d?.order!=null) Text("Заказанное количество было неизвестно; сравнение уточняется при приёмке")
                    else if(l.expected!=null) Text("Недопоставка: ${l.shortage}; превышение: ${l.overage}")
                    if(l.noStockTracking) Text("Без складского учёта: остаток не изменится, себестоимость — по поступлению")
                }
            }
            if(preview.hasOverage) MposNotice("Есть превышение заказа. Перед проведением потребуется отдельное подтверждение",true)
            Text(if(d?.order==null) "При подтверждении склад и себестоимость изменятся. Приход сохранится в истории без закупки." else "При подтверждении склад и себестоимость изменятся, заказ будет закрыт. Недопоставленные позиции не переносятся.")
            if(ui.dirty) MposNotice("Перед проведением сохраните черновик",true)
            MposButton("Провести приход",{overage=false;dialog="post"},enabled=!locked && !ui.dirty,modifier=Modifier.testTag("receivingPost"))
        }
    }
    if(dialog!=null) MposDialog(when(dialog) {"product"->"Добавить товар";"post"->"Подтвердить приход?";"soon"->"Скоро будет подключено";"clear"->"Удалить черновик?";else->"Закрыть без сохранения?"},{if(!locked) dialog=null},!locked) {
        when(dialog) {
            "product" -> {
                MposField(search,{search=it},"Поиск товара",Modifier.fillMaxWidth().testTag("receivingSearch"))
                Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    snapshot?.products.orEmpty().filter {CatalogRules.key(it.name).contains(CatalogRules.key(search)) && d?.lines?.none {l->l.product.id==it.id}==true}.take(100).forEach {p->
                        MposButton("${p.name} · ${unitLabel(p.unit)}",{model.add(p.id);dialog=null},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.fillMaxWidth().testTag("receivingPick:${p.id}"))
                    }
                    Text("Показано до 100 товаров; уточните поиск")
                }
            }
            "post" -> {
                Text(if(d?.order==null) "Фактические количества поступят на склад. Себестоимость пересчитается; закупка не создаётся." else "Фактические количества поступят на склад. Себестоимость пересчитается; заказ будет закрыт, даже если поставка неполная.")
                if(preview?.hasOverage==true) {
                    MposNotice("Получено больше, чем заказано. Подтвердите превышение отдельно",true)
                    MposButton(if(overage) "Превышение подтверждено" else "Подтверждаю превышение заказа",{overage=!overage},enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("receivingOverage"))
                }
                MposButton("Подтвердить приход",{
                    dialog=null
                    posting.post(snapshot!!.ownerId,snapshot.records.find {it.orderId==d!!.key}!!.revision,d!!,overage)
                },enabled=!locked && !ui.dirty && (preview?.hasOverage!=true || overage),modifier=Modifier.testTag("receivingPostConfirm"))
            }
            "soon" -> Text("Приёмка без заказа будет подключена следующей частью.")
            "clear" -> {
                Text("Черновик и текущий ввод будут удалены. Статус заказа, склад и себестоимость не изменятся.")
                MposButton("Удалить",{dialog=null;model.clear()},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("receivingClearConfirm"))
            }
            else -> {
                Text("Несохранённый ввод будет потерян. Сохранённый черновик останется.")
                MposButton("Продолжить редактирование",{dialog=null},style=ActionStyle.Secondary)
                MposButton("Закрыть без сохранения",{val action=dialog;dialog=null;model.discard();when(action) {"back"->{model.close();onBack()};"close"->model.close();"standalone"->{posting.acknowledge();showHistory=false;model.standalone()};"switch"->{model.open(switchOrder!!);switchOrder=null};else->model.refresh()}},style=ActionStyle.Danger,modifier=Modifier.testTag("receivingDiscard"))
            }
        }
    }
}
