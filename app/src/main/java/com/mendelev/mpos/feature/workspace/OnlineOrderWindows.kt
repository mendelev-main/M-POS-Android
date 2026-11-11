package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.OnlineOrderCodec
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun onlineMoney(value: Any?): String=runCatching {
    value.toString().toBigDecimal().setScale(2,RoundingMode.HALF_UP).toPlainString().replace('.',',')+" BYN"
}.getOrDefault("—")
private fun onlineText(order: JSONObject,key: String)=order.optString(key).takeUnless {it=="null"}.orEmpty()
private fun onlineDate(order: JSONObject)=runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(order.getString("created_at")))
}.getOrDefault("")

@Composable internal fun OnlineEventsWindow(orders: List<JSONObject>,busy: Boolean,onSelect: (JSONObject)->Unit,onClose: ()->Unit) {
    MposDialog("События",onClose,closable=!busy) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("onlineOrdersScreen")) {
            if(orders.isEmpty()) Text("Новых событий нет",Modifier.fillMaxWidth().padding(vertical=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            orders.sortedByDescending {onlineText(it,"created_at")}.forEach {order->
                Row(Modifier.fillMaxWidth().clickable(enabled=!busy) {onSelect(order)}.testTag("online:${order.getString("id")}")
                    .padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("Заказ с сайта ${onlineText(order,"external_id")}",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("${onlineText(order,"customer_name").ifBlank {"Клиент"}} · ${onlineMoney(order.opt("total"))} · ${onlineDate(order)}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MposPosIcon("next",Modifier.size(18.dp))
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
            }
        }
        MposButton("Закрыть",onClose,Modifier.fillMaxWidth(),enabled=!busy,style=ActionStyle.Secondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun OnlineOrderWindow(order: JSONObject,busy: Boolean,message: String?,onBack: ()->Unit,canDelete: Boolean=false,onDelete: ()->Unit={},onAccept: (String)->Unit) {
    var estimate by remember(order.getString("id")) {mutableStateOf("")}
    var customTime by remember(order.getString("id")) {mutableStateOf("")}
    val custom=estimate=="custom" || estimate.startsWith("at:")
    MposDialog("",onBack,closable=!busy,maxWidth=940.dp,contentPadding=0.dp,spacing=0.dp,header={
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Заказ с сайта",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(onlineText(order,"external_id").ifBlank {"Новый заказ"},Modifier.padding(top=6.dp,bottom=8.dp),fontSize=18.sp,fontWeight=FontWeight.Bold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(onlineText(order,"customer_name").ifBlank {"Клиент"},fontSize=14.sp,fontWeight=FontWeight.Bold)
                    onlineText(order,"phone").takeIf {it.isNotBlank()}?.let {Text(it,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
            MposCloseButton(onBack,!busy,description="Закрыть заказ с сайта")
        }
    }) {
        HorizontalDivider()
        BoxWithConstraints(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp).testTag("onlineOrderBody")) {
            val twoReadyColumns=maxWidth<360.dp
            val products: @Composable (Modifier)->Unit={modifier->OnlineSection("Состав заказа",modifier,accessory={
                Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.background) {
                    Text(when(onlineText(order,"order_type").lowercase()) {"delivery","доставка"->"Доставка";"pickup","самовывоз","с собой","на вынос"->"На вынос";else->"На месте"},Modifier.padding(horizontal=9.dp,vertical=6.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }) {
                val items=order.getJSONArray("order_items")
                repeat(items.length()) {index->val item=items.getJSONObject(index)
                    Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(onlineText(item,"product_name"),fontSize=14.sp,fontWeight=FontWeight.Bold)
                            Text("${item.get("quantity")} × ${onlineMoney(item.opt("price"))}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            onlineText(item,"comment").takeIf {it.isNotBlank()}?.let {Text(it,fontSize=12.sp)}
                        }
                        Text(onlineMoney(BigDecimal(item.get("price").toString())*BigDecimal(item.get("quantity").toString())),fontSize=14.sp,fontWeight=FontWeight.Bold)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                }
                if(order.opt("delivery_fee")?.toString()?.toBigDecimalOrNull()?.signum()==1 || onlineText(order,"delivery_tariff_id").isNotBlank()) {
                    OnlineAmount("Доставка"+onlineText(order,"delivery_tariff_name").takeIf {it.isNotBlank()}?.let {" · $it"}.orEmpty(),onlineMoney(order.opt("delivery_fee")))
                }
                OnlineAmount("Итого",onlineMoney(order.opt("total")),true)
            }}
            val details: @Composable (Modifier)->Unit={modifier->Column(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                onlineText(order,"address").takeIf {it.isNotBlank()}?.let {OnlineSection("Адрес доставки") {Text(it,fontSize=14.sp,lineHeight=21.sp)}}
                onlineText(order,"comment").takeIf {it.isNotBlank()}?.let {OnlineSection("Комментарий клиента",danger=true) {Text(it,fontSize=14.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.SemiBold)}}
                OnlineSection("Время готовности") {
                    val choices=listOf("5m" to "5 мин","15m" to "15 мин","30m" to "30 мин","40m" to "40 мин","60plus" to "Больше часа","custom" to "Будет готово к")
                    Box {val columns=if(twoReadyColumns) 2 else 3
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            choices.chunked(columns).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                row.forEach {(value,label)->
                                    val active=estimate==value || value=="custom" && custom
                                    MposPosControl(label,{estimate=if(value=="custom" && customTime.isNotBlank()) "at:$customTime" else value},
                                        Modifier.weight(1f).testTag("onlineEstimate:$value").semantics {selected=active},!busy,
                                        if(active) PosControl.Cash else PosControl.Secondary,48.dp,16.dp,13f,horizontalPadding=6.dp,retainingAppearance=true)
                                }
                            }}
                        }
                    }
                    if(custom) {
                        HorizontalDivider(Modifier.padding(top=2.dp))
                        MposWebField(customTime,{customTime=it;estimate="at:$it"},"Укажите время","onlineReadyTime",!busy,placeholder="18:30")
                        Text("По времени заведения",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }}
            if(maxWidth>680.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {products(Modifier.weight(1.15f));details(Modifier.weight(1f))}
            else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {products(Modifier.fillMaxWidth());details(Modifier.fillMaxWidth())}
        }
        message?.let {Text(it,Modifier.padding(horizontal=20.dp,vertical=8.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("Назад",onBack,Modifier.weight(1f),!busy,PosControl.Secondary,48.dp,16.dp,retainingAppearance=true)
            if(canDelete) MposPosControl("Удалить заказ",onDelete,Modifier.weight(1.4f).testTag("onlineDelete"),!busy,PosControl.Delete,48.dp,16.dp,retainingAppearance=true)
            MposPosControl("Взять в работу",{onAccept(estimate)},Modifier.weight(2f).testTag("onlineAccept"),!busy && OnlineOrderCodec.estimate(estimate),PosControl.Cash,48.dp,16.dp,retainingAppearance=busy)
        }
    }
}

@Composable private fun OnlineSection(title: String,modifier: Modifier=Modifier,accessory: (@Composable ()->Unit)?=null,danger: Boolean=false,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {Row(verticalAlignment=Alignment.CenterVertically) {Text(title,Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.ExtraBold,color=if(danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface);accessory?.invoke()};content()}
    }
}
@Composable private fun OnlineAmount(label: String,value: String,total: Boolean=false) {
    Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),fontSize=if(total) 18.sp else 13.sp,fontWeight=if(total) FontWeight.ExtraBold else FontWeight.Normal,color=if(total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,fontSize=if(total) 18.sp else 13.sp,fontWeight=FontWeight.Bold,color=if(total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
