package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.telegram.*
import kotlinx.coroutines.*

@Composable internal fun TelegramSettingsDialog(onClose: ()->Unit,onSaved: (TelegramSettings)->Unit) {
    val context=LocalContext.current;val runtime=remember {TelegramRuntime.get(context)}
    val generation=remember {Administration.session.state.value.generation}
    val store=remember {runtime.settingsStore(generation)}
    val ownerPreview by runtime.ownerPreview.collectAsState()
    ownerPreview?.let {OwnerAnalyticsPreview(it) {runtime.closeOwnerPreview()}}
    val status by runtime.status.collectAsState();val pending by runtime.pending.collectAsState()
    var original by remember {mutableStateOf<TelegramSettings?>(null)}
    // Credentials deliberately never enter savedInstanceState / rememberSaveable.
    var form by remember {mutableStateOf<TelegramSettings?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(true)}
    var visible by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun load() {busy=true;scope.launch {try {val s=withContext(Dispatchers.IO) {store.read()};original=s;form=s;error=null} catch(_:Exception) {error="Не удалось прочитать настройки Telegram. Данные не изменены"} finally {busy=false}}}
    LaunchedEffect(Unit) {runtime.clearStatus();load()}
    val locked=busy || pending>0
    MposDialog("Telegram",onClose,closable=!busy,maxWidth=680.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("telegramSettings"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {MposNotice(it,true)}
            if(form==null && !busy) MposButton("Повторить чтение",{load()})
            form?.let {s->
                Text("Рабочий бот отправляет отчёты в группу. Укажите своего бота и чат для этой организации.",style=MaterialTheme.typography.bodySmall)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {MposField(s.botToken,{form=s.copy(botToken=it)},"Токен бота",Modifier.testTag("telegramToken"),!locked,password=!visible)}
                    MposButton(if(visible) "Скрыть" else "Показать",{visible=!visible},enabled=!locked,style=ActionStyle.Outline)
                }
                MposWebField(s.chatId,{form=s.copy(chatId=it)},"ID рабочей группы","telegramChat",!locked,placeholder="Например: -1001234567890")
                MposWebField(s.threadId,{form=s.copy(threadId=it)},"ID темы · необязательно","telegramThread",!locked,placeholder="Например: 123")
                TelegramToggle("Включить Telegram","Разрешить отправку выбранных уведомлений.",s.enabled,!locked) {form=s.copy(enabled=it)}
                TelegramToggle("Смена открыта","Сообщение после открытия смены.",s.shiftOpened,!locked) {form=s.copy(shiftOpened=it)}
                TelegramToggle("Смена закрыта","Изображение чека-отчёта после закрытия смены.",s.shiftClosed,!locked) {form=s.copy(shiftClosed=it)}
                TelegramToggle("Ежемесячный складской отчёт","PDF за предыдущий календарный месяц; отправляется при открытом приложении и административном доступе.",s.monthlyWarehouse,!locked) {form=s.copy(monthlyWarehouse=it)}
                MposWebField(s.deviceChatId,{form=s.copy(deviceChatId=it)},"ID рабочего устройства","telegramDevice",!locked,placeholder="Telegram ID рабочего устройства")
                Text("Аккаунт рабочего устройства должен открыть бота и нажать Start.",style=MaterialTheme.typography.bodySmall)
                MposWebField(s.ownerChatId,{form=s.copy(ownerChatId=it)},"ID владельца","telegramOwner",!locked,placeholder="Telegram ID владельца")
                TelegramToggle("Отправка статистики владельцу","Подробные изображения за смену отправляются в личный чат после каждого закрытия.",s.ownerShiftStats,!locked,"telegramOwnerShift") {form=s.copy(ownerShiftStats=it)}
                TelegramToggle("Месячная статистика владельцу","В последний день месяца — после каждого закрытия, с итогами месяца на этот момент.",s.ownerMonthlyStats,!locked,"telegramOwnerMonth") {form=s.copy(ownerMonthlyStats=it)}
                Text("Владелец должен открыть бота и нажать Start. Статистика не отправляется в рабочую группу.",style=MaterialTheme.typography.bodySmall)
                TelegramToggle("Уведомления об онлайн-заказах через сервер","Серверные Telegram-уведомления и команды владельца подключим отдельно.",s.onlineOrders,false) {}
                Text("Адрес и ключ backend задаются в модуле «Меню и backend».",style=MaterialTheme.typography.bodySmall)
                if(original?.enabled==true) {
                    Text("Отправка по сохранённым настройкам",style=MaterialTheme.typography.titleSmall)
                    MposButton("Отчёт последней закрытой смены",{runtime.sendLatestShift()},enabled=!locked && s==original,style=ActionStyle.Outline)
                    MposButton("Складской PDF за прошлый месяц",{runtime.sendMonthly()},enabled=!locked && s==original && s.monthlyWarehouse,style=ActionStyle.Outline)
                    if(s!=original) Text("Сохраните изменения перед отправкой отчётов.",style=MaterialTheme.typography.bodySmall)
                }
                Text("Предпросмотр без отправки в Telegram",style=MaterialTheme.typography.titleSmall)
                MposButton("Предпросмотр статистики смены",{runtime.previewLatestOwner()},enabled=!locked,style=ActionStyle.Outline,modifier=Modifier.testTag("ownerStatsPreview"))
                MposButton("Предпросмотр статистики месяца",{runtime.previewLatestOwner(month=true)},enabled=!locked,style=ActionStyle.Outline)
                if(original?.enabled==true && (s.ownerShiftStats || s.ownerMonthlyStats)) {
                    MposButton("Повторить статистику владельцу",{runtime.resendLatestOwner()},enabled=!locked && s==original,style=ActionStyle.Outline)
                }
                Text("Проверка отправляет одно тестовое сообщение в выбранную группу. После обрыва связи проверьте чат перед повтором.",style=MaterialTheme.typography.bodySmall)
            }
            status?.let {Text(it,Modifier.testTag("telegramStatus"),style=MaterialTheme.typography.bodySmall)}
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MposButton("Закрыть",onClose,Modifier.weight(1f),enabled=!busy,style=ActionStyle.Outline)
            MposButton("Проверить",{try {val s=form!!.validated(test=true);error=null;runtime.test(s,generation)} catch(e:IllegalArgumentException) {error=e.message}},Modifier.weight(1f).testTag("telegramTest"),enabled=!locked && form!=null,style=ActionStyle.Outline)
            MposButton("Сохранить",{
                val s=try {form!!.validated()} catch(e:IllegalArgumentException) {error=e.message;return@MposButton}
                busy=true;error=null
                scope.launch {
                    try {val saved=withContext(Dispatchers.IO) {store.save(s)};original=saved;form=saved;onSaved(saved);runtime.configurationSaved();onClose()}
                    catch(e:IllegalStateException) {error=e.message ?: "Не удалось сохранить настройки Telegram"}
                    catch(_:Exception) {error="Не удалось сохранить настройки Telegram. Данные не изменены"}
                    finally {busy=false}
                }
            },Modifier.weight(1f).testTag("telegramSave"),enabled=!locked && form!=null)
        }
    }
}
@Composable private fun TelegramToggle(title: String,note: String,value: Boolean,enabled: Boolean,tag: String="",onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title);Text(note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Switch(value,onChange,enabled=enabled,modifier=if(tag.isBlank()) Modifier else Modifier.testTag(tag))
    }
}
