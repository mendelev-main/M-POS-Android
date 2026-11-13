package com.mendelev.mpos.feature.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.shift.*
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun shiftMoney(value: BigInteger)=BigDecimal(value,2).toPlainString().replace('.',',')+" BYN"
internal fun shiftDate(value: Long,zone: String)=DateTimeFormatter.ofPattern("dd.MM, HH:mm").withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(value))
@Composable internal fun ShiftCard(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(18.dp),content=content)}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ShiftOverview(data: ShiftDashboard,enabled: Boolean,openEnabled: Boolean,admin: Boolean,onOpen: ()->Unit,onClose: ()->Unit,onDeposit: ()->Unit,onWithdraw: ()->Unit,onDrawer: ()->Unit,onReport: (String)->Unit,historyLoading: Boolean=false,historyMessage: String?=null,onMore: ()->Unit={}) {
    val active=data.active
    if(active==null) ShiftCard {
        Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Смена закрыта",Modifier.testTag("shiftStatus"),fontSize=18.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            MposPosControl("Открыть смену",onOpen,Modifier.testTag("shiftOpen"),enabled && openEnabled,PosControl.Cash,height=44.dp,radius=14.dp)
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Кассовая смена №${active.number}",Modifier.weight(1f).testTag("shiftStatus"),fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            ShiftAction("Закрыть смену",onClose,enabled,Modifier.width(220.dp).testTag("shiftClose"),outline=true)
        }
        val cash: @Composable (Modifier)->Unit={modifier->ShiftCard(modifier.testTag("shiftCashSummary")) {
            ShiftSectionTitle("Наличные в кассе")
            Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(shiftMoney(active.expected),Modifier.testTag("shiftExpected"),fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.ExtraBold)
                }
                ShiftStatus("Касса",icon="cash")
            }
            ShiftQuietLine("На начало",active.shift.openingCash.toBigInteger())
            ShiftQuietLine("Оплаты наличными",active.saleCash,prefix="+")
            ShiftQuietLine("Возвраты наличными",active.refundCash,prefix="−")
            ShiftQuietLine("Внесения",active.deposits,prefix="+")
            ShiftQuietLine("Изъятия",active.withdrawals,prefix="−")
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ShiftAction("Внести",onDeposit,enabled,Modifier.weight(1f).fillMaxHeight().testTag("shiftDeposit"),deposit=true,icon="add")
                ShiftAction("Открыть ящик",onDrawer,enabled,Modifier.weight(1f).fillMaxHeight().testTag("shiftDrawer"),icon="drawer")
                ShiftAction("Изъять",onWithdraw,enabled,Modifier.weight(1f).fillMaxHeight().testTag("shiftWithdraw"),danger=true,icon="minus")
            }
        }}
        val sales: @Composable (Modifier)->Unit={modifier->Column(modifier,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            ShiftCard(Modifier.testTag("shiftRevenueSummary")) {
                ShiftSectionTitle("Выручка")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    ShiftMetric(null,shiftMoney(active.revenue),Modifier.weight(1f),large=true)
                    ShiftMetric("Чеков",active.count.toString())
                }
                ShiftPaymentSplit(active)
            }
            ShiftCard(Modifier.weight(1f).testTag("shiftSalesSummary")) {
                Text(active.shift.actorName,Modifier.testTag("shiftCashier"),fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Bold)
                Text("Открыта: ${shiftDate(active.shift.openedAt,active.shift.zoneId)}",Modifier.padding(top=7.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.outline)
                ShiftSectionTitle("Продажи за смену")
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    ShiftMetric("Продажи",shiftMoney(active.sales).removeSuffix(" BYN"),Modifier.weight(1f).widthIn(min=110.dp))
                    ShiftMetric("Возвраты",shiftMoney(active.refunds).removeSuffix(" BYN"),Modifier.weight(1f).widthIn(min=110.dp))
                    ShiftMetric("Скидки",shiftMoney(active.discounts).removeSuffix(" BYN"),Modifier.weight(1f).widthIn(min=110.dp))
                }
            }
        }}
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("shiftSalesRow")) {
            if(maxWidth>=780.dp) Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                cash(Modifier.weight(1f).fillMaxHeight())
                sales(Modifier.weight(1f).fillMaxHeight())
            } else Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {cash(Modifier);sales(Modifier)}
        }
        if(data.pendingCash>0) Text("Частичные оплаты наличными: ${ShiftRules.money(data.pendingCash)}. Завершите оплату перед закрытием смены.",Modifier.padding(top=14.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        ShiftCard {
            ShiftSectionTitle("Последние операции")
            if(active.movements.isEmpty()) Text("Движений наличных пока нет",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else ShiftMovements(active)
        }
    }
    Spacer(Modifier.height(18.dp))
    ShiftCard {
        ShiftSectionTitle("История смен")
        if(data.history.isEmpty()) Text("Ещё нет закрытых смен",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        data.history.forEach {row->
            Surface(onClick={onReport(row.shift.id)},enabled=enabled,modifier=Modifier.fillMaxWidth().padding(bottom=9.dp).testTag("shiftHistory:${row.shift.id}"),
                shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
                FlowRow(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).widthIn(min=210.dp)) {
                        Text("№${row.number} · ${shiftDate(row.shift.openedAt,row.shift.zoneId)} — ${shiftDate(row.shift.closedAt!!,row.shift.zoneId)}",fontSize=14.sp,fontWeight=FontWeight.Bold)
                        Text("${row.shift.actorName} · Чеков: ${row.count}",Modifier.padding(top=5.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment=Alignment.End) {
                        Text(shiftMoney(row.revenue),fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Text("Расхождение ${ShiftRules.money(row.shift.difference!!)}",Modifier.padding(top=5.dp),fontSize=12.sp,
                            color=if(row.shift.difference==0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    }
                    MposPosControl("Отчёт",{onReport(row.shift.id)},enabled=enabled,kind=PosControl.Outline,height=44.dp,radius=14.dp,icon="bill")
                }
            }
        }
        historyMessage?.let {MposNotice(it,true)}
        if(data.nextHistory!=null) MposButton(if(historyLoading) "Загрузка…" else "Показать 10 предыдущих",onMore,
            Modifier.fillMaxWidth().padding(top=7.dp).testTag("shiftHistoryMore"),enabled=enabled && !historyLoading,style=ActionStyle.Secondary)
    }
}

@Composable private fun ShiftStatus(label: String,icon: String?=null) {
    Surface(shape=RoundedCornerShape(10.dp),color=if(isMposDarkTheme()) Color(0xFF233E34) else Color(0xFFE3F3EB),contentColor=if(isMposDarkTheme()) Color(0xFF8FE0BE) else Color(0xFF167451)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if(icon!=null) MposPosIcon(icon,Modifier.size(16.dp))
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable private fun ShiftMetric(label: String?,value: String,modifier: Modifier=Modifier,large: Boolean=false) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        label?.let {Text(it,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Text(value,fontSize=if(large) 30.sp else 21.sp,lineHeight=if(large) 38.sp else 29.sp,fontWeight=FontWeight.ExtraBold)
    }
}
@Composable private fun ShiftQuietLine(label: String,value: BigInteger,prefix: String="") {
    Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(prefix+shiftMoney(value),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ShiftPaymentSplit(row: ShiftSummary) {
    val green=if(isMposDarkTheme()) Color(0xFF8FE0BE) else Color(0xFF167451)
    val purple=if(isMposDarkTheme()) Color(0xFFC3A8EE) else Color(0xFF7752AD)
    val cash=row.cashNet.max(BigInteger.ZERO);val card=row.cardNet.max(BigInteger.ZERO);val sum=cash+card
    // Only the visual width uses Float. All values remain exact minor-unit integers.
    if(sum>BigInteger.ZERO && row.cashNet.signum()>=0 && row.cardNet.signum()>=0) {
        val share=cash.toBigDecimal().divide(sum.toBigDecimal(),6,java.math.RoundingMode.HALF_UP).toFloat()
        Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            if(share>0f) Box(Modifier.weight(share).height(8.dp).background(green,RoundedCornerShape(4.dp)))
            if(share<1f) Box(Modifier.weight(1f-share).height(8.dp).background(purple,RoundedCornerShape(4.dp)))
        }
    } else Spacer(Modifier.height(18.dp))
    FlowRow(horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(Triple("Наличные",row.cashNet,green),Triple("Карта",row.cardNet,purple)).forEach {(label,value,color)->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).background(color,RoundedCornerShape(4.dp)))
                Text("$label ${shiftMoney(value)}",fontSize=12.sp)
            }
        }
    }
}
@Composable private fun ShiftSectionTitle(text: String) {Text(text,Modifier.padding(bottom=14.dp),fontSize=15.sp,lineHeight=21.sp,fontWeight=FontWeight.Bold)}
@Composable internal fun ShiftAction(label: String,onClick: ()->Unit,enabled: Boolean,modifier: Modifier=Modifier,deposit: Boolean=false,danger: Boolean=false,outline: Boolean=false,icon: String?=null) {
    val c=MaterialTheme.colorScheme
    val softGreen=if(isMposDarkTheme()) Color(0xFF233E34) else Color(0xFFE3F3EB)
    val green=if(isMposDarkTheme()) Color(0xFF8FE0BE) else Color(0xFF167451)
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=44.dp),shape=RoundedCornerShape(14.dp),
        color=if(deposit) softGreen else if(danger) c.errorContainer else if(outline) c.surface else c.background,
        contentColor=if(deposit) green else if(danger || outline) c.error else c.onSurface,
        border=if(outline) BorderStroke(1.dp,c.error.copy(alpha=.45f)) else if(!deposit && !danger) BorderStroke(1.dp,c.outline) else null) {
        Row(Modifier.padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            if(icon!=null) {MposPosIcon(icon,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))}
            Text(label,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}
@Composable internal fun ShiftMovements(row: ShiftSummary,ascending: Boolean=false) {
    (if(ascending) row.movements.sortedWith(compareBy<ShiftMovementRow> {it.at}.thenBy {it.id}) else row.movements).forEach {m->
        Surface(Modifier.fillMaxWidth().padding(bottom=9.dp).testTag("shiftCash:${m.id}"),shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
            Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                val color=if(m.delta<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surface,contentColor=color) {
                    Box(Modifier.size(34.dp),contentAlignment=Alignment.Center) {MposPosIcon(if(m.delta<0) "minus" else "add",Modifier.size(18.dp))}
                }
                Column(Modifier.weight(1f)) {
                    Text(m.title,fontSize=14.sp,fontWeight=FontWeight.Bold)
                    Text(shiftDate(m.at,row.shift.zoneId)+(if(m.note.isBlank()) "" else " · ${m.note}"),Modifier.padding(top=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val magnitude=BigInteger.valueOf(m.delta).abs()
                Text((if(m.delta>=0) "+" else "−")+shiftMoney(magnitude),fontSize=14.sp,fontWeight=FontWeight.Bold,color=color)
            }
        }
    }
}
@Composable internal fun ShiftReportWindow(row: ShiftSummary,onClose: ()->Unit,onPrint: ()->Unit) {
    MposWebModal("Отчёт по смене",onClose,wide=true) {
        Text("${shiftDate(row.shift.openedAt,row.shift.zoneId)} — ${shiftDate(row.shift.closedAt!!,row.shift.zoneId)} · ${row.shift.actorName}",Modifier.padding(bottom=14.dp),fontSize=12.sp,lineHeight=17.4.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val values=listOf("Заказов" to row.count.toString(),"Выручка" to shiftMoney(row.revenue),"Наличные" to shiftMoney(row.cashNet),"Карта" to shiftMoney(row.cardNet),"Расхождение" to ShiftRules.money(row.shift.difference!!),"Внесено наличных" to shiftMoney(row.deposits),"Изъято наличных" to shiftMoney(row.withdrawals))
        values.take(3).let {cells->Row(Modifier.padding(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {cells.forEach {ShiftReportCell(it.first,it.second,Modifier.weight(1f))}}}
        values.drop(3).chunked(2).forEach {cells->Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {cells.forEach {ShiftReportCell(it.first,it.second,Modifier.weight(1f))}}}
        Text("Движение наличных",Modifier.padding(top=16.dp,bottom=8.dp),fontWeight=FontWeight.Bold)
        if(row.movements.isEmpty()) Text("Движения наличных не было",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) else Column(Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState())) {ShiftMovements(row,ascending=true)}
        Row(Modifier.padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposPosControl("Закрыть",onClose,Modifier.weight(1f),kind=PosControl.Secondary,height=48.dp,radius=16.dp)
            MposPosControl("Распечатать отчёт",onPrint,Modifier.weight(1f).testTag("shiftPrint"),kind=PosControl.Cash,height=48.dp,radius=16.dp)
        }
    }
}
@Composable private fun ShiftReportCell(label: String,value: String,modifier: Modifier) {Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.background) {Column(Modifier.padding(16.dp)) {Text(label,Modifier.padding(bottom=6.dp),fontSize=12.5.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold)}}}
