package com.mendelev.mpos.feature.purchasing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.warehouse.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Human-facing Web export. The immutable native audit snapshot remains unchanged. */
internal fun purchaseOrderViewText(order: PurchaseOrder,zone: ZoneId=ZoneId.systemDefault()): String=buildString {
    append("Заказ поставщику: ${order.source.supplierName}\n")
    append("Дата: ${DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").format(Instant.ofEpochMilli(order.createdAt).atZone(zone))}\n\n")
    order.source.lines.forEachIndexed {i,line->append("${i+1}. ${line.productName} — ${PurchaseOrderRules.requested(line)}\n")}
    append("\nПросьба подтвердить наличие и сроки поставки.")
}

@Composable internal fun PurchaseOrderWindow(order: PurchaseOrder,editable: Boolean,locked: Boolean,onClose: ()->Unit,onEdit: ()->Unit,
    onCopy: ()->Unit,onShare: ()->Unit,onReceive: ()->Unit,onCancel: ()->Unit,canReceive: Boolean=true,canCancel: Boolean=true) {
    var more by remember(order.id) {mutableStateOf(false)}
    MposDialog("Заказ поставщику",onClose,closable=!locked,wide=true,header={
        Text("Заказ поставщику",Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.Bold)
        if(order.status=="pending") {
            MposPosControl("",onEdit,Modifier.size(48.dp).testTag("purchaseEditOpen"),editable,PosControl.Outline,height=48.dp,radius=13.dp,icon="edit")
            Box(Modifier.padding(horizontal=8.dp)) {
                MposPosControl("⋯",{more=true},Modifier.size(44.dp).testTag("purchaseOrderMore"),!locked,PosControl.Outline,height=44.dp,radius=13.dp)
                DropdownMenu(more,{more=false}) {
                    DropdownMenuItem(text={Text("Приёмка")},enabled=editable && canReceive,onClick={more=false;onReceive()},modifier=Modifier.testTag("purchaseReceiving"))
                    if(canCancel) DropdownMenuItem(text={Text("Отменить заказ")},enabled=editable,onClick={more=false;onCancel()},modifier=Modifier.testTag("purchaseCancelOpen"))
                }
            }
        }
        MposCloseButton(onClose,!locked)
    }) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(order.source.supplierName,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text("${purchaseDate(order.createdAt)} · ${purchaseOrderStatus(order)}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("purchaseOrderStatus"))
        }
        Surface(Modifier.fillMaxWidth().weight(1f,fill=false),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.background,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                SelectionContainer {Text(purchaseOrderViewText(order),fontSize=15.sp,lineHeight=23.25.sp,modifier=Modifier.testTag("purchaseOrderText"))}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            PurchaseAction("Скопировать текст",onCopy,Modifier.weight(1f).testTag("purchaseCopy"),!locked,ActionStyle.Secondary)
            PurchaseAction("Поделиться",onShare,Modifier.weight(1f).testTag("purchaseShare"),!locked,ActionStyle.Secondary)
            PurchaseAction("Готово",onClose,Modifier.weight(1f).testTag("purchaseDone"),!locked)
        }
    }
}
