package com.mendelev.mpos.print

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.PrinterConfiguration
import kotlinx.coroutines.*

@Composable fun ReceiptPrintWindow(value: Pair<com.mendelev.mpos.data.local.ReceiptEntity,com.mendelev.mpos.data.local.ReceiptSnapshot>,kind: PrintKind=PrintKind.RECEIPT,onClose: ()->Unit) {
    val context=LocalContext.current
    var document by remember(value.first.id,kind) {mutableStateOf<PrintDocument?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(value.first.id,kind) {
        try {document=withContext(Dispatchers.IO) {val db=com.mendelev.mpos.data.local.CatalogDatabase.get(context);val employee=db.shifts().shift(value.first.shiftId)?.actorName ?: "Сотрудник";PrintDocuments.receipt(value,employee,com.mendelev.mpos.data.local.LoyaltyOutbox(db).read(value.first.id)).copy(kind=kind)}}
        catch(e:Exception) {error=e.message ?: "Не удалось открыть чек"}
    }
    document?.let {PrintWindow(it,onClose)}
    LaunchedEffect(error) {error?.let {android.widget.Toast.makeText(context,it,android.widget.Toast.LENGTH_SHORT).show();onClose()}}
}

/** A click is one explicit print request; never display an intermediate modal. */
@Composable fun PrintWindow(document: PrintDocument,onClose: ()->Unit) {
    if(document.kind==PrintKind.SHIFT) {PrintPreviewWindow(document,onClose);return}
    val context=LocalContext.current
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(document) {
        PrintRuntime.get(context).manualAll(document)
        close()
    }
}

@Composable private fun PrintPreviewWindow(document: PrintDocument,onClose: ()->Unit) {
    val context=LocalContext.current;val runtime=remember {PrintRuntime.get(context)}
    val status by runtime.status.collectAsStateWithLifecycle()
    val pending by runtime.pendingJobs.collectAsStateWithLifecycle()
    var printers by remember {mutableStateOf<List<PrinterConfiguration>>(emptyList())}
    var selected by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    var loading by remember {mutableStateOf(true)}
    LaunchedEffect(document) {
        runtime.clearStatus()
        try {printers=withContext(Dispatchers.IO) {PrinterRouting.targets(document.kind,runtime.configurations())};selected=printers.firstOrNull()?.id.orEmpty()}
        catch(_:Exception) {error="Не удалось прочитать принтеры"}
        finally {loading=false}
    }
    val printer=printers.find {it.id==selected}
    var bitmap by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(document,printer) {
        bitmap=null
        if(printer!=null) try {
            val prepared=withContext(Dispatchers.IO) {runtime.prepare(document)}
            bitmap=withContext(Dispatchers.Default) {ReceiptRenderer.render(PrintDocuments.page(prepared,printer))}
        }
        catch(e:Exception) {error=e.message}
    }
    MposDialog(if(document.kind==PrintKind.KITCHEN) "Печать заказа" else "Печать чека",onClose,maxWidth=640.dp) {
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        if(!loading && printers.isEmpty()) Text("Принтер не настроен. Добавьте его в панели администратора → Сетевые настройки.")
        if(printers.isNotEmpty()) MposSelect("Принтер",selected,printers.map {it.id to "${it.name} · ${it.paperWidth} мм"},{selected=it})
        bitmap?.let {image->Box(Modifier.weight(1f,fill=false).heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            Image(image.asImageBitmap(),"Предпросмотр печати",Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).testTag("printPreview"))
        }}
        status?.let {Text(it,Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall)}
        Text("Если отправка прервалась, сначала проверьте бумажный чек. Повторная печать выполняется только по вашему нажатию.",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("Закрыть",onClose,Modifier.weight(1f),kind=PosControl.Secondary,height=48.dp,radius=16.dp)
            MposPosControl("Распечатать",{printer?.let {runtime.manual(document,it)}},Modifier.weight(1f).testTag("printSend"),enabled=printer!=null && bitmap!=null && pending==0,kind=PosControl.Cash,height=48.dp,radius=16.dp)
        }
    }
}


/** History has an explicit document choice; each button enqueues one manual request. */
@Composable fun ReceiptPrintMenu(value: Pair<com.mendelev.mpos.data.local.ReceiptEntity,com.mendelev.mpos.data.local.ReceiptSnapshot>,onClose: ()->Unit) {
    var kind by remember(value.first.id) {mutableStateOf<PrintKind?>(null)}
    if(kind==null) ReceiptPrintChoice(onClose) {kind=it}
    else ReceiptPrintWindow(value,kind!!,onClose)
}

@Composable internal fun ReceiptPrintChoice(onClose: ()->Unit,onChoose: (PrintKind)->Unit) {
    MposDialog("Печать",onClose,maxWidth=460.dp) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            MposPosControl("Печатать чек",{onChoose(PrintKind.RECEIPT)},Modifier.fillMaxWidth().testTag("receiptPrintReceipt"),
                kind=PosControl.Cash,height=52.dp,radius=16.dp,icon="bill")
            MposPosControl("Печатать заказ",{onChoose(PrintKind.KITCHEN)},Modifier.fillMaxWidth().testTag("receiptPrintKitchen"),
                kind=PosControl.Secondary,height=52.dp,radius=16.dp,icon="printer")
        }
    }
}
