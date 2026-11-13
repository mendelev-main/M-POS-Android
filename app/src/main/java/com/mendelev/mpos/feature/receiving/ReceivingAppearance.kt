package com.mendelev.mpos.feature.receiving

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.unitLabel
import com.mendelev.mpos.domain.warehouse.*
import com.mendelev.mpos.feature.purchasing.*
import java.math.BigDecimal

internal fun receivingMoney(value: Long)=BigDecimal.valueOf(value,2).toPlainString()+" BYN"
internal fun receivingDiscrepancy(record: PostedReceiving)=record.draft.order!=null && ReceivingAmountDefaults.shortage(record)

@Composable internal fun ReceivingHome(orders: List<PurchaseOrder>,records: List<ReceivingRecord>,history: List<PostedReceiving>,expanded: Boolean,
    historyOpen: Boolean,busy: Boolean,onHistory: ()->Unit,onNew: ()->Unit,onOrder: (String)->Unit,onRecord: (String)->Unit,
    notice: @Composable ()->Unit,form: @Composable ()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=16.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.widthIn(max=1200.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MposSupplyAction("История",onHistory,Modifier.fillMaxWidth().testTag("receivingHistory"),!busy,PosControl.Outline,height=54.dp,radius=14.dp,icon="history")
            if(historyOpen) Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp).testTag("receivingHistoryList")) {
                    if(history.isEmpty()) item {Text("Приёмок пока нет.",Modifier.padding(16.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    items(history.sortedByDescending {it.at},key={it.id}) {r->
                        Surface(onClick={onRecord(r.id)},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("receivingPosted:${r.id}")) {
                            Row(Modifier.heightIn(min=88.dp).padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                    Text(r.draft.supplierName,fontSize=15.sp,fontWeight=FontWeight.Bold)
                                    Text("${purchaseDate(r.at)}${r.draft.invoiceNumber.takeIf {it.isNotBlank()}?.let {" · Номер $it"}.orEmpty()} · ${receivingMoney(r.result.totalMinor)}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ReceivingBadge(if(receivingDiscrepancy(r)) "Недовоз" else "Принят",receivingDiscrepancy(r))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            PurchaseAction("Новая приёмка",onNew,Modifier.fillMaxWidth().testTag("receivingNew"),!busy)
            notice()
            if(expanded) form()
            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    val pending=orders.filter {it.status=="pending"}.sortedByDescending {it.createdAt}
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("К приёмке",fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
                        ReceivingBadge(pending.size.toString())
                    }
                    if(pending.isEmpty()) Text("Нет ожидающих заказов.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {val columns=1 // Web supply-pending-grid stays single-column.
                        Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                            pending.chunked(columns).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                row.forEach {o->Surface(Modifier.weight(1f),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Text(o.source.supplierName,fontSize=15.sp,fontWeight=FontWeight.Bold)
                                        Text("${purchaseDate(o.createdAt)} · ${o.source.lines.size} позиций",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Surface(color=MaterialTheme.colorScheme.surface) {
                                            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                                o.source.lines.take(5).forEach {l->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                                    Text(l.productName,Modifier.weight(1f),fontSize=13.sp)
                                                    Text(PurchaseOrderRules.requested(l),fontSize=13.sp,fontWeight=FontWeight.Bold)
                                                }}
                                            }
                                        }
                                        MposSupplyAction(if(records.any {it.orderId==o.id && it.draft!=null}) "Продолжить приёмку" else "Принять",{onOrder(o.id)},Modifier.fillMaxWidth().testTag("receivingOrder:${o.id}"),!busy,if(records.any {it.orderId==o.id && it.draft!=null}) PosControl.Receiving else PosControl.Cash,height=48.dp,radius=16.dp)
                                    }
                                }}
                                repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                            }}
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun ReceivingBadge(label: String,error: Boolean=false) {
    Surface(shape=RoundedCornerShape(9.dp),color=if(error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,contentColor=if(error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
        Text(label,Modifier.padding(horizontal=10.dp,vertical=8.dp),fontSize=11.sp,fontWeight=FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ReceivingDocument(d: ReceivingDraft,suppliers: List<ReceivingSupplier>,enabled: Boolean,onExit: ()->Unit,onNext: ()->Unit,
    onSupplier: (String)->Unit,onMeta: (ReceivingDraft)->Unit,onLine: (String,(ReceivingLine)->ReceivingLine)->Unit,onUnit: (String,String)->Unit,
    onMode: (String,String)->Unit,onAdd: ()->Unit,onRemove: (String)->Unit,navigationEnabled: Boolean=enabled,
    suggested: Set<String> = emptySet(),onReviewAmount: (String)->Unit={},onAmount: (String,String)->Unit={id,value->onLine(id) {it.copy(total=value,priceMode="total",unitPrice="")}}) {
    var calendar by remember(d.id) {mutableStateOf(false)}
    if(calendar) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=runCatching {java.time.LocalDate.parse(d.invoiceDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()}.getOrNull())
        MposDialog("Дата накладной",{calendar=false},maxWidth=480.dp) {
            DatePicker(picker,showModeToggle=false)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton("Отмена",{calendar=false},Modifier.weight(1f))
                MposButton("Выбрать",{picker.selectedDateMillis?.let {millis->
                    onMeta(d.copy(invoiceDate=java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()))
                    calendar=false
                }},Modifier.weight(1f))
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(d.supplierName,fontSize=17.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top=18.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Bottom) {
            MposSupplyField("Номер накладной",d.invoiceNumber,{onMeta(d.copy(invoiceNumber=it))},Modifier.weight(1f),enabled,numeric=false,tag="receivingInvoice",placeholder="Например, ТН-1043",error=d.invoiceNumber.isBlank())
            MposSupplyField("Дата",d.invoiceDate,{onMeta(d.copy(invoiceDate=it))},Modifier.weight(1f),enabled,numeric=false,tag="receivingDate",placeholder="ГГГГ-ММ-ДД",error=d.invoiceDate.isBlank() || runCatching {java.time.LocalDate.parse(d.invoiceDate)}.isFailure,
                onOpen={calendar=true})
        }
        if(d.order==null) PurchaseSupplierSelect("Поставщик",d.supplier?.id.orEmpty(),listOf("" to "Без поставщика")+suppliers.map {it.id to it.name},onSupplier,enabled)
        Column(Modifier.fillMaxWidth()) {
            d.lines.forEach {l->key(l.product.id) {ReceivingInvoiceRow(l,enabled,onLine,onUnit,d.order==null,onRemove,l.product.id in suggested,onAmount,onReviewAmount)}}
            if(d.order==null) PurchaseAction("Добавить товар",onAdd,enabled=enabled,style=ActionStyle.Outline)
        }
        val total=runCatching {receivingMoney(d.lines.fold(0L) {sum,l->Math.addExact(sum,ReceivingRules.lineTotal(l) ?: 0L)})}.getOrDefault("—")
        Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Позиций: ${d.lines.count {runCatching {PurchaseDraftRules.number(it.quantity)?.signum()==1}.getOrDefault(false)}} из ${d.lines.size}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {Text("Итого",fontSize=14.sp);Text(total,fontSize=22.sp,fontWeight=FontWeight.Bold)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            MposSupplyAction("Продолжить позже",onExit,enabled=navigationEnabled,kind=PosControl.Receiving,height=44.dp,radius=16.dp)
            MposSupplyAction("Принять поставку",onNext,enabled=enabled && d.lines.isNotEmpty(),kind=PosControl.Cash,height=44.dp,radius=16.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ReceivingInvoiceRow(l: ReceivingLine,enabled: Boolean,onLine: (String,(ReceivingLine)->ReceivingLine)->Unit,
    onUnit: (String,String)->Unit,removable: Boolean,onRemove: (String)->Unit,suggested: Boolean,onAmount: (String,String)->Unit,onReviewAmount: (String)->Unit) {
    val p=l.product;val packed=l.unit in PurchaseDraftRules.packs
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        val wide=maxWidth>=820.dp
        val heading: @Composable (Modifier)->Unit={modifier->Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(p.name,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text((ReceivingAmountDefaults.unitPrice(l) ?: "—")+" BYN / "+unitLabel(p.unit),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        val fields: @Composable ()->Unit={Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
            MposSupplyField("Принято",l.quantity,{v->onLine(p.id) {it.copy(quantity=v)}},Modifier.weight(1f),enabled,tag="receivingQty:${p.id}")
            PurchaseSelect("Единица",l.unit,units(p.unit)+PurchaseDraftRules.packs.map {it to purchaseUnitLabel(it)},{onUnit(p.id,it)},enabled,Modifier.weight(1f))
            if(packed) {
                MposSupplyField("В единице",l.packSize,{v->onLine(p.id) {it.copy(packSize=v)}},Modifier.weight(1f),enabled)
                PurchaseSelect("Единица внутри",l.contentUnit,units(p.unit),{v->onLine(p.id) {it.copy(contentUnit=v)}},enabled,Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
            MposSupplyField("Сумма строки, BYN",l.total,{onAmount(p.id,it)},Modifier.weight(1f),enabled,
                suggested=suggested,onTouch={onReviewAmount(p.id)},tag="receivingTotal:${p.id}")
            if(removable) MposSupplyAction("",{onRemove(p.id)},Modifier.size(44.dp),enabled,PosControl.Delete,height=44.dp,radius=12.dp,icon="close")
        }}
        if(wide) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            heading(Modifier.weight(1f));Box(Modifier.weight(3f)) {fields()}
        } else Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {heading(Modifier.fillMaxWidth());fields()}
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outline)
}
