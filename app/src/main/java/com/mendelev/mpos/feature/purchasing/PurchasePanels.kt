package com.mendelev.mpos.feature.purchasing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.warehouse.*

private data class SupplyListEntry(val id: String,val at: Long,val order: PurchaseOrder?=null,val receipt: PostedReceiving?=null)

@Composable internal fun PurchasePanels(orders: List<PurchaseOrder>,history: Boolean,expanded: Boolean,locked: Boolean,canCreate: Boolean,
    canEdit: Boolean,onHistory: ()->Unit,onNew: ()->Unit,onOpen: (PurchaseOrder)->Unit,onEdit: (PurchaseOrder)->Unit,
    onSupplies: ()->Unit,onReceive: (String)->Unit,onCopy: (PurchaseOrder)->Unit,onCancel: (PurchaseOrder)->Unit,canCancel: Boolean,
    drafts: List<ReceivingRecord>,receipts: List<PostedReceiving>,initialOrder: String?=null,
    notice: @Composable ()->Unit,form: @Composable ColumnScope.()->Unit) {
    var expandedOrder by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(initialOrder) {if(initialOrder!=null) expandedOrder=initialOrder}
    Box(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=16.dp)) {
        MposFolderPanel(when {expanded->MposFolderTab.New;history->MposFolderTab.History;else->MposFolderTab.Supplies},
            {expandedOrder=null;when(it) {MposFolderTab.Supplies->onSupplies();MposFolderTab.New->if(canCreate) onNew();MposFolderTab.History->onHistory()}},
            Modifier.fillMaxSize(),enabled=!locked) {
            notice()
            if(expanded) Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp),content=form)
            else {
                val visible=if(history) orders else orders.filter {it.status=="pending"}
                val entries=(visible.map {SupplyListEntry("order:"+it.id,it.createdAt,order=it)}+
                    if(history) receipts.filter {it.draft.order==null}.map {SupplyListEntry("receipt:"+it.id,it.at,receipt=it)} else emptyList()).sortedWith(compareByDescending<SupplyListEntry> {it.at}.thenByDescending {it.id})
                LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(entries.isEmpty()) item {Text(if(history) "История поставок пуста" else "Нет поставок к приёмке",Modifier.padding(18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp)}
                    items(entries,key={it.id}) {entry->
                        val order=entry.order
                        if(order!=null) {
                        SupplyCard(order,drafts.find {it.orderId==order.id}?.draft,receipts.find {it.draft.order?.id==order.id},locked,canEdit,canCancel,expandedOrder==order.id,{expandedOrder=if(expandedOrder==order.id) null else order.id},
                            {onEdit(order)},{onCopy(order)},{onCancel(order)},{onReceive(order.id)})
                        } else entry.receipt?.let {receipt->StandaloneSupplyCard(receipt,locked,expandedOrder==entry.id,{expandedOrder=if(expandedOrder==entry.id) null else entry.id})}
                    }
                }
            }
        }
    }
}

