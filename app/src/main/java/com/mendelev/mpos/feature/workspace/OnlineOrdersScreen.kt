package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.OnlineAcceptance
import kotlinx.coroutines.*
import org.json.JSONObject
import java.math.BigDecimal

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OnlineOrdersScreen(onWorkspace: ()->Unit) {
    val context=LocalContext.current;val runtime=remember {BackendRuntime.get(context)}
    val orders by runtime.orders.collectAsState();val records by runtime.accepted.collectAsState()
    val status by runtime.status.collectAsState();val busy by runtime.busy.collectAsState()
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var estimate by remember {mutableStateOf("")}
    var custom by remember {mutableStateOf(false)}
    var customTime by remember {mutableStateOf("")}
    var message by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    MposDialog(if(selected==null) "События" else "Заказ с сайта ${selected!!.optString("external_id")}",onWorkspace,closable=!busy,maxWidth=760.dp) {
        val detail=selected
        if(detail==null) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("onlineOrdersScreen"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(status,style=MaterialTheme.typography.bodySmall)
            message?.let {Text(it)}
            if(orders.isEmpty()) Text("Новых заказов нет")
            orders.forEach {order->
                Surface(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable(enabled=!busy) {selected=order;estimate="";custom=false;customTime="";message=null}.testTag("online:${order.getString("id")}")) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Заказ с сайта ${order.optString("external_id")}",fontWeight=FontWeight.Bold)
                        Text("${order.optString("customer_name")} · ${order.opt("total")} BYN")
                        Text(order.optString("created_at"),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            records.filter {(!it.settled || !it.readyConfirmed) && !it.cancelled}.forEach {record->key(record.sessionId) {OnlineOrderState(record,runtime)}}
            if(records.any {(!it.settled || !it.readyConfirmed) && !it.cancelled}) MposButton("К рабочей зоне и Отложенным",onWorkspace,style=ActionStyle.Outline)
        }
        MposButton("Закрыть",onWorkspace,Modifier.fillMaxWidth().padding(top=16.dp),enabled=!busy,style=ActionStyle.Outline)
        } else {
        val o=detail
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(o.optString("customer_name"),style=MaterialTheme.typography.titleLarge)
            Text(o.optString("phone"))
            Text(o.optString("order_type","На месте"),fontWeight=FontWeight.Bold)
            val items=o.getJSONArray("order_items")
            repeat(items.length()) {i->val item=items.getJSONObject(i)
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.035f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(item.optString("product_name"),fontWeight=FontWeight.Bold)
                        Text("${item.get("quantity")} × ${item.get("price")} BYN")
                        val comment=item.optString("comment").takeUnless {it=="null"}.orEmpty();if(comment.isNotBlank()) Text(comment)
                    }
                }
            }
            val delivery=o.opt("delivery_fee")?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            if(delivery.signum()>0) Text("Доставка: $delivery BYN")
            Text("Итого: ${o.get("total")} BYN",style=MaterialTheme.typography.titleLarge)
            val address=o.optString("address").takeUnless {it=="null"}.orEmpty();if(address.isNotBlank()) Text("Адрес: $address")
            val comment=o.optString("comment").takeUnless {it=="null"}.orEmpty();if(comment.isNotBlank()) Text("Комментарий: $comment")
            Text("Время готовности",fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("5m" to "5 мин","15m" to "15 мин","30m" to "30 мин","40m" to "40 мин","60plus" to "Больше часа").forEach {(value,label)->
                    MposButton(label,{estimate=value;custom=false},enabled=!busy,style=if(estimate==value) ActionStyle.Primary else ActionStyle.Outline)
                }
                MposButton("Будет готово к",{custom=true;estimate="at:$customTime"},enabled=!busy,style=if(custom) ActionStyle.Primary else ActionStyle.Outline)
            }
            if(custom) MposWebField(customTime,{customTime=it;estimate="at:$it"},"Время HH:mm","onlineReadyTime",!busy,placeholder="18:30")
            Text("Время готовности сохраняется на планшете.",style=MaterialTheme.typography.bodySmall)
            message?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Назад",{selected=null},Modifier.weight(1f),enabled=!busy,style=ActionStyle.Outline)
            MposButton("Взять в работу",{scope.launch {try {message=runtime.accept(o,estimate);selected=null} catch(e:Exception) {message=e.message ?: "Не удалось принять заказ"}}},Modifier.weight(1f).testTag("onlineAccept"),enabled=!busy && com.mendelev.mpos.data.local.OnlineOrderCodec.estimate(estimate))
        }
        }
    }
}

