package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.feature.access.AdminControl

/** Pinned renderSettingsScreen/renderAdminPanel, mapped to shared native controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsHome(onUpdates: ()->Unit,onCatalog: (() -> Unit)?,onSuppliers: (() -> Unit)?,
    onInventory: (() -> Unit)?,onStock: (() -> Unit)?,onGallery: (() -> Unit)?,onBack: ()->Unit,updateAvailable: Boolean) {
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    var panel by rememberSaveable {mutableStateOf(false)}
    var planned by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(admin.active) {if(!admin.active) panel=false}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        if(LocalMposTopNavigation.current!=null) MposAppBar {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    MposButton("Назад",onBack,style=ActionStyle.Secondary)
                    Text("Настройки",style=MaterialTheme.typography.titleLarge)
                    MposInfoButton("Проверить обновления приложения",onUpdates,Modifier.testTag("settingsUpdatesInfo"))
                }
                AdminControl(compact=true)
            }
            if(updateAvailable) Text("Доступно обновление — откройте ⓘ",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodyMedium)
            MposButton("Панель администратора",{panel=true},Modifier.fillMaxWidth().testTag("settingsAdminPanel"),enabled=admin.active)
            if(!admin.active) Text("Для управления приложением войдите в администрирование",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            onCatalog?.let {MposButton("Товары",it,Modifier.fillMaxWidth(),style=ActionStyle.Outline)}
            MposButton("Резервная копия",{planned="Резервная копия"},Modifier.fillMaxWidth().testTag("settingsBackup"),style=ActionStyle.Outline)
            SettingsCard("Складской учёт","Остатки, движения товаров и инвентаризация уже подключены.") {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    onStock?.let {MposButton("Остатки и движения",it,Modifier.testTag("settingsStock"),style=ActionStyle.Outline)}
                    onInventory?.let {MposButton("Инвентаризация",it,Modifier.testTag("settingsInventory"),style=ActionStyle.Outline)}
                    onSuppliers?.let {MposButton("Поставщики",it,Modifier.testTag("settingsSuppliers"),style=ActionStyle.Outline)}
                }
            }
            BoxWithConstraints {
                val left: @Composable ()->Unit={
                    SettingsCard("Сотрудники","Кассиры и администраторы, выбор сотрудника при открытии смены.",planned=true,onInfo={planned="Сотрудники"}) {}
                    SettingsCard("Стоимость доставки","Справочник тарифов доставки и синхронизация с сайтом.",planned=true,onInfo={planned="Стоимость доставки"}) {}
                    SettingsCard("Шаблоны названий чеков","Быстрые варианты названий для отложенных заказов.",planned=true,onInfo={planned="Шаблоны названий чеков"}) {}
                }
                val right: @Composable ()->Unit={
                    SettingsCard("Поставщики и товары","Поставщики и закреплённые за ними товары уже доступны.") {
                        onSuppliers?.let {MposButton("Открыть поставщиков",it,Modifier.testTag("settingsSuppliersCard"),style=ActionStyle.Outline)}
                    }
                    SettingsCard("Скидки","Скидки строк чека доступны при продаже. Справочник шаблонов скидок будет добавлен.",planned=true,onInfo={planned="Шаблоны скидок"}) {}
                    SettingsCard("Оформление","Используется системная светлая или тёмная тема. Ручное переключение будет добавлено.",planned=true,onInfo={planned="Выбор темы"}) {}
                }
                if(maxWidth>700.dp) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1.2f),verticalArrangement=Arrangement.spacedBy(16.dp)) {left()}
                    Column(Modifier.weight(.8f),verticalArrangement=Arrangement.spacedBy(16.dp)) {right()}
                } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {left();right()}
            }
            if(com.mendelev.mpos.BuildConfig.DEBUG) onGallery?.let {MposButton("Компоненты",it,style=ActionStyle.Secondary)}
        }
    }
    if(panel && admin.active) MposDialog("Панель администратора",{panel=false},maxWidth=820.dp) {
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            for((title,description) in listOf("Программа лояльности" to "Программы, подарки и база клиентов.","Сетевые настройки" to "Подключение сайта, синхронизация, принтер и Telegram.","Реквизиты организации" to "Название заведения, юридическое лицо и адрес.")) {
                SettingsCard(title,description,planned=true,onInfo={panel=false;planned=title}) {}
            }
            SettingsCard("Инвентаризация","График пересчёта, проведение и история уже доступны.") {
                onInventory?.let {action->MposButton("Открыть инвентаризацию",{panel=false;action()},style=ActionStyle.Outline)}
            }
            SettingsCard("Складской учёт","Остатки и движения товаров уже доступны. Расширенные отчёты будут добавлены.") {
                onStock?.let {action->MposButton("Открыть склад",{panel=false;action()},style=ActionStyle.Outline)}
            }
        }
    }
    planned?.let {title->MposDialog(title,{planned=null}) {
        Text("Будет реализовано",Modifier.testTag("settingsPlannedNotice"),style=MaterialTheme.typography.titleMedium)
        Text("Этот раздел пока не подключён. Его реализация запланирована в следующих этапах разработки.")
        MposButton("Понятно",{planned=null})
    }}
}

@Composable private fun SettingsCard(title: String,description: String,planned: Boolean=false,onInfo: (() -> Unit)?=null,content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                onInfo?.let {MposInfoButton("Информация: $title",it)}
            }
            Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(planned) Text("Будет реализовано",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
