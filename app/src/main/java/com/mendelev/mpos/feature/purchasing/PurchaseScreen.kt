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
    var expanded by rememberSaveable {mutableStateOf(false)}
    var history by rememberSaveable {mutableStateOf(false)}
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(model) {model.refresh()}
    LaunchedEffect(ui.lastPlacedId) {ui.lastPlacedId?.let {dialog="order:$it"}}
    var restored by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(snapshot!=null) {if(!restored && snapshot!=null) {if(form!=null) expanded=true;restored=true}}
    fun back() {if(!locked) onBack()}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Заказы поставщикам",navigationEnabled=!locked && dialog==null && ui.editing==null) {}
        PurchasePanels(snapshot?.orders.orEmpty(),history,expanded,locked,!ui.blocked && ui.canEdit,ui.canEdit,
            {history=!history},{expanded=!expanded},{dialog="order:${it.id}"},{model.openEdit(it)},
            notice={
                if(ui.blocked) PurchaseAction("Перечитать",model::refresh,Modifier.testTag("purchaseReload"),!locked,ActionStyle.Secondary)
                ui.message?.let {Text(it,fontSize=13.sp,color=if(ui.blocked || ui.unresolved!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)}
                ui.autoError?.let {Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error);PurchaseAction("Повторить сохранение",model::retryAutosave,enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("purchaseAutosaveRetry"))}
                if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(ui.orderUnresolved!=null && ui.editing==null) PurchaseAction("Проверить результат",model::retryOrder,enabled=!ui.pending,modifier=Modifier.testTag("purchaseOrderRetry"))
                if(ui.unresolved!=null) PurchaseAction("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("purchaseRetry"))
            }) {
            if(snapshot!=null) {
                PurchaseSupplierSelect("Поставщик",form?.supplierId ?: "",listOf("" to "Выберите поставщика")+snapshot.suppliers.map {it.id to it.name},model::supplier,
                    enabled=!locked && !ui.blocked && ui.canEdit && (form==null || !PurchaseDraftRules.hasRequestedLines(form)),modifier=Modifier.testTag("purchaseSupplier"))
                if(form!=null) {
                    val supplier=snapshot.suppliers.find {it.id==form.supplierId}
                    val available=snapshot.products.filter {it.id in supplier?.productIds.orEmpty()}
                    val missing=form.lines.filter {l->available.none {it.id==l.productId}}
                    Column(Modifier.fillMaxWidth().testTag("purchaseLines")) {
                        available.sortedBy {it.name.lowercase()}.forEach {p->
                            val line=form.lines.find {it.productId==p.id} ?: p.blankLine()
                            PurchaseRequestRow(p,line,editable) {next->if(line.quantity.isNotBlank() && next.quantity.isBlank()) model.remove(p.id) else model.line(p.id) {next}}
                        }
                        missing.forEach {l->
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("${l.productName}: товар недоступен у поставщика. Введено ${l.quantity} ${purchaseUnitLabel(l.unit)}")
                                PurchaseAction("Убрать строку",{model.remove(l.productId)},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseRemove:${l.productId}"))
                            }
                        }
                        if(available.isEmpty() && missing.isEmpty()) Text("Закрепите простые товары за поставщиком в Настройках → Поставщики")
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                    PurchaseAction("Сформировать заказ",model::saveAndPlace,Modifier.widthIn(min=230.dp).testTag("purchasePlace"),editable && form?.lines.orEmpty().any {runCatching {PurchaseDraftRules.number(it.quantity)?.signum()==1}.getOrDefault(false)})

                }

            }
        }

    }
    val order=if(ui.orderReloadNeeded) null else dialog?.takeIf {it.startsWith("order:")}?.substringAfter("order:")?.let {id->snapshot?.orders?.find {it.id==id}}
    val orderDialog=dialog?.startsWith("order:")==true
    if(orderDialog && order!=null) PurchaseOrderWindow(order,editable,locked,
        onClose={dialog=null;model.dismissPlaced()},
        onEdit={dialog=null;model.openEdit(order)},
        onCopy={clipboard.setText(AnnotatedString(purchaseOrderViewText(order)))},
        onShare={
            val intent=android.content.Intent(android.content.Intent.ACTION_SEND).apply {type="text/plain";putExtra(android.content.Intent.EXTRA_SUBJECT,"Заказ поставщику — ${order.source.supplierName}");putExtra(android.content.Intent.EXTRA_TEXT,purchaseOrderViewText(order))}
            context.startActivity(android.content.Intent.createChooser(intent,"Поделиться заказом"))
        },
        onReceive={dialog=null;model.dismissPlaced();onReceive(order.id)},
        onCancel={model.dismissPlaced();dialog="cancel:${order.id}"},canReceive=true,canCancel=administration.active)
    else if(dialog!=null && (dialog?.startsWith("cancel:")!=true || administration.active)) MposDialog(when {orderDialog->"Заказ поставщику";dialog?.startsWith("cancel:")==true->"Отменить заказ?";dialog=="place"->"Сформировать заказ?";dialog=="soon"->"Скоро будет подключено";dialog=="clear"->"Удалить черновик?";else->"Закрыть без сохранения?"},
        {if(!locked) {dialog=null;model.dismissPlaced()}},!locked,wide=orderDialog) {
        when {
            orderDialog -> {
                Text(ui.message ?: "Перечитайте историю, чтобы открыть карточку заказа")
                PurchaseAction("Перечитать историю",model::refreshOrders,enabled=!locked,modifier=Modifier.testTag("purchaseOrderReload"))
            }
            dialog?.startsWith("cancel:")==true -> {
                Text("Заказ останется в истории со статусом «Отменён». Остатки и себестоимость не изменятся; отмена не отправляет сообщение поставщику.")
                val target=snapshot?.orders?.find {it.id==dialog?.substringAfter("cancel:")}
                PurchaseAction("Отменить заказ",{if(target!=null) {dialog=null;model.cancelOrder(target)}},enabled=editable && target!=null,style=ActionStyle.Danger,modifier=Modifier.testTag("purchaseCancelConfirm"))
            }

        }
    }
    PurchaseOrderEditDialog(ui,model) {dialog="order:$it"}
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
