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

@Composable fun OnlineOrdersScreen(onWorkspace: ()->Unit) {
    val context=LocalContext.current;val runtime=remember {BackendRuntime.get(context)}
    val orders by runtime.orders.collectAsState();val busy by runtime.busy.collectAsState()
    var selected by remember {mutableStateOf<JSONObject?>(null)}
    var selectedHistory by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val directory=remember(runtime,scope) {EventsDirectory(scope,runtime::refreshInbox,runtime::orderHistory)}
    val page by directory.state.collectAsState()
    val connection by runtime.connection.collectAsState()
    LaunchedEffect(connection) {directory.reset();selected=null;message=null}
    val admin by com.mendelev.mpos.core.access.Administration.session.state.collectAsState()
    var deleting by remember {mutableStateOf(false)}
    val detail=selected?.let {chosen->(if(selectedHistory) page.rows else orders).firstOrNull {it.optString("id")==chosen.optString("id")} ?: chosen}
    EventsTheme {
    if(detail==null) OnlineEventsWindow(if(page.history) page.rows else orders,busy || page.loading,
        {selected=it;selectedHistory=page.history;message=null},onWorkspace,
        history=page.history,loading=page.loading,error=page.error,hasMore=page.cursor!=null,
        onCurrent=directory::current,onHistory=directory::history,onRefresh=directory::refresh,onMore=directory::more,currentCount=orders.size)
    else OnlineOrderWindow(detail,busy || deleting || page.loading,message ?: page.error,{selected=null;message=null},canDelete=admin.active && !selectedHistory,readOnly=selectedHistory,onRefresh=directory::refresh,onClose=onWorkspace,onDelete={
        val generation=admin.generation
        deleting=true;message=null
        scope.launch {
            try {runtime.deleteNewOrder(detail,generation);selected=null}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {message=e.message ?: "Не удалось удалить заказ. Повторите действие"}
            finally {deleting=false}
        }
    }) {estimate->
        scope.launch {
            try {runtime.accept(detail,estimate);onWorkspace()}
            catch(e: kotlinx.coroutines.CancellationException) {throw e}
            catch(e:Exception) {message=e.message ?: "Не удалось принять заказ"}
        }
    }
}
}

@Composable internal fun OnlineReadyAction(record: OnlineAcceptance,runtime: BackendRuntime,compact: Boolean=false,enabled: Boolean=true) {
    val scope=rememberCoroutineScope()
    var confirm by remember(record.sessionId) {mutableStateOf(false)}
    var pending by remember(record.sessionId) {mutableStateOf(false)}
    var error by remember(record.sessionId) {mutableStateOf<String?>(null)}
    if(record.cancelPending || record.conflict.isNotEmpty()) return
    if(record.readyConfirmed) {if(compact) OnlineReadyControl(true,true,false,false,false,{});return}
    val retry=!record.accepted || record.ready
    OnlineReadyControl(record.readyConfirmed,compact,retry,pending,enabled,{confirm=true},Modifier.testTag("onlineReady:${record.sessionId}"))
    if(confirm) MposDialog(if(retry) "Подтверждение заказа" else "Подтверждение",{if(!pending) confirm=false},closable=!pending,maxWidth=520.dp) {
        Text(if(retry) "Повторить отправку сохранённого состояния заказа на сервер?" else "Подтвердить готовность заказа?")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",{confirm=false},Modifier.weight(1f),enabled=!pending,style=ActionStyle.Secondary)
            MposButton("Подтвердить",{pending=true;scope.launch {
                try {if(retry) runtime.retryAcceptance(record.sessionId) else runtime.ready(record.sessionId);confirm=false}
                catch(e:kotlinx.coroutines.CancellationException) {throw e}
                catch(e:Exception) {error=e.message ?: "Не удалось подтвердить заказ"}
                finally {pending=false}
            }},Modifier.weight(1f).testTag("onlineReadyConfirm"),enabled=!pending,busy=pending)
        }
    }
}

/** Shared presentation only; all network commands remain in OnlineReadyAction. */
@Composable internal fun OnlineReadyControl(confirmed: Boolean,compact: Boolean,retry: Boolean,pending: Boolean,enabled: Boolean,
    onClick: ()->Unit,modifier: Modifier=Modifier) {
    if(confirmed && !compact) return
    val label=if(confirmed) "✓ Готов" else if(retry) "Подтвердить" else if(compact) "Готов" else "Заказ готов"
    if(compact) ParkedReadyBadge(label,enabled && !pending && !confirmed,onClick,modifier)
    else PosAction(label,onClick,modifier.fillMaxWidth(),enabled=enabled && !pending,tone=PosTone.Primary,footer=true,retainingAppearance=pending)
}

@Composable internal fun CurrentOnlineOrder(session: String,busy: Boolean=false) {
    val runtime=BackendRuntime.get(LocalContext.current);val records by runtime.accepted.collectAsState()
    records.singleOrNull {it.sessionId==session && (!it.settled || !it.readyConfirmed) && !it.cancelled}?.let {key(it.sessionId) {OnlineReadyAction(it,runtime,enabled=!busy)}}
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
