package com.mendelev.mpos.feature.purchasing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.warehouse.*

/** Web supply-stack: history expands before New; the two panels toggle independently. */
@Composable internal fun PurchasePanels(orders: List<PurchaseOrder>,history: Boolean,expanded: Boolean,locked: Boolean,canCreate: Boolean,
    canEdit: Boolean,onHistory: ()->Unit,onNew: ()->Unit,onOpen: (PurchaseOrder)->Unit,onEdit: (PurchaseOrder)->Unit,
    notice: @Composable ()->Unit,form: @Composable ColumnScope.()->Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal=24.dp,vertical=16.dp),contentAlignment=Alignment.TopCenter) {
        Column(Modifier.widthIn(max=1200.dp).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("purchaseStack"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("История",onHistory,Modifier.fillMaxWidth().testTag("purchaseHistory").semantics {stateDescription=if(history) "Развёрнуто" else "Свёрнуто"},!locked,PosControl.Outline,height=54.dp,radius=14.dp,icon="history")
            if(history) PurchaseHistoryPanel(orders,locked,canEdit,onOpen,onEdit)
            PurchaseAction("Новый заказ",onNew,Modifier.fillMaxWidth().testTag("purchaseNew").semantics {stateDescription=if(expanded) "Развёрнуто" else "Свёрнуто"},!locked && canCreate)
            notice()
            if(expanded) Surface(Modifier.fillMaxWidth().testTag("purchaseForm"),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=form)
            }
        }
    }
}

@Composable private fun PurchaseHistoryPanel(orders: List<PurchaseOrder>,locked: Boolean,canEdit: Boolean,onOpen: (PurchaseOrder)->Unit,onEdit: (PurchaseOrder)->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp).testTag("purchaseHistoryList")) {
            if(orders.isEmpty()) item {Text("Заказов пока нет.",Modifier.padding(16.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            itemsIndexed(orders.sortedWith(compareByDescending<PurchaseOrder> {it.createdAt}.thenByDescending {it.id}),key={_,o->o.id}) {index,order->
                Row(Modifier.fillMaxWidth().padding(end=12.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(onClick={onOpen(order)},enabled=!locked,modifier=Modifier.weight(1f).testTag("purchaseOrder:${order.id}")) {
                        Row(Modifier.heightIn(min=88.dp).fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                Text(order.source.supplierName,fontSize=15.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text("${purchaseDate(order.createdAt)} · ${order.source.lines.size} позиций",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PurchaseStatus(order)
                        }
                    }
                    if(order.status=="pending" && canEdit) Surface(onClick={onEdit(order)},enabled=!locked,modifier=Modifier.size(46.dp).testTag("purchaseHistoryEdit:${order.id}").semantics {contentDescription="Изменить заказ поставщику: ${order.source.supplierName}"},shape=RoundedCornerShape(13.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary),color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary) {
                        Box(contentAlignment=Alignment.Center) {MposPosIcon("edit",Modifier.size(20.dp))}
                    }
                }
                if(index<orders.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable private fun PurchaseStatus(order: PurchaseOrder) {
    val dark=isMposDarkTheme()
    val background=when(order.status) {"pending"->if(dark) 0xFF3A3022 else 0xFFFFF4E9;"received"->if(dark) 0xFF1D4638 else 0xFFE3F6E8;else->if(dark) 0xFF171A21 else 0xFFF5F4F0}
    val ink=when(order.status) {"pending"->if(dark) 0xFFE9C989 else 0xFF8A5A17;"received"->if(dark) 0xFF8FE0BE else 0xFF26733A;else->if(dark) 0xFFA9B0BF else 0xFF767C8C}
    Surface(shape=RoundedCornerShape(9.dp),color=androidx.compose.ui.graphics.Color(background),contentColor=androidx.compose.ui.graphics.Color(ink),border=BorderStroke(1.dp,androidx.compose.ui.graphics.Color(ink).copy(alpha=.3f))) {
        Text(purchaseOrderStatus(order),Modifier.widthIn(max=150.dp).padding(horizontal=10.dp,vertical=8.dp),fontSize=11.sp,fontWeight=FontWeight.Bold)
    }
}
