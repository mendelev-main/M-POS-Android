package com.mendelev.mpos.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import java.math.BigDecimal
import java.math.BigInteger

@OptIn(ExperimentalLayoutApi::class)
@Composable fun BackupScreen(model: BackupViewModel,onSettings: ()->Unit,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(model,admin.generation) {model.open()}
    val recoveryChooser=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {model.preparationSelected(it?.toString())}
    val nativeChooser=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {model.nativeSelected(it?.toString())}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {model.exportSelected(it?.toString())}
    val chooser=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {model.selected(it?.toString())}
    ui.confirmation?.takeIf {admin.active && ui.epoch==admin.generation}?.let {p->
        var activeConfirmed by remember(p.id) {mutableStateOf(false)}
        val needsActive=p.protection.activity.openShifts>0 || p.protection.activity.partials==null || p.protection.activity.partials!!>0
        MposDialog("Заменить рабочие данные?",{model.dismissApply()}) {
            Text("Будет применена выбранная native-копия целиком. Текущие данные сохранены в защитной копии ${p.protection.id.take(8)}. Объединения историй и повторного приёма оплаченных частей не будет")
            Text("После применения закройте M-POS и откройте снова. До нового запуска рабочие операции будут остановлены")
            if(needsActive) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(activeConfirmed,{activeConfirmed=it},Modifier.testTag("recoveryActiveConfirmation"))
                Text("Подтверждаю замену открытой смены и частичных оплат. Их текущее состояние сохранено в защитной копии")
            }
            MposButton("Заменить данные",{model.confirmApply(activeConfirmed)},Modifier.testTag("recoveryConfirmApply"),style=ActionStyle.Danger,enabled=!needsActive || activeConfirmed)
            MposButton("Отмена",{model.dismissApply()},style=ActionStyle.Secondary)
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("Резервная копия") {MposButton("Назад",onBack,style=ActionStyle.Secondary)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(admin.active && (ui.picking || ui.busy || ui.error!=null)) MposAnalyticsCard(Modifier.fillMaxWidth()) {
                if(ui.picking) Text("Завершите выбор файла в системном окне")
                if(ui.busy) Text("Подготавливаем или проверяем копию…",Modifier.testTag("backupBusy"))
                ui.error?.let {MposNotice(it,true)}
            }
            if(model.recoveryEnabled && admin.active) MposAnalyticsCard(Modifier.fillMaxWidth().testTag("recoveryActions")) {
                Text("Подготовка восстановления",style=MaterialTheme.typography.titleLarge)
                Text("Выберите native-копию. Приложение проверит файл и сохранит защитную копию текущих данных, включая открытую смену и уже оплаченные части.")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Подготовить native-восстановление",{if(model.beginPreparation()) try {recoveryChooser.launch(arrayOf("*/*"))} catch(_:Exception) {model.preparationSelected(null)}},Modifier.testTag("recoveryPrepare"),enabled=!ui.picking && !ui.busy)
                    MposButton("Показать защитные копии",{model.loadRecovery()},Modifier.testTag("recoveryInventory"),style=ActionStyle.Secondary,enabled=!ui.picking && !ui.busy)
                }
                Text(if(model.activationEnabled) "Подготовка не меняет рабочую базу. Применение потребует подтверждения и нового запуска M-POS" else "Применение ещё не подключено. Подготовка сохраняется после перезапуска, рабочая база не заменяется.",Modifier.testTag("recoveryPreparationOnly"))
            }
            if(ui.activated && admin.active) MposNotice("Для завершения проверки закройте M-POS и откройте снова",false)
            ui.prepared?.takeIf {admin.active && ui.epoch==admin.generation}?.let {p->
                MposAnalyticsCard(Modifier.fillMaxWidth().testTag("recoveryPrepared")) {
                    Text("Файл подготовлен, защитная копия сохранена",style=MaterialTheme.typography.titleMedium)
                    Text("В выбранной копии: товаров ${p.source.products}, смен ${p.source.shifts}, чеков ${p.source.receipts}")
                    RecoveryActivity("Выбранная копия",p.sourceActivity)
                    RecoveryActivity("Защитная копия текущих данных",p.protection.activity)
                    Text("Защитная копия: ${p.protection.id.take(8)} · "+java.time.Instant.ofEpochMilli(p.protection.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' '))
                    Text("Наборы данных сохранены отдельно. Для будущего применения нужно повторно проверить, не изменились ли рабочие данные, и подтвердить замену. Активная смена потребует дополнительного подтверждения.")
                    if(model.activationEnabled) MposButton("Применить восстановление",{model.requestApply(p)},Modifier.testTag("recoveryApply"),enabled=!ui.busy)
                    MposButton("Очистить предпросмотр",{model.clear()},style=ActionStyle.Secondary)
                }
            }
            ui.recovery?.takeIf {admin.active && ui.epoch==admin.generation}?.let {r->
                MposAnalyticsCard(Modifier.fillMaxWidth().testTag("recoveryInventoryResult")) {
                    Text("Защитные копии",style=MaterialTheme.typography.titleMedium)
                    Text("Проверено: ${r.protections.size} · Подготовлено восстановлений: ${r.prepared.size}")
                    if(r.damaged>0) MposNotice("Записей, не прошедших проверку: ${r.damaged}. Их файлы сохранены и не будут применены автоматически",true)
                    if(r.protections.isEmpty()) Text("Проверенных защитных копий пока нет")
                    r.protections.forEach {p->
                        Text("Копия ${p.id.take(8)} · "+java.time.Instant.ofEpochMilli(p.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString().replace('T',' '))
                        Text("Товаров: ${p.report.products} · Смен: ${p.report.shifts} · Чеков: ${p.report.receipts}")
                        RecoveryActivity("Сохранённое состояние",p.activity)
                        if(model.activationEnabled) MposButton("Подготовить копию ${p.id.take(8)}",{model.prepareProtection(p.id)},style=ActionStyle.Secondary,enabled=!ui.busy)
                    }
                    if(model.activationEnabled) r.prepared.forEach {p->MposButton("Применить подготовку ${p.id.take(8)}",{model.requestApply(p)},style=ActionStyle.Secondary,enabled=!ui.busy)}
                    Text("Список проверяет сохранённые файлы и не выполняет восстановление автоматически. Удаление приложения удалит и эти внутренние копии; внешние копии сохраняйте отдельно.")
                }
            }
            if(model.nativeEnabled && admin.active) MposAnalyticsCard(Modifier.fillMaxWidth().testTag("nativeBackupActions")) {
                Text("Копия этого приложения",style=MaterialTheme.typography.titleLarge)
                Text("Сохраняет товары, фото, историю, открытую смену и частичные оплаты. Данные не пересчитываются. После записи файл читается повторно и проверяется.")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    MposButton("Сохранить native-копию",{if(model.beginExport()) try {exporter.launch("M-POS-"+java.time.LocalDate.now()+".mposnative")} catch(_:Exception) {model.exportSelected(null)}},Modifier.testTag("nativeBackupExport"),enabled=!ui.picking && !ui.busy)
                    MposButton("Проверить native-копию",{if(model.beginNativeInspection()) try {nativeChooser.launch(arrayOf("*/*"))} catch(_:Exception) {model.nativeSelected(null)}},Modifier.testTag("nativeBackupInspect"),style=ActionStyle.Secondary,enabled=!ui.picking && !ui.busy)
                }
                Text("Проверка файла подтверждает целостность и не заменяет рабочую базу.")
            }
            ui.native?.takeIf {admin.active && ui.epoch==admin.generation}?.let {n->
                MposAnalyticsCard(Modifier.fillMaxWidth().testTag("nativeBackupResult")) {
                    Text(if(n.exported) "Копия сохранена и повторно проверена" else "Целостность native-копии проверена",style=MaterialTheme.typography.titleMedium)
                    Text("Товаров: ${n.report.products} · Смен: ${n.report.shifts} · Чеков: ${n.report.receipts} · Фото: ${n.report.photos}")
                    Text("Размер: ${n.report.bytes} байт")
                    Text("SHA-256: ${n.report.sha256}",style=MaterialTheme.typography.bodySmall)
                    Text("Версия формата: 1 · Схема базы: 9. Проверка целостности не означает завершённого восстановления.")
                    MposButton("Очистить результат",{model.clear()},style=ActionStyle.Secondary)
                }
            }
            MposAnalyticsCard(Modifier.fillMaxWidth()) {
                Text("Проверка Web-копии",style=MaterialTheme.typography.titleLarge)
                Text("Выберите .mposbackup или JSON старого приложения. Проверка не заменяет данные и не выполняет оплаты.")
                Text("Сейчас доступен предварительный разбор. Преобразование в native и восстановление будут подключены следующим шагом.",Modifier.testTag("backupInspectionOnly"))
                if(!admin.active) {
                    Text("Для чтения копии войдите в администрирование",Modifier.testTag("backupLocked"))
                    MposButton("Открыть настройки",onSettings)
                } else {
                    MposButton("Выбрать Web-копию",{if(model.beginSelection()) try {chooser.launch(arrayOf("*/*"))} catch(_:Exception) {model.selected(null)}},
                        Modifier.testTag("backupChoose"),enabled=!ui.picking && !ui.busy)
                }
            }
            ui.preview?.takeIf {admin.active && ui.epoch==admin.generation}?.let {p->
                MposAnalyticsCard(Modifier.fillMaxWidth().testTag("backupPreview")) {
                    Text("Структура Web v${p.version} прочитана",style=MaterialTheme.typography.titleMedium)
                    Text("Товаров: ${p.sections["products"] ?: 0} · Сотрудников: ${p.sections["employees"] ?: 0}")
                    Text("Смен: ${p.sections["shifts"] ?: 0} · Чеков: ${p.sections["orders"] ?: 0}")
                    Text("Открытых смен: ${p.openShifts} · Черновиков частичной оплаты: ${p.paymentDrafts}")
                    if(p.currentSessionPresent) Text("Отмечено оплаченным: ${money(p.paidMinor)}",Modifier.testTag("backupPaid"))
                    else MposNotice("В этой копии нет текущего заказа. Отсутствие оплаченных частей не подтверждает нулевую оплату",true)
                    if(p.currentSessionPresent) Text("Осталось по частям: ${money(p.unpaidMinor)}")
                    Text("Фотографий в копии: ${p.imageCount} · Ссылок на внешние изображения: ${p.externalImages}")
                    Text("Преобразование документов, итогов и ссылок проверяется отдельно. Этот результат не означает завершённого переноса.")
                }
                MposAnalyticsCard(Modifier.fillMaxWidth()) {
                    Text("Разделы с отдельными правилами переноса",style=MaterialTheme.typography.titleMedium)
                    Text("${p.deferredSections.size} групп настроек/данных требуют отдельного решения; неизвестных разделов: ${p.unknownSections}")
                    p.deferredSections.distinct().forEach {key->
                        val label=when(key) {
                            "hallTables"->"Зал";"bookings"->"Бронирования";"operationalOutbox"->"Операционный журнал: требуется сверка повторов"
                            "webEvents"->"События сайта";"network"->"Подключение к сайту";"telegram"->"Telegram";"printer","printerSettings"->"Печать"
                            "webOrderAcceptances"->"Принятые онлайн-заказы";"sessionIntegrations"->"Лояльность и связи онлайн-заказа"
                            "company"->"Реквизиты компании";"theme"->"Тема Web";"discounts"->"Шаблоны скидок";"deliveryRates"->"Тарифы доставки"
                            "receiptLabelTemplates"->"Шаблоны названий чеков";else->"Прочие данные"
                        }
                        Text("$label: ${p.sections[key]?.toString() ?: "настройки присутствуют"}")
                    }
                    Text("Сайт, лояльность, зал и интеграции не включаются автоматически. Исходный файл остаётся неизменным.")
                    MposButton("Очистить результат",{model.clear()},Modifier.testTag("backupClear"),style=ActionStyle.Secondary)
                }
            }
        }
    }
}
private fun money(minor: BigInteger)=BigDecimal(minor,2).toPlainString().replace('.',',')+" BYN"

@Composable private fun RecoveryActivity(label: String,a: com.mendelev.mpos.data.backup.NativeSnapshotActivity) {
    Text("$label: открытых смен ${a.openShifts}; "+(a.partials?.let {"частичных оплат $it"} ?: "частичные оплаты требуют отдельной проверки"))
    a.paidMinor?.let {Text("$label — отмечено оплаченным: "+money(it.toBigInteger()))}
}
