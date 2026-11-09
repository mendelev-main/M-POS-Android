package com.mendelev.mpos.feature.sale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import com.mendelev.mpos.core.ui.isMposDarkTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.shift.ShiftRules
import com.mendelev.mpos.domain.workspace.OrderRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun receiptDate(value: Long)=DateTimeFormatter.ofPattern("dd.MM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
internal fun receiptPaymentLabel(snapshot: ReceiptSnapshot): String = when(snapshot.payment.parts.map {it.method}.distinct()) {
    listOf("cash")->"Наличные"
    listOf("card")->"Карта"
    else->"Наличные + карта"
}

/** Pinned receipts screen: 26/22/40 gutters, .82:1.18 columns, gap16, shared history selection. */
@Composable internal fun ReceiptHistoryPage(receipts: List<ReceiptEntity>,types: Map<String,String>,methods: Map<String,String>,selectedId: String,
    detail: Pair<ReceiptEntity,ReceiptSnapshot>?,returns: List<RefundEntity>,enabled: Boolean,canRefund: Boolean,
    onSelect: (String)->Unit,onRefund: ()->Unit,onPrint: ()->Unit,error: String?=null) {
    ProvideTextStyle(LocalTextStyle.current.copy(platformStyle=PlatformTextStyle(includeFontPadding=false),letterSpacing=0.sp)) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("receiptsPage")) {
            val narrow=maxWidth<=900.dp
            val small=maxWidth<=600.dp
            val listMax=maxHeight*.34f
            Column(Modifier.fillMaxSize().padding(start=if(small) 12.dp else 26.dp,end=if(small) 12.dp else 26.dp,top=if(small) 12.dp else 22.dp,bottom=if(small) 12.dp else 40.dp)) {
                Text("Чеки",Modifier.padding(bottom=18.dp),fontSize=21.sp,lineHeight=29.sp,fontWeight=FontWeight.ExtraBold)
                if(narrow) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    ReceiptList(receipts,types,methods,selectedId,returns,enabled,onSelect,Modifier.fillMaxWidth().heightIn(max=listMax))
                    ReceiptDetail(detail,returns,enabled,canRefund,onRefund,onPrint,error,{onSelect(selectedId)},Modifier.fillMaxWidth().weight(1f))
                } else Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    ReceiptList(receipts,types,methods,selectedId,returns,enabled,onSelect,Modifier.weight(.82f).fillMaxHeight())
                    ReceiptDetail(detail,returns,enabled,canRefund,onRefund,onPrint,error,{onSelect(selectedId)},Modifier.weight(1.18f).fillMaxHeight())
                }
            }
        }
    }
}
@Composable private fun ReceiptList(receipts: List<ReceiptEntity>,types: Map<String,String>,methods: Map<String,String>,selectedId: String,returns: List<RefundEntity>,enabled: Boolean,onSelect: (String)->Unit,modifier: Modifier) {
    Surface(modifier.testTag("receiptsListPanel"),shape=RoundedCornerShape(22.dp),shadowElevation=1.dp) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=18.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("История чеков",Modifier.weight(1f),fontSize=16.sp,lineHeight=20.8.sp,fontWeight=FontWeight.ExtraBold)
                Text("Показано: ${receipts.size}",fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline)
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("receiptHistory"),contentPadding=PaddingValues(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(receipts.isEmpty()) item {Text("Чеков пока нет — они появятся здесь после первой оплаты.",Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=30.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
                items(receipts,key={it.id}) {r->
                    val active=selectedId==r.id;val returned=returns.any {it.receiptId==r.id};val c=MaterialTheme.colorScheme
                    Surface(onClick={onSelect(r.id)},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("receipt:${r.commandId}").semantics {selected=active},shape=RoundedCornerShape(16.dp),
                        color=if(active) c.primaryContainer else if(returned) c.background else c.surface,border=BorderStroke(if(active) 2.dp else 1.dp,if(active) c.primary else c.outline)) {
                        Column(Modifier.padding(horizontal=15.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("Чек №${r.number}",Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.ExtraBold,color=c.onSurfaceVariant)
                                Text(ShiftRules.money(r.total),fontSize=18.sp,lineHeight=21.6.sp,fontWeight=FontWeight.ExtraBold)
                            }
                            Text("${receiptDate(r.createdAt)} · ${methods[r.id] ?: "—"}",fontSize=12.5.sp,lineHeight=17.5.sp,color=c.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                Surface(shape=RoundedCornerShape(100.dp),color=if(active) c.surface else c.background,border=BorderStroke(1.dp,if(active) c.primary else c.outline)) {
                                    Text(types[r.id]?.let(OrderRules::typeLabel) ?: "—",Modifier.padding(horizontal=9.dp,vertical=3.dp),fontSize=11.sp,fontWeight=FontWeight.Bold,color=if(active) c.primary else c.onSurfaceVariant)
                                }
                                if(returned) ReturnSticker(Modifier.testTag("returned:${r.id}"))
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ReceiptDetail(detail: Pair<ReceiptEntity,ReceiptSnapshot>?,returns: List<RefundEntity>,enabled: Boolean,canRefund: Boolean,
    onRefund: ()->Unit,onPrint: ()->Unit,error: String?,retry: ()->Unit,modifier: Modifier) {
    Surface(modifier.testTag("receiptDetailPanel"),shape=RoundedCornerShape(22.dp),shadowElevation=1.dp) {
        Column(Modifier.padding(20.dp)) {
            if(detail==null) {
                Text(error ?: "Выберите чек слева.",Modifier.fillMaxWidth().padding(vertical=30.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                if(error!=null) MposButton("Повторить открытие чека",retry,enabled=enabled,style=ActionStyle.Secondary)
            } else {
                val (r,s)=detail;val returned=returns.firstOrNull {it.receiptId==r.id}
                Text("Чек №${r.number}",Modifier.padding(bottom=14.dp),fontSize=20.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold)
                val scroll=rememberScrollState()
                LaunchedEffect(r.id) {scroll.scrollTo(0)}
                Column(Modifier.weight(1f).fillMaxWidth().padding(end=4.dp).verticalScroll(scroll).testTag("receiptDetails")) {
                    if(returned!=null) Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(12.dp)) {
                        Text("Возврат · ${receiptDate(returned.createdAt)}",Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.ExtraBold)
                    }
                    if(s.cart.details.label.isNotBlank()) Text(s.cart.details.label,Modifier.padding(bottom=5.dp),fontSize=18.sp,lineHeight=24.3.sp,fontWeight=FontWeight.ExtraBold)
                    Text("${receiptDate(r.createdAt)} · ${receiptPaymentLabel(s)} · ${OrderRules.typeLabel(s.cart.details.type)}",Modifier.padding(bottom=10.dp),fontSize=12.5.sp,lineHeight=18.125.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(s.cart.details.address.isNotBlank() || s.cart.details.phone.isNotBlank()) Text(listOf(s.cart.details.phone,s.cart.details.address).filter {it.isNotBlank()}.joinToString(" · "),fontSize=12.5.sp)
                    s.cart.lines.forEach {line->
                        val net=s.quote.lines.first {it.id==line.id}
                        ReceiptMoneyRow("${line.name} × ${line.quantity.replace('.',',')}",net.net,"receiptLine:${line.id}")
                        if(net.discount>0) ReceiptMoneyRow("↳ Скидка",-net.discount,accent=true)
                        if(line.options.isNotEmpty()) Text("↳ "+line.options.joinToString(" · ") {it.name},Modifier.padding(start=10.dp,bottom=5.dp),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(line.comment.isNotBlank()) Text("Комментарий: ${line.comment}",Modifier.padding(start=10.dp,bottom=5.dp),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(s.cart.details.comment.isNotBlank()) Text("Комментарий: ${s.cart.details.comment}",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(s.quote.discount>0) ReceiptMoneyRow("Скидки на товары",-s.quote.discount,accent=true)
                    s.quote.delivery?.let {ReceiptMoneyRow("Доставка",it.fee)}
                    HorizontalDivider(Modifier.padding(top=10.dp),color=MaterialTheme.colorScheme.outline)
                    Text("Платежи",Modifier.padding(top=10.dp),fontSize=12.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    s.payment.parts.forEachIndexed {i,p->ReceiptMoneyRow("Платёж ${i+1} · ${if(p.method=="cash") "Наличные" else "Карта"}",p.amount)}
                    s.payment.parts.filter {it.method=="cash" && it.given!=null}.forEach {p->ReceiptMoneyRow("Наличные · внесено",p.given!!,small=true);ReceiptMoneyRow("Сдача",p.change!!,small=true)}
                    val border=MaterialTheme.colorScheme.outline
                    Canvas(Modifier.fillMaxWidth().padding(top=8.dp).height(1.5.dp)) {drawLine(border,Offset(0f,center.y),Offset(size.width,center.y),size.height,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(),3.dp.toPx())))}
                    Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Итого",fontSize=17.sp,fontWeight=FontWeight.ExtraBold)
                        Text(ShiftRules.money(r.total),Modifier.testTag("receiptTotal"),fontSize=17.sp,fontWeight=FontWeight.ExtraBold)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ReceiptAction("Печать",onPrint,enabled,false,Modifier.weight(1f).testTag("receiptPrint"))
                    if(returned!=null) ReturnSticker(Modifier.weight(1f).heightIn(min=46.dp))
                    else ReceiptAction("Вернуть",onRefund,enabled && canRefund,true,Modifier.weight(1f).testTag("refundPrepare"))
                }
            }
        }
    }
}
@Composable internal fun ReceiptMoneyRow(label: String,amount: Long,tag: String?=null,accent: Boolean=false,small: Boolean=false) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(label,Modifier.weight(1f).then(if(tag!=null) Modifier.testTag(tag) else Modifier),fontSize=if(small || accent) 12.sp else 13.5.sp,lineHeight=18.5.sp,color=if(accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(ShiftRules.money(amount),fontSize=if(small || accent) 12.sp else 13.5.sp,lineHeight=18.5.sp,color=if(accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
@Composable private fun ReturnSticker(modifier: Modifier=Modifier) {
    Surface(modifier.semantics(mergeDescendants=true) {},shape=RoundedCornerShape(100.dp),color=MaterialTheme.colorScheme.errorContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.error)) {
        Box(Modifier.padding(horizontal=10.dp,vertical=5.dp),contentAlignment=Alignment.Center) {Text("Возврат",fontSize=11.sp,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.error)}
    }
}
@Composable private fun ReceiptAction(label: String,onClick: ()->Unit,enabled: Boolean,danger: Boolean,modifier: Modifier) {
    val dark=isMposDarkTheme();val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.height(46.dp),shape=RoundedCornerShape(12.dp),
        color=if(danger) c.errorContainer else if(dark) Color(0xFF4A3B16) else Color(0xFFFFF0B8),
        border=BorderStroke(1.5.dp,if(danger) c.error else if(dark) Color(0xFF8D752B) else Color(0xFFE3B82F))) {
        Box(Modifier.padding(horizontal=12.dp,vertical=11.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=14.sp,fontWeight=FontWeight.ExtraBold,color=if(!enabled) c.onSurfaceVariant else if(danger) c.error else if(dark) Color(0xFFFFE58A) else Color(0xFF6B5200))}
    }
}
