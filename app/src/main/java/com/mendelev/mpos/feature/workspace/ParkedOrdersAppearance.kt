package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.workspace.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** C09: 820px / 24px inset / 44px header / 18px gap / 66px cards in pinned Web. */
@Composable internal fun ParkedOrdersWindow(orders: List<ParkedOrder>,busy: Boolean,message: String?,
    stale: (ParkedOrder)->Boolean,onClose: ()->Unit,onOpen: (ParkedOrder)->Unit,onDelete: (ParkedOrder)->Unit) {
    val sorted=remember(orders) {
        val collator=(android.icu.text.Collator.getInstance(Locale.forLanguageTag("ru")) as android.icu.text.RuleBasedCollator).apply {numericCollation=true;strength=android.icu.text.Collator.PRIMARY}
        orders.sortedWith {a,b->
            val left=a.cart.details.label.trim();val right=b.cart.details.label.trim()
            when {left.isEmpty()!=right.isEmpty()->if(left.isEmpty()) 1 else -1
                else->collator.compare(left,right).takeIf {it!=0} ?: (b.cart.createdAt.takeIf {it>0} ?: b.parkedAt).compareTo(a.cart.createdAt.takeIf {it>0} ?: a.parkedAt)}
        }
    }
    Dialog(onDismissRequest={if(!busy) onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as DialogWindowProvider).window
        SideEffect {window.setDimAmount(0f)}
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF1B1F2A).copy(alpha=.45f))
            .pointerInput(busy) {detectTapGestures {if(!busy) onClose()}}.padding(20.dp)) {
            val compact=maxWidth<=600.dp
            val maximumHeight=(maxHeight+40.dp)*.92f
            Surface(Modifier.heightIn(max=maximumHeight).pointerInput(Unit) {detectTapGestures {}}.widthIn(max=820.dp).fillMaxWidth().align(Alignment.Center).testTag("parkedWindow"),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(if(compact) 18.dp else 24.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("Отложенные чеки",Modifier.weight(1f),fontSize=if(compact) 19.sp else 22.sp,fontWeight=FontWeight.ExtraBold)
                        MposPosControl("",onClose,Modifier.size(44.dp).semantics {contentDescription="Закрыть отложенные чеки"},!busy,PosControl.Close,44.dp,icon="close")
                    }
                    Spacer(Modifier.height(18.dp))
                    if(message!=null) {MposNotice(message,true);Spacer(Modifier.height(12.dp))}
                    LazyColumn(Modifier.weight(1f,fill=false).testTag("parkedList"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(sorted,key={it.id}) {order->
                            Surface(Modifier.fillMaxWidth().testTag("parkedCard:${order.id}"),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                                Row(Modifier.padding(1.dp).padding(horizontal=if(compact) 10.dp else 12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Surface(onClick={onOpen(order)},enabled=!busy,modifier=Modifier.weight(1f).testTag("parkedOpen:${order.id}")) {
                                        Row(Modifier.heightIn(min=66.dp).padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(if(compact) 8.dp else 16.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                                Text(order.cart.details.label.ifBlank {"Без подписи"},fontSize=14.sp,lineHeight=19.6.sp,fontWeight=FontWeight.Bold)
                                                Text(SimpleDateFormat("dd.MM, HH:mm",Locale.forLanguageTag("ru")).format(Date(order.cart.createdAt.takeIf {it>0} ?: order.parkedAt)),fontSize=11.sp,lineHeight=16.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                                if(stale(order)) Text("Требуется проверка: каталог изменился",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
                                            }
                                            if(compact) Column(Modifier.width(96.dp),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {ParkedTotal(order);ParkedType(order.cart.details.type)}
                                            else {Box(Modifier.width(110.dp)) {ParkedType(order.cart.details.type)};Box(Modifier.width(104.dp),contentAlignment=Alignment.CenterEnd) {ParkedTotal(order)}}
                                        }
                                    }
                                    MposPosControl("",{onDelete(order)},Modifier.size(40.dp).testTag("parkedDelete:${order.id}").semantics {contentDescription="Удалить отложенный заказ"},!busy,PosControl.Delete,40.dp,10.dp,icon="delete")
                                }
                            }
                        }
                        if(orders.isEmpty()) item {
                            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Нет отложенных чеков",fontSize=18.sp,fontWeight=FontWeight.Bold)
                                Text("Нажмите «Отложить» в текущем заказе, чтобы сохранить чек и вернуться к нему позже.",fontSize=14.sp,lineHeight=22.4.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ParkedTotal(order: ParkedOrder) {
    Text("${CartEngine.total(order.cart).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString().replace('.',',')} BYN",fontSize=16.sp,fontWeight=FontWeight.ExtraBold)
}
@Composable private fun ParkedType(type: String) {
    val dark=isSystemInDarkTheme()
    val palette=when(type) {
        "takeaway"->if(dark) listOf(0xFF3A3022,0xFFE9C989,0xFF5A4A30) else listOf(0xFFFFF4E9,0xFF8A5A17,0xFFF5DCB8)
        "delivery"->if(dark) listOf(0xFF26334A,0xFFBFD5FF,0xFF3C506F) else listOf(0xFFE8F0FF,0xFF31558F,0xFFC7D7F2)
        else->if(dark) listOf(0xFF1D4638,0xFF8FE0BE,0xFF43C38D) else listOf(0xFFE3F6E8,0xFF26733A,0xFFC9EBCF)
    }
    Surface(shape=RoundedCornerShape(8.dp),color=Color(palette[0]),contentColor=Color(palette[1]),border=BorderStroke(1.dp,Color(palette[2]))) {
        Row(Modifier.padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            MposPosIcon(type,Modifier.size(13.dp));Text(OrderRules.typeLabel(type),fontSize=11.sp,lineHeight=14.3.sp,fontWeight=FontWeight.Bold)
        }
    }
}
