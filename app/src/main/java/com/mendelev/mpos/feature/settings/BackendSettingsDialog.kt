package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import kotlinx.coroutines.*

@Composable internal fun BackendSettingsDialog(onClose: ()->Unit,onStatus: (String)->Unit) {
    val context=LocalContext.current
    val generation=remember {Administration.session.state.value.generation}
    val access=remember {BackendAccess(context,generation)}
    val scope=rememberCoroutineScope()
    var form by remember {mutableStateOf<BackendSettings?>(null)}
    var saved by remember {mutableStateOf<BackendSettings?>(null)}
    var busy by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    var status by remember {mutableStateOf<String?>(null)}
    var visible by remember {mutableStateOf(false)}
    var advanced by remember {mutableStateOf(false)}
    var needsRebind by remember {mutableStateOf(false)}
    var confirmTarget by remember {mutableStateOf(false)}
    fun load() {busy=true;scope.launch {try {val s=withContext(Dispatchers.IO) {access.requireSession();access.store.read().also {current->needsRebind=com.mendelev.mpos.data.local.OnlineOrderRepository(com.mendelev.mpos.data.local.CatalogDatabase.get(context)).records().any {it.target==current.target() && it.dataset!=com.mendelev.mpos.data.recovery.AppDataset.paths(context).id}}};form=s;saved=s;error=null} catch(_:Exception) {error="Не удалось прочитать подключение. Данные не изменены"} finally {busy=false}}}
    LaunchedEffect(Unit) {load()}
    fun save() {
        val draft=form ?: return
        busy=true;error=null;status=null;confirmTarget=false
        scope.launch {try {val next=withContext(Dispatchers.IO) {access.store.save(draft.copy(datasetId=com.mendelev.mpos.data.recovery.AppDataset.paths(context).id)).also {next->com.mendelev.mpos.data.local.OnlineOrderRepository(com.mendelev.mpos.data.local.CatalogDatabase.get(context)).rebind(next.target(),next.datasetId,generation)}};form=next;saved=next;needsRebind=false;status="Настройки сохранены";onStatus("Подключение сохранено. Связь ещё не проверена")}
            catch(e:Exception) {error=if(e is IllegalArgumentException || e is IllegalStateException) e.message else "Не удалось сохранить подключение"} finally {busy=false}}
    }
    fun check(events: Boolean) {
        val expected=saved ?: return
        busy=true;error=null;status="Проверяем подключение…"
        scope.launch {try {withContext(Dispatchers.IO) {val current=access.current(expected);val http=BackendHttp();http.check(current);if(events) {access.current(expected);http.checkEvents(current)}}
            status=if(events) "Канал заказов работает. Заказы не создавались" else "Связь с backend работает. Для проверки ключа проверьте канал заказов";onStatus(status!!)}
            catch(e:Exception) {status=null;error=if(e is BackendFailure || e is IllegalStateException) e.message else "Не удалось проверить подключение";onStatus(error!!)} finally {busy=false}}
    }
    MposDialog("Меню и backend",onClose,closable=!busy,maxWidth=720.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("networkConfig:backend"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {MposNotice(it,true)}
            status?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            if(form==null && !busy) MposButton("Повторить чтение",{load()})
            form?.let {s->
                MposWebField(s.url,{form=s.copy(url=it);status=null},"Адрес backend","backendUrl",!busy,placeholder="https://pos.example.org")
                MposWebField(s.deviceName,{form=s.copy(deviceName=it)},"Название устройства","backendName",!busy)
                MposField(s.deviceKey,{form=s.copy(deviceKey=it)},"Ключ устройства",Modifier.testTag("backendKey"),!busy,password=!visible)
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton(if(visible) "Скрыть ключ" else "Показать ключ",{visible=!visible},enabled=!busy,style=ActionStyle.Outline)
                    MposButton("Новый ключ",{form=s.copy(deviceKey=java.util.UUID.randomUUID().toString())},enabled=!busy,style=ActionStyle.Outline)
                }
                Text("Протокол M-POS. Новый ключ регистрируется при ручной публикации каталога. Для разных организаций нужны отдельные backend и базы.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Получать онлайн-заказы",Modifier.weight(1f))
                    Switch(s.receiveOrders,{form=s.copy(receiveOrders=it)},enabled=!busy)
                }
                MposButton(if(advanced) "Скрыть параметры API" else "Параметры API",{advanced=!advanced},enabled=!busy,style=ActionStyle.Outline)
                if(advanced) {
                    MposWebField(s.healthPath,{form=s.copy(healthPath=it)},"Проверка сервера","backendHealth",!busy)
                    MposWebField(s.statePath,{form=s.copy(statePath=it)},"Проверка ключа","backendState",!busy)
                    MposWebField(s.eventsPath,{form=s.copy(eventsPath=it)},"Канал заказов SSE","backendEvents",!busy)
                    MposWebField(s.operationalPath,{form=s.copy(operationalPath=it)},"Состояние загрузки","backendOperational",!busy)
                    MposWebField(s.heartbeatPath,{form=s.copy(heartbeatPath=it)},"Активность планшета","backendHeartbeat",!busy)
                    MposWebField(s.ordersPath,{form=s.copy(ordersPath=it)},"Действия с заказами","backendOrders",!busy)
                    MposWebField(s.menuPath,{form=s.copy(menuPath=it)},"Каталог","backendMenu",!busy)
                    MposWebField(s.mediaPath,{form=s.copy(mediaPath=it)},"Фотографии","backendMedia",!busy)
                    MposWebField(s.tariffsPath,{form=s.copy(tariffsPath=it)},"Тарифы доставки","backendTariffs",!busy)
                    MposWebField(s.availabilityPath,{form=s.copy(availabilityPath=it)},"Доступность товаров","backendAvailability",!busy)
                    MposWebField(s.timeoutSeconds.toString(),{it.toIntOrNull()?.let {n->form=s.copy(timeoutSeconds=n)}},"Ожидание ответа, секунд","backendTimeout",!busy)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Проверить связь",{check(false)},Modifier.weight(1f).testTag("backendCheck"),enabled=!busy && s==saved && s.url.isNotBlank(),style=ActionStyle.Outline)
                    MposButton("Проверить канал заказов",{check(true)},Modifier.weight(1f),enabled=!busy && s==saved && s.url.isNotBlank(),style=ActionStyle.Outline)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Закрыть",onClose,Modifier.weight(1f),enabled=!busy,style=ActionStyle.Outline)
            MposButton("Сохранить",{
                val old=saved
                val next=try {form?.validated()} catch(e:IllegalArgumentException) {error=e.message;null}
                if(next!=null) {
                    if(old!=null && old.url.isNotBlank() && (!old.sameTarget(next) || old.datasetId!=com.mendelev.mpos.data.recovery.AppDataset.paths(context).id || needsRebind)) confirmTarget=true else save()
                }
            },Modifier.weight(1f).testTag("backendSave"),enabled=!busy && form!=null)
        }
    }
    if(confirmTarget) MposDialog("Изменить подключение?",{confirmTarget=false},maxWidth=560.dp) {
        Text("Изменятся адрес backend или ключ устройства. Следующая ручная синхронизация опубликует каталог на новом подключении. Старые данные на сервере сохранятся. После восстановления копии будут разрешены подтверждения только тех заказов этого backend, которые присутствуют в восстановленной базе.")
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Отмена",{confirmTarget=false},Modifier.weight(1f),style=ActionStyle.Outline)
            MposButton("Изменить",{save()},Modifier.weight(1f))
        }
    }
}
