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

@Composable fun BackupScreen(model: BackupViewModel,onSettings: ()->Unit,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(model,admin.generation) {model.open()}
    val chooser=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {model.selected(it?.toString())}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("Резервная копия") {MposButton("Назад",onBack,style=ActionStyle.Secondary)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
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
                    if(ui.picking) Text("Завершите выбор файла в системном окне")
                    if(ui.busy) Text("Проверяем файл…",Modifier.testTag("backupBusy"))
                    ui.error?.let {MposNotice(it,true)}
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