@Composable internal fun OnlineOrderState(record: OnlineAcceptance,runtime: BackendRuntime) {
    val scope=rememberCoroutineScope();var message by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)};var cancelConfirm by remember {mutableStateOf(false)}
    Surface(shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.035f)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Веб ${record.externalId} · "+when {record.paid && record.readyConfirmed->"Оплачен · Готов";record.paid->"Оплачен";record.cancelPending->"Отмена ожидает подтверждения";record.conflict=="cancelled"->"Отменён на сайте";record.readyConfirmed->"Готов";record.ready->"Готов · ожидает подтверждения";record.accepted->"В работе";else->"Ожидает подтверждения принятия"},fontWeight=FontWeight.Bold)
            Text("Готовность: "+when(record.estimate) {"5m"->"5 мин";"15m"->"15 мин";"30m"->"30 мин";"40m"->"40 мин";"60plus"->"Больше часа";else->record.estimate.removePrefix("at:")},style=MaterialTheme.typography.bodySmall)
            if(!record.ready && !record.cancelPending && record.conflict.isEmpty()) MposButton("Заказ готов",{busy=true;scope.launch {try {message=runtime.ready(record.sessionId)} catch(e:Exception) {message=e.message ?: "Не удалось сохранить готовность"} finally {busy=false}}},enabled=!busy && record.accepted)
            if(!record.cancelPending && !record.paid) MposButton("Отменить заказ",{cancelConfirm=true},enabled=!busy,style=ActionStyle.Danger)
            message?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }
    if(cancelConfirm) MposDialog("Отменить онлайн-заказ?",{cancelConfirm=false},closable=!busy,maxWidth=520.dp) {
        Text("Отмена будет отправлена на сайт. После подтверждения заказ удалится из Отложенных, а резерв освободится. Оплаченные чеки отменяются через возврат.")
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Назад",{cancelConfirm=false},Modifier.weight(1f),enabled=!busy,style=ActionStyle.Outline)
            MposButton("Отменить заказ",{busy=true;scope.launch {try {message=runtime.cancel(record.sessionId);cancelConfirm=false} catch(e:Exception) {message=e.message ?: "Не удалось сохранить отмену";cancelConfirm=false} finally {busy=false}}},Modifier.weight(1f),enabled=!busy,style=ActionStyle.Danger)
        }
    }

}

@Composable internal fun CurrentOnlineOrder(session: String) {
    val runtime=BackendRuntime.get(LocalContext.current);val records by runtime.accepted.collectAsState()
    records.singleOrNull {it.sessionId==session && (!it.settled || !it.readyConfirmed) && !it.cancelled}?.let {key(it.sessionId) {OnlineOrderState(it,runtime)}}
}

@Composable internal fun BackendDemandDialog(onClose: ()->Unit) {
    val runtime=BackendRuntime.get(LocalContext.current);val overloaded by runtime.overloaded.collectAsState()
    val scope=rememberCoroutineScope();var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    MposDialog("Загрузка",onClose,closable=!busy,maxWidth=520.dp) {
        Text(if(overloaded) "Повышенная загрузка" else "Обычная загрузка")
        message?.let {Text(it)}
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(false to "Обычная",true to "Повышенная").forEach {(value,label)->MposButton(label,{busy=true;scope.launch {try {message=runtime.setDemand(value)} catch(e:Exception) {message=e.message ?: "Backend не настроен"} finally {busy=false}}},Modifier.weight(1f),enabled=!busy,style=if(value==overloaded) ActionStyle.Primary else ActionStyle.Outline)}
        }
    }
}
