package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
    val supplierContext=androidx.compose.ui.platform.LocalContext.current
    val supplierModel: com.mendelev.mpos.feature.suppliers.SupplierViewModel=androidx.lifecycle.viewmodel.compose.viewModel(factory=object: androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.suppliers.SupplierViewModel(com.mendelev.mpos.data.local.SupplierRepository(com.mendelev.mpos.data.local.CatalogDatabase.get(supplierContext.applicationContext))) as T
    })
    val supplierUi by supplierModel.state.collectAsStateWithLifecycle()
    var supplierTarget by rememberSaveable {mutableStateOf<String?>(null)}
    var supplierOpened by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(supplierTarget,supplierUi.pending,supplierUi.blocked,supplierUi.form) {
        if(supplierTarget!=null && !supplierOpened && !supplierUi.pending && !supplierUi.blocked) {
            supplierModel.edit(supplierTarget!!.takeIf {it.isNotEmpty()});supplierOpened=true
        } else if(supplierOpened && supplierUi.form==null && !supplierUi.pending) {supplierTarget=null;supplierOpened=false}
    }
    if(supplierTarget!=null) com.mendelev.mpos.feature.suppliers.SupplierScreen(supplierModel,embedded=true) {supplierTarget=null;supplierOpened=false}
    val employeeModel: com.mendelev.mpos.feature.staff.EmployeeViewModel=androidx.lifecycle.viewmodel.compose.viewModel(factory=object: androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = com.mendelev.mpos.feature.staff.EmployeeViewModel(com.mendelev.mpos.data.local.EmployeeRepository(com.mendelev.mpos.data.local.CatalogDatabase.get(supplierContext.applicationContext))) as T
    })
    val employeeUi by employeeModel.state.collectAsStateWithLifecycle()
    var employeeTarget by rememberSaveable {mutableStateOf<String?>(null)}
    var employeeOpened by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(employeeTarget,employeeUi.pending,employeeUi.blocked,employeeUi.form) {
        if(employeeTarget!=null && !employeeOpened && !employeeUi.pending && !employeeUi.blocked) {
            employeeModel.edit(employeeTarget!!.takeIf {it.isNotEmpty()});employeeOpened=true
        } else if(employeeOpened && employeeUi.form==null && !employeeUi.pending) {employeeTarget=null;employeeOpened=false}
    }
    if(employeeTarget!=null) com.mendelev.mpos.feature.staff.EmployeeScreen(employeeModel,embedded=true) {employeeTarget=null;employeeOpened=false}
    val dictionaries by model.state.collectAsStateWithLifecycle()
    val theme=LocalMposThemeChoice.current
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    val accessModel: com.mendelev.mpos.feature.access.AdminViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
    val navigation=LocalMposTopNavigation.current
    var network by rememberSaveable {mutableStateOf(false)}
    var panel by rememberSaveable(admin.generation) {mutableStateOf(false)}
    var planned by rememberSaveable {mutableStateOf<String?>(null)}
    var entry by remember(admin.generation) {mutableStateOf<SettingEntry?>(null)}
    var entryRevision by remember(admin.generation) {mutableStateOf(0L)}
    var plannedAccess by remember {mutableStateOf(false)}
    fun edit(row: SettingEntry) {
        if(!admin.active) {plannedAccess=true;return}
        model.dismiss();entryRevision=dictionaries.catalog.revision;entry=row
    }
    LaunchedEffect(admin.active) {if(!admin.active) {panel=false;network=false}}
    if(network && admin.active) {NetworkSettingsScreen {network=false};return}
    if(!(panel && admin.active)) Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        if(LocalMposTopNavigation.current!=null) MposAppBar {}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start=26.dp,end=26.dp,top=22.dp,bottom=40.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom=18.dp).testTag("settingsHeader"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Настройки",Modifier.weight(1f).heightIn(min=48.dp).wrapContentHeight().testTag("settingsTitle"),fontSize=24.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AdminControl(model=accessModel,compact=true,onPanel={panel=true},iconOnly=true)
                    Surface(onClick=onUpdates,modifier=Modifier.size(48.dp).testTag("settingsUpdatesInfo").semantics {contentDescription="Проверить обновления приложения"},shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                        Box(contentAlignment=Alignment.Center) {CompositionLocalProvider(LocalContentColor provides if(updateAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) {MposPosIcon("refresh",Modifier.size(22.dp))}}
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

            SettingsMainAction("Товары",{onCatalog?.invoke()},Modifier.testTag("settingsCatalog"),enabled=onCatalog!=null)
            Spacer(Modifier.height(10.dp))
            SettingsMainAction("Резервная копия",{onBackup?.invoke() ?: run {planned="Резервная копия"}},Modifier.testTag("settingsBackup"))
            Spacer(Modifier.height(16.dp))
            directoryError?.let {MposNotice(it,true);Spacer(Modifier.height(12.dp))}
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("settingsGrid")) {
                val wide=maxWidth>700.dp
                val cards: List<@Composable ()->Unit> = listOf(
                    {
                        SettingsCard("Сотрудники","Кассиры выбираются при открытии смены. Администратор работает через отдельную сессию в настройках.",onAction={employeeTarget="";employeeOpened=false;employeeModel.refresh()},tag="settingsEmployees") {
                            if((employeeUi.snapshot?.employees ?: employees).isEmpty()) SettingsEmpty("Сотрудников пока нет.")
                            (employeeUi.snapshot?.employees ?: employees).forEach {employee->
                                SettingsDirectoryRow(employee.name,employee.phone,Modifier.testTag("employee:${employee.id}"),if(admin.active) { {employeeTarget=employee.id;employeeOpened=false;employeeModel.refresh()} } else null)
                            }
                            employeeUi.message?.takeIf {employeeTarget==null}?.let {MposNotice(it,true)}
                        }
                    },
                    {
                        SettingsCard("Поставщики товаров","Здесь настраиваются поставщики и товары, которые закреплены за каждым поставщиком.",onAction={supplierTarget="";supplierOpened=false;supplierModel.refresh()},tag="settingsSuppliers") {
                            if((supplierUi.snapshot?.suppliers ?: suppliers).isEmpty()) SettingsEmpty("Поставщиков пока нет.")
                            (supplierUi.snapshot?.suppliers ?: suppliers).forEach {supplier->
                                SettingsDirectoryRow(supplier.name,"${supplier.productIds.size} закрепл. товаров",Modifier.testTag("supplier:${supplier.id}"),if(admin.active) { {supplierTarget=supplier.id;supplierOpened=false;supplierModel.refresh()} } else null)
                            }
                        }
                    },
                    {SettingsDictionaryCard(SettingKind.DELIVERY,dictionaries,admin.active,{edit(it)},{row->model.delete(row,dictionaries.catalog.revision) {}})},
                    {SettingsDictionaryCard(SettingKind.DISCOUNT,dictionaries,admin.active,{edit(it)},{row->model.delete(row,dictionaries.catalog.revision) {}})},
                    {SettingsDictionaryCard(SettingKind.RECEIPT_LABEL,dictionaries,admin.active,{edit(it)},{row->model.delete(row,dictionaries.catalog.revision) {}})}
                )
                if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    repeat(2) {column->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        cards.filterIndexed {index,_->index%2==column}.forEach {it()}
                    }}
                } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {cards.forEach {it()}}
            }

        }
    }

    if(entry!=null && admin.active) SettingsEntryDialog(entry!!,entryRevision,model) {entry=null;model.dismiss()}
    if(plannedAccess) MposDialog("Администрирование",{plannedAccess=false}) {
        Text("Для изменения справочников войдите в администрирование.")
        MposButton("Понятно",{plannedAccess=false})
    }

    if(panel && admin.active) {
        androidx.activity.compose.BackHandler {panel=false}
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("adminPanelScreen")) {
            if(navigation!=null) MposAppBar {}
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    MposButton("Назад",{panel=false},style=ActionStyle.Outline)
                    Text("Панель администратора",fontSize=24.sp,fontWeight=FontWeight.ExtraBold)
                }
                Text("Версия ${com.mendelev.mpos.BuildConfig.VERSION_NAME}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val modules=listOf(
                    AdminModule("loyalty","Программа лояльности","Программы, правила начисления, подарки и база клиентов. Будет реализовано.") {planned="Программа лояльности"},
                    AdminModule("network","Сетевые настройки","Синхронизация, подключение принтера и сетевые конфигурации.","settingsNetwork") {network=true},
                    AdminModule("company","Реквизиты организации","Название заведения, юридическое лицо, адрес и адрес доставки. Будет реализовано.") {planned="Реквизиты организации"},
                    AdminModule("inventory","Инвентаризация","График пересчёта, список товаров, проведение и история.","settingsInventory") {panel=false;onInventory?.invoke()},
                    AdminModule("warehouse","Складской учёт","Остатки, движение товаров, поставщики и складские отчёты.","settingsStock") {panel=false;onStock?.invoke()},
                    AdminModule("analytics","Аналитика","Продажи, возвраты, прибыль и отчёты.","settingsAnalytics") {panel=false;navigation?.navigate?.invoke("analytics")}
                )
                BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max=1080.dp)) {
                    val columns=if(maxWidth>760.dp) 2 else 1
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        modules.chunked(columns).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {row.forEach {module->AdminModuleCard(module,Modifier.weight(1f))}}}
                    }
                }
                MposButton("Закрыть сессию администрирования",{panel=false;accessModel.logout()},Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("adminLogout"),style=ActionStyle.Danger)
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
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(22.dp),
        color=if(primary) c.primary else c.surface,contentColor=if(primary) c.onPrimary else c.onSurface,
        border=if(primary) null else BorderStroke(1.5.dp,c.outline)) {
        Box(Modifier.padding(horizontal=18.dp,vertical=14.dp),contentAlignment=Alignment.Center) {Text(label,fontSize=16.sp,fontWeight=FontWeight.Bold)}
    }
}
@Composable internal fun SettingsRoundAction(label: String,description: String,onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier.size(48.dp).semantics {contentDescription=description},shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Box(contentAlignment=Alignment.Center) {MposPosIcon(when(label) {"+"->"add";"×"->"close";else->"edit"},Modifier.size(22.dp))}
    }
}
@Composable private fun SettingsCard(title: String,description: String,planned: Boolean=false,onInfo: (() -> Unit)?=null,
    onAction: (() -> Unit)?=null,tag: String?=null,content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.ExtraBold)
                val action=onAction ?: onInfo
                if(action!=null && Administration.session.state.collectAsStateWithLifecycle().value.active) SettingsRoundAction("+",if(onAction!=null) "Открыть: $title" else "Будет подключено: $title",action,if(tag!=null) Modifier.testTag(tag) else Modifier)
            }

            if(planned) SettingsEmpty("Будет реализовано")
            content()
        }
    }
}
@Composable private fun SettingsEmpty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical=22.dp),contentAlignment=Alignment.Center) {Text(text,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
@Composable internal fun SettingsDirectoryRow(name: String,subtitle: String,modifier: Modifier=Modifier,onClick: (() -> Unit)?) {
    Column(settingsRowModifier(modifier,onClick),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(name,fontSize=14.sp,fontWeight=FontWeight.Bold)
        if(subtitle.isNotBlank()) Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun settingsRowModifier(modifier: Modifier=Modifier,onClick: (() -> Unit)?): Modifier = modifier
    .fillMaxWidth().padding(top=8.dp).clip(RoundedCornerShape(12.dp))
    .background(MaterialTheme.colorScheme.onSurface.copy(alpha=.035f))
    .clickable(enabled=onClick!=null) {onClick?.invoke()}.padding(horizontal=14.dp,vertical=12.dp)

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
            Row(settingsRowModifier(Modifier.testTag("settingsEdit:${row.id}"),if(admin && !ui.busy) { {onEdit(row)} } else null),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.name,fontSize=14.sp,fontWeight=FontWeight.Bold)
                    if(kind!=SettingKind.RECEIPT_LABEL) Text(if(row.type=="percent") "${row.value}%" else "${row.value} BYN",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(admin) {
                    SettingsRoundAction("×","Удалить ${row.name}",{onDelete(row)},Modifier.offset(x=14.dp).testTag("settingsDelete:${row.id}"),enabled=!ui.busy)
                }
            }
        }
    }
}

private data class AdminModule(val icon: String,val title: String,val description: String,val tag: String="",val action: ()->Unit)
@Composable private fun AdminModuleCard(module: AdminModule,modifier: Modifier) {
    Surface(onClick=module.action,modifier=modifier.heightIn(min=116.dp).testTag(module.tag),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(Modifier.size(46.dp),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {Box(contentAlignment=Alignment.Center) {MposPosIcon(module.icon,Modifier.size(25.dp))}}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(module.title,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(module.description,fontSize=13.sp,lineHeight=19.5.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MposPosIcon("next",Modifier.size(18.dp))
        }
    }
}
