package com.mendelev.mpos.feature.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.shift.ShiftRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ShiftScreen(model: ShiftViewModel,onCatalog: () -> Unit,onWorkspace: () -> Unit,onSettings: () -> Unit) {
    val ui by model.state.collectAsStateWithLifecycle();val s=ui.snapshot
    var dialog by rememberSaveable {mutableStateOf<String?>(null)}
    var revision by rememberSaveable {mutableStateOf(0L)};var previous by rememberSaveable {mutableStateOf<String?>(null)}
    var actor by rememberSaveable {mutableStateOf("")};var token by rememberSaveable {mutableStateOf("")}
    var expected by rememberSaveable {mutableStateOf(0L)};var counted by rememberSaveable {mutableStateOf("")}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("M-POS · Смена", navigationEnabled=!ui.pending && dialog==null) {
            MposNavigationAction("Товары",onCatalog);MposNavigationAction("Рабочая зона",onWorkspace);MposNavigationAction("Настройки",onSettings)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Смена",style=MaterialTheme.typography.headlineMedium)
            if(ui.loading) CircularProgressIndicator()
            else if(ui.fatal || s==null) MposNotice(ui.message ?: "Смена недоступна",true)
            else {
                if(ui.message!=null && dialog==null) MposNotice(ui.message!!,true)
                Text("Локальный владелец",style=MaterialTheme.typography.titleMedium)
                Text("Касса: ${s.registerId}",style=MaterialTheme.typography.bodySmall)
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        val active=s.active;val last=s.last
                        Text(if(active!=null) "Смена открыта" else "Смена закрыта",Modifier.testTag("shiftStatus"),style=MaterialTheme.typography.titleLarge)
                        if(last!=null) {
                            Text("Смена: ${last.id}",Modifier.testTag("shiftId"),style=MaterialTheme.typography.bodySmall)
                            Text("Открыта: ${date(last.openedAt,last.zoneId)}")
                            Text("Наличные при открытии: ${ShiftRules.money(last.openingCash)}")
                            if(active!=null) Text("Сейчас в кассе: ${ShiftRules.money(s.currentCash)}")
                            if(!last.isOpen) {
                                Text("Закрыта: ${date(last.closedAt!!,last.zoneId)}")
                                Text("Ожидалось: ${ShiftRules.money(last.expectedCash!!)}")
                                Text("Пересчитано: ${ShiftRules.money(last.countedCash!!)}",Modifier.testTag("shiftCountedSummary"))
                                Text("Расхождение: ${ShiftRules.money(last.difference!!)}",Modifier.testTag("shiftDifferenceSummary"))
                            }
                        }
                        if(active==null) {
                            Text("Наличные следующей смены: ${ShiftRules.money(s.carry)}",Modifier.testTag("shiftCarry"))
                            Text(if(last==null) "Первая смена начинается с 0 BYN" else "Сумма перенесена с прошлой смены. Проверьте наличные в кассе.",style=MaterialTheme.typography.bodySmall)
                            MposButton("Открыть смену",{revision=s.revision;previous=last?.id;actor=s.ownerId;expected=s.carry;dialog="open";model.dismiss()},Modifier.testTag("shiftOpen"),busy=ui.pending)
                        } else MposButton("Закрыть смену",{revision=s.revision;previous=active.id;actor=s.ownerId;token=ShiftRules.token(s);expected=s.currentCash;counted=java.math.BigDecimal.valueOf(expected,2).toPlainString();dialog="close";model.dismiss()},Modifier.testTag("shiftClose"),busy=ui.pending,style=ActionStyle.Secondary)
                    }
                }
                Text("Смена сохраняет состояние кассы. Оплаты будут доступны на следующем этапе.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(dialog!=null) MposDialog(if(dialog=="open") "Открыть смену" else "Закрыть смену",{dialog=null;model.dismiss()},!ui.pending) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        Text("Локальный владелец")
        Text((if(dialog=="open") "Наличные при открытии: " else "Ожидается в кассе: ")+ShiftRules.money(expected))
        if(dialog=="close") {
            MposField(counted,{counted=it},"Фактически пересчитано",modifier=Modifier.testTag("shiftCounted"),numeric=true,enabled=!ui.pending)
            val value=runCatching {ShiftRules.cash(counted)}.getOrNull()
            if(value!=null) Text("Расхождение: ${ShiftRules.money(value-expected)}",Modifier.testTag("shiftDifferencePreview"))
        } else Text(if(previous==null) "Первая смена" else "Перенос из смены $previous",style=MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            MposButton("Отмена",{dialog=null;model.dismiss()},enabled=!ui.pending,style=ActionStyle.Secondary)
            MposButton(if(dialog=="open") "Открыть" else "Подтвердить закрытие",{
                if(dialog=="open") model.open(revision,previous,actor) {dialog=null}
                else model.close(previous!!,revision,token,counted,actor) {dialog=null}
            },Modifier.testTag("shiftConfirm"),busy=ui.pending)
        }
    }
}
private fun date(value: Long,zone: String): String=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(value))
