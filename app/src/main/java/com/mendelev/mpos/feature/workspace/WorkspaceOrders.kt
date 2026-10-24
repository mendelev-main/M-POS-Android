package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.workspace.*
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun OrderButtons(pending: Int,s: WorkspaceSnapshot,onDetails: ()->Unit,onPark: ()->Unit,onParked: ()->Unit,onClear: ()->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        MposButton("Параметры",onDetails,enabled=pending==0,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceDetails"))
        MposButton("Отложить",onPark,enabled=pending==0 && s.cart.lines.isNotEmpty(),style=ActionStyle.Secondary,modifier=Modifier.testTag("workspacePark"))
        MposButton("Отложенные (${s.parked.size})",onParked,enabled=pending==0,style=ActionStyle.Secondary,modifier=Modifier.testTag("workspaceParked"))
        if(s.cart.lines.isNotEmpty() || s.cart.details!=OrderDetails()) MposButton("Очистить",onClear,enabled=pending==0,style=ActionStyle.Secondary)
    }
}

@Composable fun WorkspaceOrdersDialogs(mode: String?,onMode: (String?)->Unit,s: WorkspaceSnapshot,ui: WorkspaceUi,model: WorkspaceViewModel) {
    var target by rememberSaveable {mutableStateOf("")}
    var targetRevision by rememberSaveable {mutableStateOf(0L)}
    var targetLabel by rememberSaveable {mutableStateOf("")}
    var requestRevision by rememberSaveable {mutableStateOf(0L)}
    var requestOwner by rememberSaveable {mutableStateOf("")}
    var requestHasLines by rememberSaveable {mutableStateOf(false)}
    val busy=ui.pending>0
    if(mode==null) return
    var label by rememberSaveable(mode) {mutableStateOf(s.cart.details.label)}
    var type by rememberSaveable(mode) {mutableStateOf(s.cart.details.type)}
    var comment by rememberSaveable(mode) {mutableStateOf(s.cart.details.comment)}
    var address by rememberSaveable(mode) {mutableStateOf(s.cart.details.address)}
    var phone by rememberSaveable(mode) {mutableStateOf(s.cart.details.phone)}
    val revision=rememberSaveable(mode) {s.cartRevision}
    val owner=rememberSaveable(mode) {s.cart.sessionId}
    fun close() {onMode(null);model.dismiss()}
    fun choose(order: ParkedOrder,delete: Boolean) {
        model.dismiss();target=order.id;targetRevision=order.revision;targetLabel=order.cart.details.label
        requestRevision=s.cartRevision;requestOwner=s.cart.sessionId;requestHasLines=s.cart.lines.isNotEmpty()
        if(delete) onMode("delete")
        else if(s.cart.lines.isEmpty() && s.cart.details==OrderDetails()) model.resume(order.id,order.revision,s.cartRevision,s.cart.sessionId) {close()}
        else onMode("resume")
    }
    MposDialog(when(mode) {"details" -> "Параметры заказа";"park" -> "Отложить заказ";"resume" -> "Открыть отложенный заказ?";"delete" -> "Удалить отложенный заказ?";else -> "Отложенные заказы"},
        {if(!busy) close()},!busy) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        when(mode) {
            "list" -> {
                LazyColumn(Modifier.heightIn(max=420.dp).testTag("parkedList"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(s.parked,key={it.id}) {order ->
                        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxWidth().testTag("parkedCard:${order.id}")) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(order.cart.details.label,style=MaterialTheme.typography.titleMedium)
                                Text(OrderRules.typeLabel(order.cart.details.type),style=MaterialTheme.typography.bodySmall)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,Locale.forLanguageTag("ru")).format(Date(order.parkedAt)),style=MaterialTheme.typography.bodySmall)
                                Text("${CatalogRules.canonical(CartEngine.total(order.cart)).replace('.',',')} BYN",style=MaterialTheme.typography.titleMedium)
                                if(order.cart.lines.any {CartEngine.stale(it,s.products)}) Text("Требуется проверка: каталог изменился",color=MaterialTheme.colorScheme.error)
                                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    MposButton("Открыть",{choose(order,false)},enabled=!busy,modifier=Modifier.testTag("parkedOpen:${order.id}"))
                                    MposButton("Удалить",{choose(order,true)},enabled=!busy,style=ActionStyle.Danger,modifier=Modifier.testTag("parkedDelete:${order.id}"))
                                }
                            }
                        }
                    }
                    if(s.parked.isEmpty()) item {Text("Нет отложенных заказов")}
                }
            }
            "delete" -> {
                Text("Удалить «$targetLabel»? Его резерв будет освобождён")
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{onMode("list");model.dismiss()},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton("Удалить заказ",{model.deleteParked(target,targetRevision){onMode("list")}},busy=busy,style=ActionStyle.Danger)
                }
            }
            "resume" -> {
                Text("Открываем: $targetLabel")
                if(requestHasLines) {
                    Text("Сначала отложим текущий заказ. Ни одна позиция не будет потеряна")
                    MposField(label,{label=it;model.dismiss()},"Подпись текущего заказа",Modifier.testTag("orderLabel"),!busy)
                } else Text("Введённые параметры пустого текущего заказа будут заменены параметрами выбранного заказа")
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{onMode("list");model.dismiss()},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton(if(requestHasLines) "Отложить и открыть" else "Открыть заказ",{
                        model.resume(target,targetRevision,requestRevision,requestOwner,label.takeIf {requestHasLines},!requestHasLines) {close()}
                    },busy=busy,modifier=Modifier.testTag("orderSubmit"))
                }
            }
            else -> {
                Column(Modifier.heightIn(max=350.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    MposField(label,{label=it;model.dismiss()},"Подпись заказа",Modifier.testTag("orderLabel"),!busy)
                    if(mode=="details") {
                        MposSelect("Тип заказа",type,listOf("here" to "На месте","takeaway" to "С собой","delivery" to "Доставка"),{
                            type=it;if(type!="delivery") {address="";phone=""};model.dismiss()
                        },!busy,Modifier.testTag("orderType"))
                        MposField(comment,{comment=it;model.dismiss()},"Комментарий",Modifier.testTag("orderComment"),!busy,singleLine=false)
                        if(type=="delivery") {
                            MposField(address,{address=it;model.dismiss()},"Адрес доставки",Modifier.testTag("orderAddress"),!busy)
                            MposField(phone,{phone=it;model.dismiss()},"Телефон доставки",Modifier.testTag("orderPhone"),!busy)
                        }
                    } else Text("Заказ сохранится в отложенных, а текущая корзина станет пустой")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{close()},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton(if(mode=="details") "Сохранить параметры" else "Отложить заказ",{
                        if(mode=="details") model.details(OrderDetails(label,type,comment,address,phone),revision,owner) {close()}
                        else model.park(label,revision,owner) {close()}
                    },busy=busy,modifier=Modifier.testTag("orderSubmit"))
                }
            }
        }
    }
}
