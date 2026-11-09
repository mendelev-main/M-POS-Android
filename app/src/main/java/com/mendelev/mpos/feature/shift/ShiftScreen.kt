package com.mendelev.mpos.feature.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.shift.ShiftRules

@Composable fun ShiftScreen(model: ShiftViewModel,onCatalog: ()->Unit,onWorkspace: ()->Unit,onSettings: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle();val s=ui.snapshot
    val printerContext=androidx.compose.ui.platform.LocalContext.current
    val printer=remember {com.mendelev.mpos.print.PrintRuntime.get(printerContext)}
    val printStatus by printer.status.collectAsStateWithLifecycle()
    val telegram=remember {com.mendelev.mpos.telegram.TelegramRuntime.get(printerContext)}
    val telegramStatus by telegram.status.collectAsStateWithLifecycle()
    var printReport by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(ui.committedCashId) {ui.committedCashId?.let {printer.drawer("cash:$it",automatic=true)}}
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) {model.loadCashiers()}
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    var report by rememberSaveable {mutableStateOf<String?>(null)}
    var planned by rememberSaveable {mutableStateOf<String?>(null)}
    var revision by rememberSaveable {mutableStateOf(0L)};var previous by rememberSaveable {mutableStateOf<String?>(null)}
    var actor by rememberSaveable {mutableStateOf("")};var token by rememberSaveable {mutableStateOf("")}
    var expected by rememberSaveable {mutableStateOf(0L)};var counted by rememberSaveable {mutableStateOf("")}
    fun open() {if(s!=null) {revision=s.revision;previous=s.last?.id;actor=ui.cashiers.singleOrNull()?.id ?: "";expected=s.carry;dialog="open";model.dismiss()}}
    fun close() {if(s?.active!=null) {revision=s.revision;previous=s.active!!.id;actor=s.active!!.actorId;token=ShiftRules.token(s);expected=s.currentCash;counted=java.math.BigDecimal.valueOf(expected,2).toPlainString();printer.drawer("shift-count:${java.util.UUID.randomUUID()}",automatic=true);dialog="close";model.dismiss()}}
    val navigationEnabled=!ui.pending && dialog==null && report==null && planned==null && ui.cashForm==null && ui.cashUnresolved==null
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("M-POS · Смена",navigationEnabled=navigationEnabled) {
            MposNavigationAction("Товары",onCatalog);MposNavigationAction("Рабочая зона",onWorkspace);MposNavigationAction("Настройки",onSettings)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start=26.dp,end=26.dp,top=22.dp,bottom=40.dp)) {
            Text("Кассовая смена",Modifier.padding(bottom=18.dp),fontSize=21.sp,lineHeight=28.sp,fontWeight=FontWeight.ExtraBold)
            telegramStatus?.let {Text("Telegram: $it",Modifier.padding(bottom=12.dp),style=MaterialTheme.typography.bodySmall)}
            if(ui.loading) CircularProgressIndicator()
            else if(ui.fatal || s==null) {
                MposNotice(ui.message ?: "Смена недоступна",true)
                if(ui.cashUnresolved==null && ui.cashForm==null) MposButton("Повторить чтение",model::refresh,enabled=!ui.pending)
            } else {
                if(ui.message!=null && dialog==null && ui.cashForm==null) MposNotice(ui.message!!,true)
                if(ui.dashboardMessage!=null) {MposNotice(ui.dashboardMessage!!,true);MposButton("Повторить чтение",model::refresh,enabled=navigationEnabled)}
                val data=ui.dashboard
                if(data!=null) ShiftOverview(data,!ui.pending && ui.cashForm==null && ui.cashUnresolved==null,
                    !ui.staffLoading && ui.staffMessage==null && ui.cashiers.isNotEmpty(),admin.active,::open,::close,
                    {model.cashOpen("deposit")},{model.cashOpen("withdrawal")},{printer.clearStatus();printer.drawer();planned="Кассовый ящик"},{report=it},
                    historyLoading=ui.historyLoading,historyMessage=ui.historyMessage,onMore=model::loadMoreHistory)
                else if(ui.dashboardMessage==null) Text("Загрузка итогов смены…")
                if(s.active==null) {
                    ui.staffMessage?.let {MposNotice(it,true);MposButton("Повторить чтение сотрудников",model::loadCashiers)}
                    if(!ui.staffLoading && ui.staffMessage==null && ui.cashiers.isEmpty()) {
                        Text("Перед новой сменой создайте сотрудника в настройках. Администратор кассовые смены не открывает.",Modifier.padding(top=16.dp),fontSize=13.sp)
                        MposButton("Настройки сотрудников",onSettings,Modifier.testTag("shiftSetupEmployees"),style=ActionStyle.Secondary)
                    }
                }
            }
        }
    }
    val cashForm=ui.cashForm
    if(cashForm!=null) {
        val editable=!ui.pending && ui.cashUnresolved==null
        MposWebModal(if(cashForm.type=="deposit") "Внести наличные" else "Изъять наличные",model::cashClose,editable) {
            MposWebField(cashForm.amount,{model.cashEdit(amount=it)},"Сумма","shiftCashAmount",editable,true,"0,00")
            MposWebField(cashForm.note,{model.cashEdit(note=it)},"Комментарий","shiftCashNote",editable,placeholder="Необязательно")
            ui.message?.let {MposNotice(it,true)}
            if(ui.cashUnresolved!=null) MposPosControl("Проверить результат",model::cashRetry,Modifier.fillMaxWidth().testTag("shiftCashRetry"),enabled=!ui.pending,kind=PosControl.Cash)
            else MposWebActions(model::cashClose,if(cashForm.type=="deposit") "Внести" else "Изъять",model::cashSave,!ui.pending,saveEnabled=!ui.fatal,tag="shiftCashSave")
        }
    }
    if(dialog!=null) MposWebModal(if(dialog=="open") "Открыть смену" else "Закрыть смену",{dialog=null;model.dismiss()},!ui.pending) {
        if(dialog=="open") {
            MposWebSelect("Сотрудник",actor,listOf("" to "Выберите сотрудника")+ui.cashiers.map {it.id to it.name},{actor=it},!ui.pending,"shiftEmployeeSelection")
            MposWebLabel("Наличные при открытии смены")
            Text(ShiftRules.money(expected),Modifier.testTag("shiftCarry"),fontSize=26.sp,lineHeight=35.sp,fontWeight=FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            MposWebWarningNote("Проверьте наличные в кассе. Сумма перенесена с прошлой смены")
        } else {
            MposWebLabel("Ожидается в кассе (наличные)")
            Text(ShiftRules.money(expected),Modifier.padding(bottom=14.dp),fontSize=20.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold)
            MposWebField(counted,{counted=it},"Фактически пересчитано","shiftCounted",!ui.pending,true)
        }
        ui.message?.let {MposNotice(it,true)}
        MposWebActions({dialog=null;model.dismiss()},if(dialog=="open") "Открыть" else "Закрыть смену",{
            if(dialog=="open") model.open(revision,previous,actor) {dialog=null;model.state.value.snapshot?.last?.id?.let {printer.shiftCommitted(it,false);com.mendelev.mpos.telegram.TelegramRuntime.get(printerContext).shiftCommitted(it,false)}}
            else model.close(previous!!,revision,token,counted,actor) {dialog=null;previous?.let {printer.shiftCommitted(it,true);com.mendelev.mpos.telegram.TelegramRuntime.get(printerContext).shiftCommitted(it,true)}}
        },!ui.pending,saveEnabled=dialog!="open" || ui.cashiers.any {it.id==actor},tag="shiftConfirm")
    }
    val reportRow=ui.dashboard?.summaries?.find {it.shift.id==report && !it.shift.isOpen}
    if(reportRow!=null) ShiftReportWindow(reportRow,{report=null},{printReport=reportRow.shift.id})
    ui.dashboard?.summaries?.find {it.shift.id==printReport}?.let {com.mendelev.mpos.print.PrintWindow(com.mendelev.mpos.print.PrintDocuments.shift(it)) {printReport=null}}
    if(planned!=null) MposWebModal(planned!!,{planned=null}) {
        Text(printStatus ?: "Отправка команды ящика…")
        Spacer(Modifier.height(18.dp));MposPosControl("Понятно",{planned=null},Modifier.fillMaxWidth(),kind=PosControl.Cash)
    }
}
