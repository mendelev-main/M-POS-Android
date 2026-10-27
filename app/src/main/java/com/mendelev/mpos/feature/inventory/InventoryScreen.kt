package com.mendelev.mpos.feature.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import java.time.LocalDate

@Composable fun InventoryScreen(model: InventoryViewModel,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null
    var discard by rememberSaveable {mutableStateOf(false)}
    var start by rememberSaveable {mutableStateOf<String?>(null)}
    var query by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    val close={if(!locked) {if(ui.dirty) discard=true else onBack()}}
    BackHandler {close()}
    LaunchedEffect(model) {model.refresh()}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Инвентаризация",navigationEnabled=!locked && !ui.dirty && start==null) {}
        Column(Modifier.padding(20.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton("Настройки",close,enabled=!locked,style=ActionStyle.Secondary,modifier=Modifier.testTag("inventoryBack"))
                Text("Инвентаризация",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                MposButton("Перечитать",model::refresh,enabled=!locked && !ui.dirty,style=ActionStyle.Secondary)
            }
            if(ui.message!=null) MposNotice(ui.message!!,ui.blocked)
            if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("inventoryRetry"))
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            val s=ui.snapshot;val form=ui.form
            if(s!=null && form!=null) {
                val draft=s.document.draft
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(draft!=null) item {
                        MposNotice("Пересчёт сохранён · ${if(draft.type=="adhoc") "Внеплановый" else "Плановый"} · ${draft.scheduledDate}")
                        Text("Товаров: ${draft.products.size}",Modifier.testTag("inventoryActive"))
                        Text("Ввод и фиксация фактических остатков будут подключены следующим блоком. Склад ещё не изменён.")
                    }
                    items(draft?.products.orEmpty(),key={"active:${it.id}"}) {p->Text("${p.name} · снимок при запуске ${p.stock} ${unitLabel(p.unit)}")}
                    item {
                        Text("Товары и периодичность",style=MaterialTheme.typography.titleMedium)
                        MposSelect("Периодичность",form.frequency,listOf("weekly" to "Еженедельно","monthly" to "Ежемесячно","quarterly" to "Ежеквартально"),{v->model.change {it.copy(frequency=v)}},!locked && !ui.blocked,Modifier.testTag("inventoryFrequency"))
                        Text("Первые 7 дней периода — окно планового пересчёта.")
                        if(InventoryRules.due(s.document.config,LocalDate.parse(s.today))) Text("Доступен плановый пересчёт",Modifier.testTag("inventoryDue"))
                        MposField(query,{query=it},"Поиск товара",Modifier.testTag("inventorySearch"),!locked)
                        MposSelect("Категория",category,listOf("" to "Все категории")+s.products.map {it.category}.distinct().sorted().map {it to it},{category=it},!locked)
                        val unavailable=form.productIds-s.products.map {it.id}.toSet()
                        unavailable.forEach {id->MposButton("Убрать недоступный товар $id",{model.change {it.copy(productIds=it.productIds-id)}},enabled=!locked,style=ActionStyle.Secondary)}
                    }
                    items(s.products.filter {(category.isEmpty() || it.category==category) && CatalogRules.key(it.name).contains(CatalogRules.key(query))},key={"choose:${it.id}"}) {p->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Checkbox(p.id in form.productIds,{v->model.change {it.copy(productIds=if(v) it.productIds+p.id else it.productIds-p.id)}},enabled=!locked && !ui.blocked,modifier=Modifier.testTag("inventoryProduct:${p.id}"))
                            Column {Text(p.name);Text(p.category,style=MaterialTheme.typography.bodySmall)}
                        }
                    }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposButton("Сохранить настройки",model::save,enabled=!locked && !ui.blocked,modifier=Modifier.testTag("inventorySave"))
                    MposButton("Отменить изменения",model::discard,enabled=!locked && ui.dirty,style=ActionStyle.Secondary)
                    MposButton("Начать внеплановую",{start="adhoc"},enabled=!locked && !ui.blocked && !ui.dirty && s.document.config.enabled && draft==null,style=ActionStyle.Secondary,modifier=Modifier.testTag("inventoryStart"))
                    if(InventoryRules.due(s.document.config,LocalDate.parse(s.today))) MposButton("Начать плановую",{start="scheduled"},enabled=!locked && !ui.blocked && !ui.dirty && draft==null,style=ActionStyle.Secondary)
                }
            }
        }
    }
    if(discard) MposDialog("Закрыть без сохранения?",{discard=false},!locked) {
        Text("Изменения настроек ещё не сохранены. Активный пересчёт останется.")
        MposButton("Продолжить редактирование",{discard=false},enabled=!locked,style=ActionStyle.Secondary)
        MposButton("Закрыть без сохранения",{model.discard();discard=false;onBack()},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("inventoryDiscard"))
    }
    if(start!=null) MposDialog("Начать пересчёт?",{start=null},!locked) {
        Text("Список товаров будет взят из сохранённых настроек. Изменение настроек позже не заменит этот список. Остатки пока не меняются.")
        MposButton("Начать",{val type=start!!;start=null;model.start(type)},enabled=!locked,modifier=Modifier.testTag("inventoryStartConfirm"))
    }
}
