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
    val status by runtime.status.collectAsState();val pending by runtime.pending.collectAsState()
    // Credentials deliberately never enter savedInstanceState / rememberSaveable.
    var form by remember {mutableStateOf<TelegramSettings?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(true)}
    var visible by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun load() {busy=true;scope.launch {try {val s=withContext(Dispatchers.IO) {store.read()};form=s;error=null} catch(_:Exception) {error="Не удалось прочитать настройки Telegram. Данные не изменены"} finally {busy=false}}}
    LaunchedEffect(Unit) {runtime.clearStatus();load()}
    val locked=busy || pending>0
    MposDialog("Telegram",onClose,closable=!busy,maxWidth=680.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("telegramSettings"),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {MposNotice(it,true)}
            if(form==null && !busy) MposButton("Повторить чтение",{load()})
            form?.let {s->
                TelegramSettingsSection("Подключение") {
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {MposField(s.botToken,{form=s.copy(botToken=it)},"Токен бота",Modifier.testTag("telegramToken"),!locked,password=!visible)}
                        MposButton(if(visible) "Скрыть" else "Показать",{visible=!visible},Modifier.width(108.dp).height(56.dp),enabled=!locked,style=ActionStyle.Outline)
                    }
                    TelegramRecipientFields(s,!locked) {form=it}
                }
                TelegramSettingsSection("Уведомления") {
                    TelegramToggle("Включить Telegram","Разрешить отправку выбранных уведомлений.",s.enabled,!locked) {form=s.copy(enabled=it)}
                    TelegramToggle("Смена открыта","Сообщение после открытия смены.",s.shiftOpened,!locked) {form=s.copy(shiftOpened=it)}
                    TelegramToggle("Смена закрыта","Изображение чека-отчёта после закрытия смены.",s.shiftClosed,!locked) {form=s.copy(shiftClosed=it)}
                    TelegramToggle("Уведомления об онлайн-заказах","Backend отправляет сообщение на ID рабочего устройства.",s.onlineOrders,!locked) {form=s.copy(onlineOrders=it,onlineOrdersConfigured=true)}
                }
                TelegramSettingsSection("Отчёты владельцу") {
                    TelegramToggle("Отчёт за смену","PDF после каждого закрытия смены.",s.ownerShiftStats,!locked,"telegramOwnerShift") {form=s.copy(ownerShiftStats=it)}
                    TelegramToggle("Отправлять отчёт за месяц","С первого по последнее число. Пропущенный отчёт — при следующем открытии смены.",s.ownerMonthReport,!locked,"telegramOwnerMonth") {form=s.copy(ownerMonthReport=it)}
                    TelegramToggle("Отправлять отчёт за квартал","Календарный квартал. Пропущенный отчёт — при следующем открытии смены.",s.ownerQuarterReport,!locked,"telegramOwnerQuarter") {form=s.copy(ownerQuarterReport=it)}
                }

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
                    try {val saved=withContext(Dispatchers.IO) {store.save(s)};form=saved;onSaved(saved);runtime.configurationSaved();onClose()}
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

@Composable private fun TelegramSettingsSection(title: String,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
        content()
    }
}

@Composable private fun TelegramRecipientFields(s: TelegramSettings,enabled: Boolean,onChange: (TelegramSettings)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide=maxWidth>=500.dp
        val labels=listOf("ID рабочей группы","ID темы · необязательно","ID рабочего устройства","ID владельца")
        val values=listOf(s.chatId,s.threadId,s.deviceChatId,s.ownerChatId)
        val tags=listOf("telegramChat","telegramThread","telegramDevice","telegramOwner")
        @Composable fun field(index: Int,modifier: Modifier) {
            MposField(values[index],{value->onChange(when(index) {
                0->s.copy(chatId=value);1->s.copy(threadId=value);2->s.copy(deviceChatId=value);else->s.copy(ownerChatId=value)
            })},labels[index],modifier.testTag(tags[index]),enabled)
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(wide) listOf(0 to 1,2 to 3).forEach {(left,right)->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    field(left,Modifier.weight(1f));field(right,Modifier.weight(1f))
                }
            } else (0..3).forEach {field(it,Modifier.fillMaxWidth())}
        }
    }
}
