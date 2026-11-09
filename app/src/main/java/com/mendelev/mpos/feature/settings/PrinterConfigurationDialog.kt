package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.PrinterConfiguration

@Composable internal fun PrinterConfigurationDialog(initial: PrinterConfiguration,busy: Boolean,error: String?,onClose: ()->Unit,onSave: (PrinterConfiguration)->Unit,onDelete: (PrinterConfiguration)->Unit,onTest: (PrinterConfiguration)->Unit) {
    var form by remember(initial.id,initial.revision) {mutableStateOf(initial)}
    var deleting by remember {mutableStateOf(false)}
    var preview by remember {mutableStateOf(false)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val runtime=remember {com.mendelev.mpos.print.PrintRuntime.get(context)}
    val status by runtime.status.collectAsState()
    val printPending by runtime.pendingJobs.collectAsState()
    var categories by remember {mutableStateOf<List<String>>(emptyList())}
    LaunchedEffect(Unit) {runtime.clearStatus();try {categories=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {com.mendelev.mpos.data.local.CatalogDatabase.get(context).catalog().categorySnapshot().map {it.name}}} catch(_:Exception) {}}
    val sample=com.mendelev.mpos.print.PrintDocument("test:${form.id}",com.mendelev.mpos.print.PrintKind.TEST,System.currentTimeMillis())
    if(preview) PrinterPreview(sample,form) {preview=false}
    MposDialog(if(deleting) "Удалить принтер?" else if(initial.revision==0L) "Подключить принтер" else "Настройки принтера",onClose,closable=!busy,maxWidth=760.dp) {
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).testTag("printerConfiguration"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            error?.let {MposNotice(it,true)}
            if(deleting) Text("Удалить «${initial.name}» из списка принтеров?") else {
                Text("Сетевой принтер ESC/POS · TCP 9100",style=MaterialTheme.typography.bodySmall)
                MposField(form.name,{form=form.copy(name=it)},"Название",Modifier.testTag("printerName"),!busy)
                MposField(form.ip,{form=form.copy(ip=it)},"IP-адрес",Modifier.testTag("printerIp"),!busy)
                MposSelect("Ширина бумаги",form.paperWidth,listOf("58" to "58 мм","80" to "80 мм"),{form=form.copy(paperWidth=it)},!busy)
                Text("Что печатает этот принтер",style=MaterialTheme.typography.titleMedium)
                PrinterToggle("Печатать платежные чеки",form.printReceipts,!busy) {form=form.copy(printReceipts=it)}
                PrinterToggle("Печатать комментарии в платежном чеке",form.paymentComments,!busy) {form=form.copy(paymentComments=it)}
                MposField(form.receiptTitle,{form=form.copy(receiptTitle=it)},"Название вверху платежного чека",enabled=!busy)
                MposField(form.registerLabel,{form=form.copy(registerLabel=it)},"Касса в платежном чеке",enabled=!busy)
                MposField(form.phrases,{form=form.copy(phrases=it)},"Случайные фразы · каждая с новой строки",enabled=!busy,singleLine=false)
                PrinterToggle("Печатать чеки заказов",form.printOrders,!busy) {form=form.copy(printOrders=it)}
                if(form.printOrders) {
                    Text("Категории для чеков заказов",style=MaterialTheme.typography.titleSmall)
                    Text("Если ничего не выбрано — печатаются все категории.",style=MaterialTheme.typography.bodySmall)
                    (categories+form.categories.lines().filter {it.isNotBlank()}).distinct().sorted().forEach {name->
                        val selected=form.categories.lines().filter {it.isNotBlank()}.toSet()
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(name in selected,{checked->form=form.copy(categories=(if(checked) selected+name else selected-name).joinToString("\n"))},enabled=!busy)
                            Text(name)
                        }
                    }
                }
                PrinterToggle("Автопечать платежного чека",form.autoReceipt,!busy) {form=form.copy(autoReceipt=it)}
                PrinterToggle("Автопечать заказа на кухню",form.autoKitchen,!busy) {form=form.copy(autoKitchen=it)}
                PrinterToggle("Открывать кассовый ящик после оплаты",form.drawer,!busy && form.printReceipts) {form=form.copy(drawer=it)}
                MposField(form.copies,{form=form.copy(copies=it)},"Количество копий",enabled=!busy,numeric=true)
                Text("Параметры печати",style=MaterialTheme.typography.titleMedium)
                Text("Сетевой порт: 9100",style=MaterialTheme.typography.bodySmall)
                MposSelect("Режим печати",form.printMode,listOf("graphic" to "Графический","text" to "Текстовый"),{form=form.copy(printMode=it)},!busy)
                MposSelect("Ширина печати",form.printWidth,listOf("auto" to "Автоматически","384" to "384 точки","576" to "576 точек"),{form=form.copy(printWidth=it)},!busy)
                MposField(form.dpi,{form=form.copy(dpi=it)},"Разрешение DPI",enabled=!busy,numeric=true)
                MposField(form.initCommand,{form=form.copy(initCommand=it)},"Команда инициализации HEX",enabled=!busy)
                MposField(form.cutCommand,{form=form.copy(cutCommand=it)},"Команда отрезки HEX",enabled=!busy)
                MposField(form.drawerCommand,{form=form.copy(drawerCommand=it)},"Команда ящика HEX",enabled=!busy)
                if(initial.revision>0) MposButton("Удалить принтер",{deleting=true},enabled=!busy,style=ActionStyle.Danger)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposButton("Вид чека",{preview=true},enabled=!busy,style=ActionStyle.Outline)
                    MposButton("Проверить связь",{runtime.check(form)},enabled=!busy,style=ActionStyle.Outline)
                    MposButton("Пробная печать",{onTest(form)},enabled=!busy && printPending==0,style=ActionStyle.Outline)
                }
                status?.let {Text(it,style=MaterialTheme.typography.bodySmall)}
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Отмена",{if(deleting) deleting=false else onClose()},Modifier.weight(1f),enabled=!busy,style=ActionStyle.Outline)
            MposButton(if(deleting) "Удалить" else "Сохранить",{if(deleting) onDelete(initial) else onSave(form)},Modifier.weight(1f).testTag("printerSave"),enabled=!busy,style=if(deleting) ActionStyle.Danger else ActionStyle.Primary)
        }
    }
}
@Composable private fun PrinterToggle(label: String,value: Boolean,enabled: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f));Switch(value,onChange,enabled=enabled)
    }
}

@Composable private fun PrinterPreview(document: com.mendelev.mpos.print.PrintDocument,p: PrinterConfiguration,onClose: ()->Unit) {
    var bitmap by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(p) {try {bitmap=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {com.mendelev.mpos.print.ReceiptRenderer.render(com.mendelev.mpos.print.PrintDocuments.page(document,p))}} catch(e:Exception) {error=e.message}}
    MposDialog("Пробный чек",onClose,maxWidth=480.dp) {
        error?.let {Text(it)}
        bitmap?.let {image->Box(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState())) {androidx.compose.foundation.Image(image.asImageBitmap(),"Вид пробного чека",Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height))}}
        MposButton("Закрыть",onClose)
    }
}
