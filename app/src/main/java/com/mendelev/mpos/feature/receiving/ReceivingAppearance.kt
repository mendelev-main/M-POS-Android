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
internal fun receivingDiscrepancy(record: PostedReceiving)=record.draft.order!=null && record.result.lines.any {
    if(it.expected==null) it.actual.toBigDecimal().signum()==0 else it.actual.toBigDecimal().compareTo(it.expected.toBigDecimal())!=0
}

@Composable internal fun ReceivingHome(orders: List<PurchaseOrder>,records: List<ReceivingRecord>,history: List<PostedReceiving>,expanded: Boolean,
    historyOpen: Boolean,busy: Boolean,onHistory: ()->Unit,onNew: ()->Unit,onOrder: (String)->Unit,onRecord: (String)->Unit,
    notice: @Composable ()->Unit,form: @Composable ()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=16.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.widthIn(max=1200.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("История",onHistory,Modifier.fillMaxWidth().testTag("receivingHistory"),!busy,PosControl.Outline,height=54.dp,radius=14.dp,icon="history")
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
                                        MposPosControl(if(records.any {it.orderId==o.id && it.draft!=null}) "Продолжить приёмку" else "Принять",{onOrder(o.id)},Modifier.fillMaxWidth().testTag("receivingOrder:${o.id}"),!busy,if(records.any {it.orderId==o.id && it.draft!=null}) PosControl.Receiving else PosControl.Cash,height=48.dp,radius=16.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ReceivingDocument(d: ReceivingDraft,suppliers: List<ReceivingSupplier>,enabled: Boolean,onExit: ()->Unit,onNext: ()->Unit,
    onSupplier: (String)->Unit,onMeta: (ReceivingDraft)->Unit,onLine: (String,(ReceivingLine)->ReceivingLine)->Unit,onUnit: (String,String)->Unit,
    onMode: (String,String)->Unit,onAdd: ()->Unit,onRemove: (String)->Unit,navigationEnabled: Boolean=enabled) {
    Surface((if(d.order!=null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()).testTag("receivingForm"),shape=if(d.order!=null) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(22.dp)) {
        Column {
          Column(if(d.order!=null) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            FlowRow(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if(d.order!=null) "Приёмка заказа" else "Приёмка без заказа",fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
                    Text("Не поставлено — укажите 0.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(d.order!=null) PurchaseAction("Назад",onExit,Modifier.testTag("receivingClose"),navigationEnabled,ActionStyle.Outline)
                PurchaseAction(if(d.order!=null) "Завершить приёмку позже" else "Сохранить и выйти",onExit,Modifier.testTag("receivingExit"),navigationEnabled,ActionStyle.Outline)
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.widthIn(min=220.dp).weight(1.4f)) {
                    PurchaseSupplierSelect("Поставщик",d.order?.source?.supplierId ?: d.supplier?.id.orEmpty(),listOf("" to "Не указан")+suppliers.map {it.id to it.name}+if(d.order!=null && suppliers.none {it.id==d.order.source.supplierId}) listOf(d.order.source.supplierId to d.order.source.supplierName) else emptyList(),onSupplier,enabled && d.order==null,Modifier.testTag("receivingSupplier"))
                }
                Box(Modifier.width(200.dp)) {MposWebField(d.invoiceNumber,{onMeta(d.copy(invoiceNumber=it))},"Номер","receivingInvoice",enabled,placeholder="Номер")}
                Box(Modifier.width(190.dp)) {MposWebField(d.invoiceDate,{onMeta(d.copy(invoiceDate=it))},"Дата","receivingDate",enabled,placeholder="ГГГГ-ММ-ДД")}
            }
            Column(Modifier.padding(horizontal=24.dp)) {
                d.lines.forEach {l->ReceivingInvoiceRow(d,l,enabled,onLine,onUnit,onMode,onRemove)}
            }
            if(d.order==null) PurchaseAction("Добавить товар",onAdd,Modifier.padding(horizontal=24.dp).testTag("receivingAdd"),enabled,ActionStyle.Outline)
          }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=16.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Итого",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(runCatching {receivingMoney(d.lines.fold(0L) {sum,l->Math.addExact(sum,ReceivingRules.lineTotal(l) ?: 0L)})}.getOrDefault("Проверьте суммы"),fontSize=24.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.testTag("receivingDraftTotal"))
                }
                PurchaseAction("Далее",onNext,Modifier.widthIn(min=160.dp).testTag("receivingPreview"),enabled && d.lines.isNotEmpty())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ReceivingInvoiceRow(d: ReceivingDraft,l: ReceivingLine,enabled: Boolean,onLine: (String,(ReceivingLine)->ReceivingLine)->Unit,
    onUnit: (String,String)->Unit,onMode: (String,String)->Unit,onRemove: (String)->Unit) {
    val p=l.product
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=15.dp)) {
      val wide=maxWidth>1000.dp
      val heading: @Composable ()->Unit = {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(p.name,fontSize=14.sp,fontWeight=FontWeight.Bold)
                val ordered=d.order?.source?.lines?.find {it.productId==p.id}
                Text((ordered?.let {"Заказано: "+PurchaseOrderRules.requested(it)} ?: "Дополнительная позиция")+" · Склад: ${unitLabel(p.unit)}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(d.order==null) MposPosControl("",{onRemove(p.id)},Modifier.size(40.dp).testTag("receivingRemove:${p.id}"),enabled,PosControl.Delete,height=40.dp,radius=10.dp,icon="close")
        }
      }
      val fields: @Composable ()->Unit = {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(120.dp)) {MposWebField(l.quantity,{v->onLine(p.id) {it.copy(quantity=v)}},"Принято","receivingQty:${p.id}",enabled,true)}
            PurchaseSelect("Единица",l.unit,units(p.unit)+PurchaseDraftRules.packs.map {it to purchaseUnitLabel(it)},{onUnit(p.id,it)},enabled,Modifier.width(140.dp).testTag("receivingUnit:${p.id}"))
            if(l.unit in PurchaseDraftRules.packs) {
                Box(Modifier.width(120.dp)) {MposWebField(l.packSize,{v->onLine(p.id) {it.copy(packSize=v)}},"В единице","receivingPack:${p.id}",enabled,true)}
                PurchaseSelect("Единица внутри",l.contentUnit,units(p.unit),{v->onLine(p.id) {it.copy(contentUnit=v)}},enabled,Modifier.width(140.dp).testTag("receivingContent:${p.id}"))
            }
            val zero=l.quantity.trim().replace(',','.').toBigDecimalOrNull()?.signum()==0
            if(l.priceMode=="price") Box(Modifier.width(150.dp)) {MposWebField(l.unitPrice,{v->onLine(p.id) {it.copy(unitPrice=v)}},"Цена за единицу","receivingPrice:${p.id}",enabled,true)}
            Box(Modifier.width(150.dp)) {
                val total=if(l.priceMode=="price") runCatching {ReceivingRules.lineTotal(l)?.let {BigDecimal.valueOf(it,2).toPlainString()}}.getOrNull().orEmpty() else l.total
                MposWebField(total,{v->onLine(p.id) {it.copy(total=v)}},"Сумма строки","receivingTotal:${p.id}",enabled && !zero && l.priceMode=="total",true)
            }
            PurchaseSelect("Ввод стоимости",l.priceMode,listOf("total" to "Сумма строки","price" to "Цена за единицу"),{onMode(p.id,it)},enabled,Modifier.width(160.dp).testTag("receivingMode:${p.id}"))
        }
      }
      if(wide) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1.5f)) {heading()};Box(Modifier.weight(4f)) {fields()}
      } else Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {heading();fields()}
    }
    HorizontalDivider()
}
