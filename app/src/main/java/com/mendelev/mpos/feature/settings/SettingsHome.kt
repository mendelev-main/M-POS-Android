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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.mendelev.mpos.core.ui.isMposDarkTheme
import com.mendelev.mpos.domain.settings.*
import com.mendelev.mpos.domain.staff.Employee
import com.mendelev.mpos.domain.warehouse.Supplier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.feature.access.AdminControl

internal data class SettingsDirectory(val employees: List<Employee> = emptyList(),val suppliers: List<Supplier> = emptyList(),val error: String?=null)

/** Pinned renderSettingsScreen/renderAdminPanel, mapped to shared native controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsHome(onUpdates: ()->Unit,onCatalog: (() -> Unit)?,onSuppliers: (() -> Unit)?,
    onInventory: (() -> Unit)?,onStock: (() -> Unit)?,onGallery: (() -> Unit)?,onBack: ()->Unit,updateAvailable: Boolean,onEmployees: (() -> Unit)?=null,onBackup: (() -> Unit)?=null,employees: List<Employee> = emptyList(),suppliers: List<Supplier> = emptyList(),directoryError: String?=null,model: SettingsCatalogViewModel=settingsCatalogModel()) {
    val dictionaries by model.state.collectAsStateWithLifecycle()
    val theme=LocalMposThemeChoice.current
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    val accessModel: com.mendelev.mpos.feature.access.AdminViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
    var panel by rememberSaveable(admin.generation) {mutableStateOf(false)}
    var planned by rememberSaveable {mutableStateOf<String?>(null)}
    var entry by remember(admin.generation) {mutableStateOf<SettingEntry?>(null)}
    var entryRevision by remember(admin.generation) {mutableStateOf(0L)}
    var plannedAccess by remember {mutableStateOf(false)}
    fun edit(row: SettingEntry) {
        if(!admin.active) {plannedAccess=true;return}
        model.dismiss();entryRevision=dictionaries.catalog.revision;entry=row
    }
    LaunchedEffect(admin.active) {if(!admin.active) panel=false}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        if(LocalMposTopNavigation.current!=null) MposAppBar {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start=26.dp,end=26.dp,top=22.dp,bottom=40.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom=18.dp).testTag("settingsHeader"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Настройки",Modifier.weight(1f).heightIn(min=48.dp).wrapContentHeight().testTag("settingsTitle"),fontSize=24.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AdminControl(model=accessModel,compact=true,onPanel={panel=true},iconOnly=true)
                    Surface(onClick=onUpdates,modifier=Modifier.size(48.dp).testTag("settingsUpdatesInfo").semantics {contentDescription="Проверить обновления приложения"},shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                        Box(contentAlignment=Alignment.Center) {MposPosIcon("refresh",Modifier.size(22.dp))}
                    }
                    Surface(modifier=Modifier.height(48.dp).testTag("settingsThemeControl"),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            CompositionLocalProvider(LocalContentColor provides if(!theme.dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {MposPosIcon("sun",Modifier.size(20.dp))}
                            Switch(checked=theme.dark,onCheckedChange=theme.change,enabled=!theme.busy,modifier=Modifier.testTag("settingsTheme"))
                            CompositionLocalProvider(LocalContentColor provides if(theme.dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {MposPosIcon("moon",Modifier.size(20.dp))}
                        }
                    }
                }
            }
            theme.error?.let {MposNotice(it,true)}
            if(entry==null) dictionaries.error?.let {MposNotice(it,true)}
            if(updateAvailable) Text("Доступно обновление — откройте «Обновления»",Modifier.padding(bottom=10.dp),color=MaterialTheme.colorScheme.primary)
            SettingsMainAction("Товары",{onCatalog?.invoke()},Modifier.testTag("settingsCatalog"),enabled=onCatalog!=null)
            Spacer(Modifier.height(10.dp))
            SettingsMainAction("Резервная копия",{onBackup?.invoke() ?: run {planned="Резервная копия"}},Modifier.testTag("settingsBackup"))
            Spacer(Modifier.height(16.dp))
            directoryError?.let {MposNotice(it,true);Spacer(Modifier.height(12.dp))}
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("settingsGrid")) {
                val wide=maxWidth>700.dp
                val cards: List<@Composable ()->Unit> = listOf(
                    {
                        SettingsCard("Сотрудники","Кассиры выбираются при открытии смены. Администратор работает через отдельную сессию в настройках.",onAction=onEmployees,tag="settingsEmployees") {
                            if(employees.isEmpty()) SettingsEmpty("Сотрудников пока нет.")
                            employees.forEach {employee->SettingsDirectoryRow(employee.name,employee.phone.ifBlank {"Телефон не указан"},if(employee.role=="admin") "Админ" else null,"Изменить",onEmployees)}
                        }
                    },
                    {
                        SettingsCard("Поставщики и товары","Здесь настраиваются поставщики и товары, которые закреплены за каждым поставщиком.",onAction=onSuppliers,tag="settingsSuppliers") {
                            if(suppliers.isEmpty()) SettingsEmpty("Поставщиков пока нет.")
                            suppliers.forEach {supplier->SettingsDirectoryRow(supplier.name,"${supplier.productIds.size} закрепл. товаров",null,"Товары",onSuppliers)}
                        }
                    },
                    {SettingsDictionaryCard(SettingKind.DELIVERY,dictionaries,admin.active,{edit(it)},{row->model.delete(row,dictionaries.catalog.revision) {}})},
                    {SettingsDictionaryCard(SettingKind.DISCOUNT,dictionaries,admin.active,{edit(it)},{row->model.delete(row,dictionaries.catalog.revision) {}})},
                    {SettingsDictionaryCard(SettingKind.RECEIPT_LABEL,dictionaries,admin.active,{edit(it)},{row->model.delete(row,dictionaries.catalog.revision) {}})}
                )
                // CSS grid fills rows; both cells in a row have the same height.
                Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(wide) cards.chunked(2).forEach {row->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.weight(1.2f).fillMaxHeight(),propagateMinConstraints=true) {row[0]()}
                            Box(Modifier.weight(.8f).fillMaxHeight(),propagateMinConstraints=true) {if(row.size>1) row[1]()}
                        }
                    } else cards.forEach {it()}
                }
            }
            if(com.mendelev.mpos.BuildConfig.DEBUG) onGallery?.let {Spacer(Modifier.height(16.dp));MposButton("Компоненты",it,style=ActionStyle.Secondary)}
        }
    }

    if(entry!=null && admin.active) SettingsEntryDialog(entry!!,entryRevision,model) {entry=null;model.dismiss()}
    if(plannedAccess) MposDialog("Администрирование",{plannedAccess=false}) {
        Text("Для изменения справочников войдите в администрирование.")
        MposButton("Понятно",{plannedAccess=false})
    }

    if(panel && admin.active) MposDialog("Панель администратора",{panel=false},maxWidth=820.dp) {
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            MposButton("Завершить администрирование",{panel=false;accessModel.logout()},Modifier.fillMaxWidth().testTag("adminLogout"),style=ActionStyle.Secondary)
            for((title,description) in listOf("Программа лояльности" to "Программы, подарки и база клиентов.","Сетевые настройки" to "Подключение сайта, синхронизация, принтер и Telegram.","Реквизиты организации" to "Название заведения, юридическое лицо и адрес.")) {
                SettingsCard(title,description,planned=true,onInfo={panel=false;planned=title}) {}
            }
            SettingsCard("Инвентаризация","График пересчёта, проведение и история уже доступны.") {
                onInventory?.let {action->MposButton("Открыть инвентаризацию",{panel=false;action()},Modifier.testTag("settingsInventory"),style=ActionStyle.Outline)}
            }
            SettingsCard("Складской учёт","Остатки и движения товаров уже доступны. Расширенные отчёты будут добавлены.") {
                onStock?.let {action->MposButton("Открыть склад",{panel=false;action()},Modifier.testTag("settingsStock"),style=ActionStyle.Outline)}
            }
        }
    }
    planned?.let {title->MposDialog(title,{planned=null}) {
        Text("Будет реализовано",Modifier.testTag("settingsPlannedNotice"),style=MaterialTheme.typography.titleMedium)
        Text("Этот раздел пока не подключён. Его реализация запланирована в следующих этапах разработки.")
        MposButton("Понятно",{planned=null})
    }}
}

@Composable private fun SettingsMainAction(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,primary: Boolean=false) {
    val c=MaterialTheme.colorScheme
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(999.dp),
        color=if(primary) c.primary else c.surface,contentColor=if(primary) c.onPrimary else c.onSurface,
        border=if(primary) null else BorderStroke(1.5.dp,c.outline)) {
        Box(Modifier.padding(horizontal=18.dp,vertical=14.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=16.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun SettingsRoundAction(label: String,description: String,onClick: ()->Unit,modifier: Modifier=Modifier) {
    Surface(onClick=onClick,modifier=modifier.size(44.dp).semantics {contentDescription=description},shape=RoundedCornerShape(999.dp),border=BorderStroke(1.5.dp,MaterialTheme.colorScheme.outline)) {
        Box(contentAlignment=Alignment.Center) {Text(label,fontSize=22.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun SettingsCard(title: String,description: String,planned: Boolean=false,onInfo: (() -> Unit)?=null,
    onAction: (() -> Unit)?=null,tag: String?=null,content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.ExtraBold)
                val action=onAction ?: onInfo
                if(action!=null) SettingsRoundAction("+",if(onAction!=null) "Открыть: $title" else "Будет подключено: $title",action,if(tag!=null) Modifier.testTag(tag) else Modifier)
            }
            Text(description,Modifier.padding(top=6.dp),fontSize=12.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(planned) SettingsEmpty("Будет реализовано")
            content()
        }
    }
}
@Composable private fun SettingsEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical=22.dp),contentAlignment=Alignment.Center) {Text(text,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable private fun SettingsDirectoryRow(name: String,subtitle: String,badge: String?,action: String,onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(badge!=null) Text(badge,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
        if(onClick!=null) TextButton(onClick=onClick) {Text(action,fontSize=12.sp)}
    }
    HorizontalDivider()
}

@Composable private fun SettingsDictionaryCard(kind: SettingKind,ui: SettingsCatalogUi,admin: Boolean,onEdit: (SettingEntry)->Unit,onDelete: (SettingEntry)->Unit) {
    val description=when(kind) {
        SettingKind.DISCOUNT->"Создайте варианты скидок, которые можно будет назначать отдельному товару в текущем заказе."
        SettingKind.DELIVERY->"Настройте варианты стоимости. Синхронизация с сайтом будет подключена позже."
        SettingKind.RECEIPT_LABEL->"Быстрые кнопки в окне «Отложить». Название можно изменить перед сохранением чека."
    }
    SettingsCard(settingTitle(kind),description,onAction=if(ui.loading || ui.busy) null else { {onEdit(newSetting(kind))} },tag="settingsAdd:${kind.name}") {
        val rows=ui.catalog.rows(kind)
        if(ui.loading) SettingsEmpty("Загрузка…") else if(rows.isEmpty()) SettingsEmpty(when(kind) {
            SettingKind.DISCOUNT->"Скидок пока нет.";SettingKind.DELIVERY->"Варианты доставки пока не добавлены.";SettingKind.RECEIPT_LABEL->"Шаблоны пока не добавлены."
        })
        rows.forEach {row->
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.name,fontSize=14.sp,fontWeight=FontWeight.Bold)
                    if(kind!=SettingKind.RECEIPT_LABEL) Text(if(row.type=="percent") "${row.value}%" else "${row.value} BYN",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(admin) {
                    if(kind!=SettingKind.DISCOUNT) TextButton({onEdit(row)},enabled=!ui.busy,modifier=Modifier.testTag("settingsEdit:${row.id}")) {Text("Изменить",fontSize=12.sp)}
                    TextButton({onDelete(row)},enabled=!ui.busy,modifier=Modifier.testTag("settingsDelete:${row.id}")) {Text("×",color=MaterialTheme.colorScheme.error,fontSize=22.sp)}
                }
            }
            HorizontalDivider()
        }
    }
}
