package com.mendelev.mpos.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.data.local.*
import com.mendelev.mpos.domain.settings.*
import java.util.UUID

@Composable internal fun settingsCatalogModel(): SettingsCatalogViewModel {
    val context=LocalContext.current.applicationContext
    return viewModel(key="settings-catalog",factory=object: ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=SettingsCatalogViewModel(SettingsRepository(CatalogDatabase.get(context))) as T
    })
}
internal fun settingTitle(kind: SettingKind)=when(kind) {SettingKind.DISCOUNT->"Скидки";SettingKind.DELIVERY->"Стоимость доставки";SettingKind.RECEIPT_LABEL->"Шаблоны названий чеков"}
internal fun newSetting(kind: SettingKind)=SettingEntry(UUID.randomUUID().toString(),kind,"",if(kind==SettingKind.DISCOUNT) "percent" else "")
@Composable internal fun SettingsEntryDialog(row: SettingEntry,revision: Long,model: SettingsCatalogViewModel,onClose: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    var name by remember(row.id) {mutableStateOf(row.name)}
    var type by remember(row.id) {mutableStateOf(row.type)}
    var value by remember(row.id) {mutableStateOf(row.value)}
    val editing=ui.catalog.entries.any {it.id==row.id}
    val title=when(row.kind) {
        SettingKind.DISCOUNT->if(editing) "Изменить скидку" else "Новая скидка"
        SettingKind.DELIVERY->if(editing) "Изменить стоимость доставки" else "Новая стоимость доставки"
        SettingKind.RECEIPT_LABEL->if(editing) "Изменить шаблон" else "Новый шаблон названия чека"
    }
    MposDialog(title,onClose,closable=!ui.busy) {
        MposField(name,{name=it},"Название",Modifier.testTag("settingName"),!ui.busy)
        if(row.kind==SettingKind.DISCOUNT) MposSelect("Тип скидки",type,listOf("percent" to "Процент","fixed" to "Фиксированная сумма"),{type=it},!ui.busy,Modifier.testTag("settingDiscountType"))
        if(row.kind!=SettingKind.RECEIPT_LABEL) MposField(value,{value=it},if(row.kind==SettingKind.DELIVERY) "Стоимость, BYN" else "Размер скидки",Modifier.testTag("settingValue"),!ui.busy,numeric=true)
        ui.error?.let {MposNotice(it,true)}
        if(editing && com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle().value.active) MposButton("Удалить",{model.delete(row,revision,onClose)},Modifier.fillMaxWidth().testTag("settingsDelete:${row.id}"),!ui.busy,ActionStyle.Danger)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Отмена",onClose,Modifier.weight(1f),!ui.busy,ActionStyle.Secondary)
            MposButton(if(row.kind==SettingKind.DISCOUNT && !editing) "Добавить" else "Сохранить",{model.save(row.copy(name=name,type=type,value=value),revision,onClose)},Modifier.weight(1f).testTag("settingSave"),busy=ui.busy)
        }
    }
}
