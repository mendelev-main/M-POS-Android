package com.mendelev.mpos.feature.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.domain.settings.SettingKind
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.WorkspaceSnapshot
import com.mendelev.mpos.domain.workspace.*

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
    val presets by com.mendelev.mpos.feature.settings.settingsCatalogModel().state.collectAsStateWithLifecycle()
    var label by rememberSaveable(mode) {mutableStateOf(s.cart.details.label)}
    val revision=rememberSaveable(mode) {s.cartRevision}
    val owner=rememberSaveable(mode) {s.cart.sessionId}
    fun close() {onMode(null);model.dismiss()}
    fun choose(order: ParkedOrder,delete: Boolean) {
        model.dismiss();target=order.id;targetRevision=order.revision;targetLabel=order.cart.details.label
        requestRevision=s.cartRevision;requestOwner=s.cart.sessionId;requestHasLines=s.cart.lines.isNotEmpty()
        if(delete) onMode("delete")
        else if(s.cart.lines.isEmpty() && s.cart.details==OrderDetails()) model.resume(order.id,order.revision,s.cartRevision,s.cart.sessionId) {close()}
        else if(s.cart.lines.isNotEmpty() && s.cart.details.label.isNotBlank())
            model.resume(order.id,order.revision,s.cartRevision,s.cart.sessionId,s.cart.details.label) {close()}
        else onMode("resume")
    }
    if(mode=="list") {
        ParkedOrdersWindow(s.parked,busy,ui.message,{order->order.cart.lines.any {CartEngine.stale(it,s.products)}},
            {close()},{choose(it,false)},{choose(it,true)})
        return
    }
    if(mode=="details") {
        OrderSettingsWindow(s.cart.details,s.sourceDelivery,presets.catalog.rows(SettingKind.DELIVERY),presets.catalog.revision,
            runCatching {orderSettingsTotal(s.cart,s.sourceDiscounts,s.sourceDelivery)}.getOrNull(),busy,ui.message,s.cart.sessionId,s.cartRevision,{close()},
            {next,done->model.details(next,s.cartRevision,s.cart.sessionId,done)})
        return
    }
    MposDialog(when(mode) {"details" -> "Параметры заказа";"park" -> "Отложить заказ";"resume" -> "Открыть отложенный заказ?";"delete" -> "Удалить отложенный заказ?";else -> "Отложенные заказы"},
        {if(!busy) close()},!busy) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        when(mode) {
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
                    if(mode=="park") FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        presets.catalog.rows(SettingKind.RECEIPT_LABEL).forEach {template->
                            MposButton(template.name,{label=OrderRules.uniqueLabel(template.name,s.parked.map {it.cart.details.label});model.dismiss()},enabled=!busy,style=ActionStyle.Secondary,modifier=Modifier.testTag("receiptTemplate:${template.id}"))
                        }
                    }
                    MposField(label,{label=it;model.dismiss()},"Подпись заказа",Modifier.testTag("orderLabel"),!busy)
                    Text("Заказ сохранится в отложенных, а текущая корзина станет пустой")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Отмена",{close()},enabled=!busy,style=ActionStyle.Secondary)
                    MposButton("Отложить заказ",{
                        model.park(label,revision,owner) {close()}
                    },busy=busy,modifier=Modifier.testTag("orderSubmit"))
                }
            }
        }
    }
}
