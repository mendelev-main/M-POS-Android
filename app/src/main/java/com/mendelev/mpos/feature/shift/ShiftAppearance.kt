package com.mendelev.mpos.feature.shift

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
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,color=MaterialTheme.colorScheme.surface) {Column(Modifier.padding(20.dp),content=content)}
}
@Composable internal fun ShiftOverview(data: ShiftDashboard,enabled: Boolean,openEnabled: Boolean,admin: Boolean,onOpen: ()->Unit,onClose: ()->Unit,onDeposit: ()->Unit,onWithdraw: ()->Unit,onDrawer: ()->Unit,onReport: (String)->Unit,historyLoading: Boolean=false,historyMessage: String?=null,onMore: ()->Unit={}) {
    val active=data.active
    if(active==null) ShiftCard {
        Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("Смена закрыта",Modifier.testTag("shiftStatus"),fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.ExtraBold)
            Text("Откройте смену, чтобы начать принимать заказы.",Modifier.padding(top=6.dp,bottom=16.dp),fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            MposPosControl("Открыть смену",onOpen,Modifier.testTag("shiftOpen"),enabled && openEnabled,PosControl.Cash,height=44.dp,radius=16.dp,fontSize=14.5f,horizontalPadding=24.dp)
        }
    } else ShiftCard {
        Row(Modifier.fillMaxWidth().padding(bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ShiftAction("Внести наличные",onDeposit,enabled,Modifier.weight(1f).testTag("shiftDeposit"),deposit=true)
            ShiftAction("Открыть кассовый ящик",onDrawer,enabled,Modifier.weight(1f).testTag("shiftDrawer"))
            ShiftAction("Изъять наличные",onWithdraw,enabled,Modifier.weight(1f).testTag("shiftWithdraw"),danger=true)
        }
        ShiftSection {
            Text("Кассовая смена №${active.number}",Modifier.testTag("shiftStatus"),fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.ExtraBold)
            Text("Смена открыта: ${shiftDate(active.shift.openedAt,active.shift.zoneId)} · ${active.shift.actorName}",Modifier.padding(top=5.dp).testTag("shiftCashier"),fontSize=12.5.sp,lineHeight=17.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        val cash: @Composable ()->Unit={ShiftSection {
            ShiftSectionTitle("Наличные в кассе")
            ShiftSummaryLine("Наличные на начало смены",active.shift.openingCash.toBigInteger())
            ShiftSummaryLine("Оплата наличными",active.saleCash)
            ShiftSummaryLine("Возвраты наличными",active.refundCash)
            ShiftSummaryLine("Сумма внесений",active.deposits)
            ShiftSummaryLine("Сумма изъятий",active.withdrawals)
            if(active.baseline!=BigInteger.ZERO) ShiftSummaryLine("Перенесённые движения Web",active.baseline)
            ShiftSummaryLine("Ожидаемая сумма наличных",active.expected,total=true,tag="shiftExpected")
        }}
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("shiftSalesRow"),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            ShiftSection(Modifier.weight(1f).fillMaxHeight().testTag("shiftSalesSummary")) {ShiftSectionTitle("Итоги продаж");ShiftSummaryLine("Продажи",active.sales);ShiftSummaryLine("Возвраты",active.refunds);ShiftSummaryLine("Скидки",active.discounts,last=true)}
            ShiftSection(Modifier.weight(1f).fillMaxHeight().testTag("shiftRevenueSummary")) {ShiftSectionTitle("Выручка");ShiftSummaryLine("Выручка",active.revenue,total=true);ShiftSummaryLine("Наличные",active.cashNet);ShiftSummaryLine("Карта",active.cardNet,last=true)}
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().testTag("shiftCashSummary")) {cash()}
        if(active.imported) Text("Сводка продаж содержит подключённые native-чеки. Перенесённый кассовый остаток Web учтён отдельно.",Modifier.padding(top=14.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(data.pendingCash>0) Text("Частичные оплаты наличными: ${ShiftRules.money(data.pendingCash)}. Завершите оплату перед закрытием смены.",Modifier.padding(top=14.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(active.movements.isNotEmpty()) {Spacer(Modifier.height(16.dp));ShiftSection {ShiftSectionTitle("Движение средств");ShiftMovements(active)}}
        HorizontalDivider(Modifier.padding(top=20.dp,bottom=16.dp))
        ShiftAction("Закрыть смену",onClose,enabled,Modifier.fillMaxWidth().testTag("shiftClose"),outline=true)
    }
    Text("История смен",Modifier.padding(top=22.dp,bottom=12.dp),fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.ExtraBold)
    ShiftCard {
        if(data.history.isEmpty()) Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center) {Text("Ещё нет закрытых смен",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        data.history.forEach {row->
            Row(Modifier.fillMaxWidth().clickable(enabled=enabled) {onReport(row.shift.id)}.padding(horizontal=4.dp,vertical=16.dp).testTag("shiftHistory:${row.shift.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${shiftDate(row.shift.openedAt,row.shift.zoneId)} — ${shiftDate(row.shift.closedAt!!,row.shift.zoneId)}",fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
                    Text("${row.shift.actorName} · Заказов: ${row.count} · Наличные ${shiftMoney(row.cashNet)} · Карта ${shiftMoney(row.cardNet)}",fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape=RoundedCornerShape(999.dp),color=MaterialTheme.colorScheme.background) {Text("Расхожд.: ${ShiftRules.money(row.shift.difference!!)}",Modifier.padding(horizontal=9.dp,vertical=4.dp),fontSize=11.sp,color=if(row.shift.difference==0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)}
            }
            HorizontalDivider()
        }
        historyMessage?.let {MposNotice(it,true)}
        if(data.nextHistory!=null) MposButton(if(historyLoading) "Загрузка…" else "Показать 10 предыдущих",onMore,
            Modifier.fillMaxWidth().padding(top=14.dp).testTag("shiftHistoryMore"),enabled=enabled && !historyLoading,style=ActionStyle.Secondary)
        if(data.history.isNotEmpty()) Text("Показано смен: ${data.history.size}",Modifier.padding(top=10.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ShiftSection(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {Column(Modifier.padding(horizontal=18.dp,vertical=16.dp),content=content)}}
@Composable private fun ShiftSectionTitle(text: String) {Text(text,Modifier.padding(bottom=9.dp),fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.ExtraBold)}
@Composable private fun ShiftSummaryLine(label: String,value: BigInteger,total: Boolean=false,last: Boolean=false,tag: String="") {
    if(total) HorizontalDivider(Modifier.padding(top=5.dp))
    Row(Modifier.fillMaxWidth().heightIn(min=if(total) 44.dp else 38.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(label,Modifier.weight(1f),fontSize=13.5.sp,lineHeight=19.sp,fontWeight=if(total) FontWeight.ExtraBold else FontWeight.SemiBold,color=if(total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(shiftMoney(value),Modifier.testTag(tag),fontSize=if(total) 18.sp else 14.sp,lineHeight=24.sp,fontWeight=FontWeight.Bold)
    }
    if(!total && !last) HorizontalDivider()
}
@Composable internal fun ShiftAction(label: String,onClick: ()->Unit,enabled: Boolean,modifier: Modifier=Modifier,deposit: Boolean=false,danger: Boolean=false,outline: Boolean=false) {
    val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=47.dp),shape=RoundedCornerShape(16.dp),color=if(deposit) (if(isMposDarkTheme()) Color(0xFF8FE0BE) else Color(0xFF26733A)) else if(danger) c.error else if(outline) Color.Transparent else c.background,contentColor=if(deposit) (if(isMposDarkTheme()) Color(0xFF07140F) else Color.White) else if(danger) Color.White else if(outline) c.error else c.onSurface,border=if(outline) BorderStroke(1.5.dp,c.errorContainer) else null) {
        Box(Modifier.padding(horizontal=12.dp,vertical=14.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=14.5.sp,lineHeight=19.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable internal fun ShiftMovements(row: ShiftSummary,ascending: Boolean=false) {
    (if(ascending) row.movements.sortedWith(compareBy<ShiftMovementRow> {it.at}.thenBy {it.id}) else row.movements).forEach {m->
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp).testTag("shiftCash:${m.id}"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(m.title,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Bold)
                Text(shiftDate(m.at,row.shift.zoneId)+(if(m.note.isBlank()) "" else " · ${m.note}"),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text((if(m.delta>=0) "+" else "−")+ShiftRules.money(if(m.delta>=0) m.delta else -m.delta),fontSize=11.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}
@Composable internal fun ShiftReportWindow(row: ShiftSummary,onClose: ()->Unit,onPrint: ()->Unit) {
    MposWebModal("Отчёт по смене",onClose,wide=true) {
        Text("${shiftDate(row.shift.openedAt,row.shift.zoneId)} — ${shiftDate(row.shift.closedAt!!,row.shift.zoneId)} · ${row.shift.actorName}",Modifier.padding(bottom=14.dp),fontSize=12.sp,lineHeight=17.4.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        val values=listOf("Заказов" to row.count.toString(),"Выручка" to shiftMoney(row.revenue),"Наличные" to shiftMoney(row.cashNet),"Карта" to shiftMoney(row.cardNet),"Расхождение" to ShiftRules.money(row.shift.difference!!),"Внесено наличных" to shiftMoney(row.deposits),"Изъято наличных" to shiftMoney(row.withdrawals))
        values.take(3).let {cells->Row(Modifier.padding(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {cells.forEach {ShiftReportCell(it.first,it.second,Modifier.weight(1f))}}}
        values.drop(3).chunked(2).forEach {cells->Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {cells.forEach {ShiftReportCell(it.first,it.second,Modifier.weight(1f))}}}
        if(row.imported) Text("Включены native-чеки; перенесённый остаток Web: ${shiftMoney(row.baseline)}",Modifier.padding(top=12.dp),fontSize=12.sp)
        Text("Движение наличных",Modifier.padding(top=16.dp,bottom=8.dp),fontWeight=FontWeight.Bold)
        if(row.movements.isEmpty()) Text("Движения наличных не было",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) else Column(Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState())) {ShiftMovements(row,ascending=true)}
        Row(Modifier.padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposPosControl("Закрыть",onClose,Modifier.weight(1f),kind=PosControl.Secondary,height=48.dp,radius=16.dp)
            MposPosControl("Распечатать отчёт",onPrint,Modifier.weight(1f).testTag("shiftPrint"),kind=PosControl.Cash,height=48.dp,radius=16.dp)
        }
    }
}
@Composable private fun ShiftReportCell(label: String,value: String,modifier: Modifier) {Surface(modifier,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.background) {Column(Modifier.padding(16.dp)) {Text(label,Modifier.padding(bottom=6.dp),fontSize=12.5.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontSize=20.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold)}}}
