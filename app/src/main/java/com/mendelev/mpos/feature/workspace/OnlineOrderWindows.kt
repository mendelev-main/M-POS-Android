package com.mendelev.mpos.feature.workspace

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

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun OnlineEventsWindow(orders: List<JSONObject>,busy: Boolean,onSelect: (JSONObject)->Unit,onClose: ()->Unit,
    history: Boolean=false,loading: Boolean=false,error: String?=null,hasMore: Boolean=false,onCurrent: ()->Unit={},onHistory: ()->Unit={},onRefresh: ()->Unit={},onMore: ()->Unit={},currentCount: Int=orders.size) {
    MposDialog("",onClose,closable=!busy,maxWidth=940.dp,contentPadding=0.dp,spacing=0.dp,header={
        EventsHeader(onClose=onClose,onRefresh=onRefresh,busy=busy)
    }) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=12.dp)) {
            Surface(shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=eventColor(0xFFF5F4F0,0xFF171A21)) {
                Row(Modifier.padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    EventTab("Текущие",!history,!busy,onCurrent,count=currentCount)
                    EventTab("История",history,!busy,onHistory,icon="history")
                }
            }
        }
        HorizontalDivider()
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it,Modifier.padding(horizontal=22.dp,vertical=10.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
        LazyColumn(Modifier.weight(1f,fill=false).testTag("onlineOrdersScreen"),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(orders.isEmpty() && !loading && error==null) item {Text(if(history) "История заказов пуста" else "Новых заказов нет",Modifier.fillMaxWidth().padding(vertical=32.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(orders,key={it.getString("id")}) {order->
                Surface(onClick={onSelect(order)},enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("online:${order.getString("id")}"),
                    shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
                    if(history) {
                        Row(Modifier.padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Text(onlineNumber(order),fontSize=15.sp,fontWeight=FontWeight.Bold);Text(onlineText(order,"customer_name").ifBlank {"Клиент"},fontSize=14.sp)}
                                Text(onlineDate(order)+" · "+onlineFulfilmentLabel(order).first,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OnlineOrderStatus(order)
                            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(6.dp)) {EventPrice(order,17);MposPosIcon("next",Modifier.size(16.dp))}
                        }
                    } else Row(Modifier.padding(17.dp).height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(15.dp)) {
                        Column(Modifier.weight(1f)) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(onlineNumber(order),fontSize=17.sp,fontWeight=FontWeight.ExtraBold)
                                Text(onlineText(order,"customer_name").ifBlank {"Клиент"},fontSize=14.sp,fontWeight=FontWeight.Medium)
                            }
                            Text(onlineDate(order),Modifier.padding(top=8.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            val rows=order.optJSONArray("order_items")
                            Text((0 until (rows?.length() ?: 0)).joinToString(" · ") {i->rows!!.getJSONObject(i).let {"${onlineText(it,"product_name")} × ${it.opt("quantity")}"}},Modifier.padding(top=7.dp,bottom=12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {OnlineFulfilment(order);OnlineOrderStatus(order)}
                        }
                        Column(Modifier.fillMaxHeight(),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.SpaceBetween) {
                            EventPrice(order,22)
                            Surface(shape=RoundedCornerShape(11.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                                Row(Modifier.padding(horizontal=11.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {Text("Открыть заказ",fontSize=12.sp,fontWeight=FontWeight.SemiBold);MposPosIcon("next",Modifier.size(16.dp))}
                            }
                        }
                    }
                }
            }
            if(history && hasMore) item {MposPosControl("Показать 5 следующих",onMore,Modifier.fillMaxWidth(),!busy,PosControl.Outline,height=44.dp,radius=13.dp)}
        }
    }
}

private fun onlineNumber(order: JSONObject)="Заказ №"+onlineText(order,"external_id").ifBlank {onlineText(order,"id")}.removePrefix("№")

@Composable private fun EventsHeader(onClose: ()->Unit,onRefresh: ()->Unit,busy: Boolean,onBack: (() -> Unit)?=null) {
    Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(onBack!=null) EventControl("Назад",onBack,enabled=!busy)
        else {MposPosIcon("events",Modifier.size(22.dp));Text("События",fontSize=23.sp,fontWeight=FontWeight.Bold)}
        Spacer(Modifier.weight(1f))
        EventControl("",onRefresh,Modifier.size(44.dp).semantics {contentDescription="Обновить события"},!busy,icon="refresh")
        MposCloseButton(onClose,!busy,description="Закрыть события")
    }
}

@Composable private fun OnlineBadge(label: String,icon: String,color: androidx.compose.ui.graphics.Color,outlined: Boolean=false,minWidth: Int=102,fill: androidx.compose.ui.graphics.Color?=null) {
    Surface(shape=RoundedCornerShape(8.dp),color=if(outlined) MaterialTheme.colorScheme.surface else fill ?: color.copy(alpha=.12f),
        contentColor=color,border=if(outlined) BorderStroke(1.dp,MaterialTheme.colorScheme.outline) else null) {
        Row(Modifier.widthIn(min=minWidth.dp).height(29.dp).padding(horizontal=9.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
            MposPosIcon(icon,Modifier.size(16.dp),tint=color)
            Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable internal fun OnlineOrderStatus(order: JSONObject) {
    val status=order.optString("status")
    val paid=onlineText(order,"web_paid_at").isNotBlank()
    val label=when {status=="cancelled"->"Отменён";paid->"Оплачен";else->when(status) {"new"->"Новый";"accepted"->"Принят";"preparing"->"Готовится";"ready"->"Готов";else->status}}
    val dark=isMposDarkTheme()
    val color=when {status=="cancelled"->MaterialTheme.colorScheme.error;paid->if(dark) androidx.compose.ui.graphics.Color(0xFFC8ADEE) else androidx.compose.ui.graphics.Color(0xFF715099)
        status=="ready"->MaterialTheme.colorScheme.primary;status=="accepted"->if(dark) androidx.compose.ui.graphics.Color(0xFFA6C9F7) else androidx.compose.ui.graphics.Color(0xFF2E629C)
        else->if(dark) androidx.compose.ui.graphics.Color(0xFFEDCA7C) else androidx.compose.ui.graphics.Color(0xFF89600D)}
    val fill=when {status=="cancelled"->MaterialTheme.colorScheme.errorContainer;paid->eventColor(0xFFEEE6FA,0xFF3A304C);status=="ready"->MaterialTheme.colorScheme.primaryContainer;status=="accepted"->eventColor(0xFFE5EFFF,0xFF293A52);else->eventColor(0xFFFFF2CE,0xFF413721)}
    OnlineBadge(label,if(status=="cancelled") "close" else if(paid) "card" else if(status=="new") "events" else "check",color,fill=fill)
}

private fun onlineFulfilmentLabel(order: JSONObject): Pair<String,String> {
    val type=onlineText(order,"order_type").lowercase()
    return when(type) {"delivery","доставка"->"Доставка" to "delivery";"pickup","самовывоз","с собой","на вынос"->"С собой" to "takeaway";else->"На месте" to "here"}
}
@Composable private fun OnlineFulfilment(order: JSONObject,minWidth: Int=102) {
    val (label,icon)=onlineFulfilmentLabel(order)
    OnlineBadge(label,icon,MaterialTheme.colorScheme.onSurfaceVariant,true,minWidth)
}

@Composable private fun OnlinePayment(order: JSONObject) {
    val paid=onlineText(order,"web_paid_at").isNotBlank()
    val color=if(paid) {if(isMposDarkTheme()) androidx.compose.ui.graphics.Color(0xFFC8ADEE) else androidx.compose.ui.graphics.Color(0xFF715099)} else MaterialTheme.colorScheme.onPrimaryContainer
    // web_paid_at is also set by POS receipt settlement; it does not identify online payment.
    OnlineBadge(if(paid) "Оплачен" else "Наличными",if(paid) "check" else "cash",color,minWidth=140,fill=if(paid) eventColor(0xFFEEE6FA,0xFF3A304C) else MaterialTheme.colorScheme.primaryContainer)
}

@Composable private fun OnlineContact(icon: String,text: String) {
    if(text.isNotBlank()) Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Top) {
        MposPosIcon(icon,Modifier.padding(top=2.dp).size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text,Modifier.weight(1f),fontSize=13.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun OnlineOrderWindow(order: JSONObject,busy: Boolean,message: String?,onBack: ()->Unit,canDelete: Boolean=false,onDelete: ()->Unit={},readOnly: Boolean=false,
    onRefresh: ()->Unit={},onClose: ()->Unit=onBack,onAccept: (String)->Unit) {
    var minutes by remember(order.getString("id")) {mutableStateOf("")}
    var preset by remember(order.getString("id")) {mutableStateOf<Int?>(null)}
    val chosen=preset ?: minutes.toIntOrNull()
    val valid=chosen!=null && chosen in 1..1440
    MposDialog("",onClose,closable=!busy,maxWidth=940.dp,contentPadding=0.dp,spacing=0.dp,header={EventsHeader(onClose,onRefresh,busy,onBack)}) {
        HorizontalDivider()
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(onlineNumber(order),fontSize=21.sp,fontWeight=FontWeight.Bold);OnlineOrderStatus(order)}
                    Text(onlineDate(order),Modifier.padding(top=5.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {OnlineFulfilment(order,140);OnlinePayment(order)}
            }
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("onlineOrderBody")) {
                val products: @Composable (Modifier)->Unit={modifier->OnlineSection("Состав заказа",modifier,spacing=0.dp) {
                    val items=order.getJSONArray("order_items")
                    repeat(items.length()) {index->val item=items.getJSONObject(index)
                        Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(onlineText(item,"product_name"),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                                Text("${item.get("quantity")} × ${onlineMoney(item.opt("price"))}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                onlineText(item,"comment").takeIf {it.isNotBlank()}?.let {Text(it,fontSize=12.sp)}
                            }
                            Text(onlineMoney(BigDecimal(item.get("price").toString())*BigDecimal(item.get("quantity").toString())).removeSuffix(" BYN"),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                        }
                        if(index<items.length()-1) HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                    }
                    if(order.opt("delivery_fee")?.toString()?.toBigDecimalOrNull()?.signum()==1) OnlineAmount("Доставка",onlineMoney(order.opt("delivery_fee")))
                    HorizontalDivider();OnlineAmount("Итого",onlineMoney(order.opt("total")),true)
                }}
                val details: @Composable (Modifier)->Unit={modifier->Column(modifier,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    OnlineSection("Клиент",spacing=0.dp) {
                        OnlineContact("customer",onlineText(order,"customer_name").ifBlank {"Клиент"})
                        OnlineContact("phone",onlineText(order,"phone"))
                        OnlineContact("location",onlineText(order,"address"))
                        OnlineContact("comment",onlineText(order,"comment"))
                    }
                    if(!readOnly) OnlineSection("Когда будет готов?") {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(5,15,30,40).forEach {value->EventControl("$value мин",{preset=value;minutes=""},Modifier.weight(1f).semantics {selected=preset==value},!busy,active=preset==value)}
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf(60 to "1 час",120 to "2 часа").forEach {(value,label)->EventControl(label,{preset=value;minutes=""},Modifier.weight(1f).semantics {selected=preset==value},!busy,active=preset==value)}
                        }
                        }
                        EventMinutes(minutes,{if(it.length<=4 && it.all(Char::isDigit)) {minutes=it;preset=null}},!busy)
                    }
                }}
                if(maxWidth>736.dp) Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {products(Modifier.weight(1.2f));details(Modifier.weight(1f))}
                else Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {products(Modifier.fillMaxWidth());details(Modifier.fillMaxWidth())}
            }
            message?.let {Text(it,fontSize=13.sp,color=MaterialTheme.colorScheme.error)}
            if(!readOnly) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(canDelete) MposPosControl("",onDelete,Modifier.size(48.dp).testTag("onlineDelete").semantics {contentDescription="Удалить заказ"},!busy,PosControl.Delete,48.dp,13.dp,icon="delete")
                EventControl("Взять в работу",{com.mendelev.mpos.backend.PreparationEstimate.encode(chosen)?.let(onAccept)},Modifier.weight(1f).testTag("onlineAccept"),!busy && valid,primary=true)
            }
        }
    }
}

@Composable private fun OnlineSection(title: String,modifier: Modifier=Modifier,accessory: (@Composable ()->Unit)?=null,danger: Boolean=false,spacing: androidx.compose.ui.unit.Dp=12.dp,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.Bold,color=if(danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                accessory?.invoke()
            }
            Column(verticalArrangement=Arrangement.spacedBy(spacing),content=content)
        }
    }
}
@Composable private fun OnlineAmount(label: String,value: String,total: Boolean=false) {
    Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f),fontSize=if(total) 18.sp else 13.sp,fontWeight=if(total) FontWeight.ExtraBold else FontWeight.Normal,color=if(total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,fontSize=if(total) 18.sp else 13.sp,fontWeight=FontWeight.Bold,color=if(total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun EventTab(label: String,active: Boolean,enabled: Boolean,onClick: ()->Unit,count: Int=0,icon: String?=null) {
    val c=MaterialTheme.colorScheme
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(11.dp),color=if(active) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,contentColor=if(active) c.onPrimaryContainer else c.onSurfaceVariant) {
            Row(Modifier.heightIn(min=38.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                icon?.let {MposPosIcon(it,Modifier.size(16.dp))}
                Text(label,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                if(count>0) MposCountBadge(count,MposCountTone.Events)
            }
        }
    }
}

@Composable private fun EventPrice(order: JSONObject,size: Int) {
    Row(verticalAlignment=Alignment.Bottom) {
        Text(onlineMoney(order.opt("total")).removeSuffix(" BYN"),fontSize=size.sp,fontWeight=FontWeight.ExtraBold)
        Text(" BYN",Modifier.padding(bottom=2.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