@Composable private fun StandaloneSupplyCard(record: PostedReceiving,locked: Boolean,details: Boolean,onDetails: ()->Unit) {
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    var menu by remember(record.id) {mutableStateOf(false)}
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).clickable(enabled=!locked,onClick=onDetails),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(record.draft.supplierName,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text("${purchaseDate(record.at)} · ${record.draft.lines.size} поз.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SupplyBadge("Принята","success")
                }
                Box {
                    MposSupplyAction("⋯",{menu=true},Modifier.size(44.dp),!locked,PosControl.Outline,height=44.dp,radius=12.dp)
                    DropdownMenu(menu,{menu=false},shape=RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text={Text("Скопировать")},leadingIcon={MposPosIcon("copy",Modifier.size(18.dp))},onClick={
                            menu=false;clipboard.setText(androidx.compose.ui.text.AnnotatedString(buildString {
                                append(record.draft.supplierName+"\n"+purchaseDate(record.at)+"\n")
                                record.draft.lines.forEach {append(it.product.name+" — "+it.quantity+" "+purchaseUnitLabel(it.unit)+"\n")}
                                append("Итого: "+java.math.BigDecimal.valueOf(record.result.totalMinor,2).toPlainString()+" BYN")
                            }))
                        })
                    }
                }
            }
            if(details) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                record.draft.lines.forEach {line->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(line.product.name,Modifier.weight(1f),fontSize=14.sp);Text(line.quantity.replace('.',',')+" "+purchaseUnitLabel(line.unit),fontSize=13.sp)
                }}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Сумма приёмки",fontSize=14.sp);Text(java.math.BigDecimal.valueOf(record.result.totalMinor,2).toPlainString()+" BYN",fontWeight=FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun SupplyCard(order: PurchaseOrder,draft: ReceivingDraft?,receipt: PostedReceiving?,locked: Boolean,canEdit: Boolean,
    canCancel: Boolean,details: Boolean,onDetails: ()->Unit,onEdit: ()->Unit,onCopy: ()->Unit,onCancel: ()->Unit,onReceive: ()->Unit) {
    var menu by remember(order.id) {mutableStateOf(false)}
    val pending=order.status=="pending"
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.background,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f).clickable(enabled=!locked,onClick=onDetails),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(order.source.supplierName,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text("${purchaseDate(order.createdAt)} · ${order.source.lines.size} поз.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        SupplyBadge(when(order.status) {"pending"->"Ожидает поставки";"received"->"Принята";else->"Отменена"},when(order.status) {"pending"->"waiting";"received"->"success";else->"danger"})
                        if(receipt!=null && ReceivingAmountDefaults.shortage(receipt)) SupplyBadge("Недовоз","danger")
                    }
                }
                if(pending) MposSupplyAction(if(draft!=null) "Продолжить" else "Принять",onReceive,Modifier.widthIn(min=134.dp),!locked,
                    if(draft!=null) PosControl.Receiving else PosControl.Cash,height=44.dp,radius=16.dp)
                Box {
                    MposSupplyAction("⋯",{menu=true},Modifier.size(44.dp),!locked,PosControl.Outline,height=44.dp,radius=12.dp)
                    DropdownMenu(menu,{menu=false},shape=RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text={Text("Изменить")},leadingIcon={MposPosIcon("edit",Modifier.size(18.dp))},enabled=pending && canEdit && !locked,onClick={menu=false;onEdit()})
                        DropdownMenuItem(text={Text("Скопировать")},leadingIcon={MposPosIcon("copy",Modifier.size(18.dp))},enabled=!locked,onClick={menu=false;onCopy()})
                        if(canCancel) DropdownMenuItem(text={Text("Отменить заказ",color=MaterialTheme.colorScheme.error)},leadingIcon={MposPosIcon("close",Modifier.size(18.dp))},enabled=pending && canEdit && !locked,onClick={menu=false;onCancel()})
                    }
                }
            }
            if(details) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                order.source.lines.forEach {line->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(line.productName,Modifier.weight(1f),fontSize=14.sp)
                        val actual=receipt?.draft?.lines?.find {it.product.id==line.productId}
                        Text(if(actual==null) PurchaseOrderRules.requested(line) else "${actual.quantity.replace('.',',')} ${purchaseUnitLabel(actual.unit)} / ${PurchaseOrderRules.requested(line)}",fontSize=13.sp)
                    }
                }
                if(order.source.note.isNotBlank()) Text(order.source.note,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                receipt?.let {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Сумма приёмки",fontSize=14.sp);Text(java.math.BigDecimal.valueOf(it.result.totalMinor,2).toPlainString()+" BYN",fontWeight=FontWeight.Bold)
                }}
            }
        }
    }
}

@Composable internal fun SupplyBadge(label: String,tone: String) {
    val dark=isMposDarkTheme()
    val fill=when(tone) {"waiting"->if(dark) 0xFF433722 else 0xFFFFF1C1;"success"->if(dark) 0xFF203F31 else 0xFFE3F5E9;else->if(dark) 0xFF492D30 else 0xFFFCE8E6}
    val ink=when(tone) {"waiting"->if(dark) 0xFFE9C989 else 0xFF80600C;"success"->if(dark) 0xFF8FE0B5 else 0xFF236B3E;else->if(dark) 0xFFFFAAA4 else 0xFFB43D3B}
    Surface(shape=RoundedCornerShape(9.dp),color=Color(fill),contentColor=Color(ink)) {
        Row(Modifier.padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            MposPosIcon(when(tone) {"waiting"->"history";"success"->"check";else->"close"},Modifier.size(14.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}
