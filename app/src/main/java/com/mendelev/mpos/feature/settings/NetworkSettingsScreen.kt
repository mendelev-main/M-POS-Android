package com.mendelev.mpos.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.PrinterConfiguration
import com.mendelev.mpos.data.local.PrinterSettingsStore
import com.mendelev.mpos.core.access.Administration
import kotlinx.coroutines.*

/** Presentation of pinned renderNetworkScreen. Integration commands remain TODO09/D01–D05. */
@Composable internal fun NetworkSettingsScreen(onBack: ()->Unit) {
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    var syncDialog by remember {mutableStateOf(false)}
    val backendRuntime=com.mendelev.mpos.backend.BackendRuntime.get(androidx.compose.ui.platform.LocalContext.current)
    val backendBusy by backendRuntime.busy.collectAsState()
    val backendLiveStatus by backendRuntime.status.collectAsState()
    var backendStatus by remember {mutableStateOf("Подключение не проверено")}
    val context=androidx.compose.ui.platform.LocalContext.current
    val printRuntime=remember {com.mendelev.mpos.print.PrintRuntime.get(context)}
    val printStatus by printRuntime.status.collectAsState()
    val adminSession by Administration.session.state.collectAsState()
    val generation=adminSession.generation
    val telegramRuntime=remember {com.mendelev.mpos.telegram.TelegramRuntime.get(context)}
    var telegramSettings by remember {mutableStateOf<com.mendelev.mpos.telegram.TelegramSettings?>(null)}
    var telegramReadError by remember {mutableStateOf(false)}
    LaunchedEffect(generation) {try {telegramSettings=withContext(Dispatchers.IO) {telegramRuntime.settingsStore(generation).read()}} catch(_:Exception) {telegramReadError=true}}
    val store=remember(context,generation) {PrinterSettingsStore(java.io.File(context.filesDir,"printer-configurations-v1.json")) {Administration.session.state.value.let {it.active && it.generation==generation}}}
    var configured by remember {mutableStateOf<List<PrinterConfiguration>>(emptyList())}
    var editing by remember {mutableStateOf<PrinterConfiguration?>(null)}
    var busy by remember {mutableStateOf(true)}
    var readFailed by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    fun load() {
        busy=true
        scope.launch {
            try {configured=withContext(Dispatchers.IO) {store.read()};readFailed=false;error=null}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {readFailed=true;error="Не удалось прочитать конфигурации принтеров"}
            finally {busy=false}
        }
    }
    fun persist(config: PrinterConfiguration,delete: Boolean=false,test: Boolean=false) {
        if(busy) return
        busy=true;error=null
        scope.launch {
            try {configured=withContext(Dispatchers.IO) {if(delete) store.delete(config) else store.save(config)};if(test) {val saved=configured.single {it.id==config.id};editing=saved;com.mendelev.mpos.print.PrintRuntime.get(context).print(com.mendelev.mpos.print.PrintDocument("test:${saved.id}",com.mendelev.mpos.print.PrintKind.TEST,System.currentTimeMillis()),saved);if(saved.drawer) printRuntime.drawer("test:${java.util.UUID.randomUUID()}",automatic=true)} else editing=null}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {error=e.message ?: "Не удалось сохранить принтер"}
            finally {busy=false}
        }
    }
    LaunchedEffect(store) {load()}
    BackHandler(enabled=!busy,onBack=onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("networkSettingsScreen")) {
        if(LocalMposTopNavigation.current!=null) MposAppBar {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                MposButton("Назад",onBack,enabled=!busy,style=ActionStyle.Outline)
                Text("Сетевые конфигурации",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
            }
            if(editing==null) error?.let {MposNotice(it,true)}
            if(readFailed) MposButton("Повторить чтение",{load()},enabled=!busy)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cards: List<@Composable (Modifier)->Unit> = listOf(
                    {m->NetworkCard("Меню и backend","Подключение и ручная передача каталога",if(backendStatus=="Подключение не проверено") backendLiveStatus else backendStatus,m,pending=false) {
                        MposButton("Синхронизировать",{syncDialog=true},Modifier.weight(1f).testTag("backendSync"),enabled=!backendBusy)
                        MposButton("Настроить",{dialog="backend"},Modifier.weight(1f).testTag("networkBackend"),enabled=!backendBusy,style=ActionStyle.Outline)
                    }},
                    {m->NetworkCard("Чековые принтеры","",if(busy) "Загрузка…" else if(readFailed) "Не удалось прочитать список" else "",m,pending=false,headerAction={
                        SettingsRoundAction("+","Подключить принтер",{error=null;editing=PrinterConfiguration()},Modifier.testTag("networkPrinters"),enabled=!busy && !readFailed)
                    },body={
                        if(!busy && !readFailed && configured.isEmpty()) Text("Принтеров пока нет.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        configured.forEach {printer->
                            SettingsDirectoryRow(printer.name,"${printer.ip}:9100 · ${printer.paperWidth} мм",Modifier.testTag("printer:${printer.id}"),
                                if(!busy && !readFailed) {{error=null;editing=printer}} else null)
                        }
                        printStatus?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
                    })},
                    {m->NetworkCard("Telegram","Отчёты в группу и уведомления рабочего устройства",when {telegramReadError->"Не удалось прочитать настройки";telegramSettings==null->"Загрузка…";telegramSettings?.enabled==true->"Telegram включён";else->"Telegram выключен"},m,pending=false) {
                        MposButton("Настроить Telegram",{dialog="telegram"},Modifier.fillMaxWidth().testTag("networkTelegram"))
                    }}
                )
                val columns=if(maxWidth>700.dp) 2 else 1
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    cards.chunked(columns).forEach {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {row.forEach {it(Modifier.weight(1f))};if(row.size<columns) Spacer(Modifier.weight(1f))}
                    }
                }
            }
        }
    }
    editing?.let {printer->PrinterConfigurationDialog(printer,busy,error,{if(!busy) {editing=null;error=null}},{persist(it)},{persist(it,true)},{persist(it,test=true)})}
    if(dialog=="telegram") TelegramSettingsDialog({dialog=null},{telegramSettings=it;telegramReadError=false})
    if(syncDialog) BackendSyncDialog({syncDialog=false}) {backendStatus=it}
    if(dialog=="backend") BackendSettingsDialog({dialog=null}) {backendStatus=it}
}

@Composable private fun NetworkPending(text: String) {
    Text(text,fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun NetworkCard(title: String,subtitle: String,status: String,modifier: Modifier,pending: Boolean=true,headerAction: (@Composable ()->Unit)?=null,body: @Composable ColumnScope.()->Unit={},actions: (@Composable RowScope.()->Unit)?=null) {
    Surface(modifier.heightIn(min=220.dp),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.ExtraBold)
                headerAction?.invoke()
            }
            if(subtitle.isNotBlank()) Text(subtitle,fontSize=12.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(status.isNotBlank()) Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.background) {
                Text("●  $status",Modifier.padding(12.dp),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            body()
            if(pending) NetworkPending("Подключение будет реализовано")
            actions?.let {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),content=it)}
        }
    }
}
