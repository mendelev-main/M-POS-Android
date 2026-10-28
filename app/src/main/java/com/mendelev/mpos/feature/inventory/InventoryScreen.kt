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

@Composable fun InventoryScreen(model: InventoryViewModel,onBack: ()->Unit,onWork: ()->Unit={},work: Boolean=false,onPause: ()->Unit=onBack) {
    val ui by model.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null
    var discard by rememberSaveable {mutableStateOf(false)}
    var start by rememberSaveable {mutableStateOf<String?>(null)}
    var fixing by rememberSaveable {mutableStateOf<String?>(null)}
    var openAfterStart by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    val close={if(ui.countForm!=null) model.closeCount() else if(!locked) {if(ui.dirty) discard=true else onBack()}}
    BackHandler {close()}
    LaunchedEffect(model,work) {model.refresh()}
    LaunchedEffect(ui.snapshot?.document?.draft?.id,openAfterStart,ui.pending) {if(openAfterStart && !ui.pending && ui.snapshot?.document?.draft!=null) {openAfterStart=false;onWork()}}
    Column(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) {
        MposAppBar("Инвентаризация",navigationEnabled=!locked && !ui.dirty && start==null && fixing==null) {}
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
                if(work && draft!=null) {
                    InventoryWorkRows(ui,model,{fixing=it},Modifier.weight(1f))
                    MposButton("Продолжить позже",onPause,enabled=!locked && !ui.dirty,style=ActionStyle.Secondary,modifier=Modifier.testTag("inventoryPause"))
                    Text("Завершение и отмена оставшихся строк будут подключены следующим блоком.")
                } else {
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(draft!=null) item {
                        MposNotice("Пересчёт сохранён · ${if(draft.type=="adhoc") "Внеплановый" else "Плановый"} · ${draft.scheduledDate}")
                        Text("Товаров: ${draft.products.size}",Modifier.testTag("inventoryActive"))
                        MposButton("Продолжить пересчёт",onWork,enabled=!locked && !ui.dirty,modifier=Modifier.testTag("inventoryContinue"))
                    }
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
    }
    val countForm=ui.countForm
    if(countForm!=null) MposDialog(if(countForm.discarding) "Закрыть без сохранения?" else "Фактический остаток",{model.closeCount()},!locked) {
        if(ui.message!=null) MposNotice(ui.message!!,true)
        if(countForm.discarding) {
            Text("Введённое значение ещё не сохранено.")
            MposButton("Продолжить редактирование",model::continueCount,enabled=!locked,style=ActionStyle.Secondary)
            MposButton("Закрыть без сохранения",{model.closeCount(true)},enabled=!locked,style=ActionStyle.Danger)
        } else {
            Text(countForm.product.name,style=MaterialTheme.typography.titleMedium)
            Text("Учётный остаток: ${countForm.product.stock} ${unitLabel(countForm.product.unit)}. Пересчитайте товар и введите новое значение.")
            MposField(countForm.raw,model::countRaw,"Факт, ${unitLabel(countForm.product.unit)}",Modifier.testTag("inventoryActual"),!locked,numeric=true)
            MposButton("Сохранить пересчёт",model::saveCount,enabled=!locked && !ui.blocked,modifier=Modifier.testTag("inventoryCountSave"))
            MposButton("Пересчитать заново",model::recount,enabled=!locked,style=ActionStyle.Secondary)
            if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,enabled=!ui.pending,modifier=Modifier.testTag("inventoryCountRetry"))
        }
    }
    val fixCount=ui.snapshot?.document?.draft?.counts?.find {it.product.id==fixing && it.fixed==null}
    if(fixing!=null && fixCount!=null) MposDialog("Зафиксировать остаток?",{fixing=null},!locked) {
        Text("${fixCount.product.name}: ${fixCount.product.stock} → ${InventoryRules.actual(fixCount.raw)} ${unitLabel(fixCount.product.unit)}. Разница: ${InventoryRules.delta(fixCount)}.")
        Text("Это изменение склада. Повторная команда не применит разницу второй раз.")
        MposButton("Фиксировать",{val id=fixing!!;fixing=null;model.fix(id)},enabled=!locked && !ui.blocked,modifier=Modifier.testTag("inventoryFixConfirm"))
    }
    if(discard) MposDialog("Закрыть без сохранения?",{discard=false},!locked) {
        Text("Изменения настроек ещё не сохранены. Активный пересчёт останется.")
        MposButton("Продолжить редактирование",{discard=false},enabled=!locked,style=ActionStyle.Secondary)
        MposButton("Закрыть без сохранения",{model.discard();discard=false;onBack()},enabled=!locked,style=ActionStyle.Danger,modifier=Modifier.testTag("inventoryDiscard"))
    }
    if(start!=null) MposDialog("Начать пересчёт?",{start=null},!locked) {
        Text("Список товаров будет взят из сохранённых настроек. Изменение настроек позже не заменит этот список. Остатки пока не меняются.")
        MposButton("Начать",{val type=start!!;start=null;openAfterStart=true;model.start(type)},enabled=!locked,modifier=Modifier.testTag("inventoryStartConfirm"))
    }
}

@Composable private fun InventoryWorkRows(ui: InventoryUi,model: InventoryViewModel,onFix: (String)->Unit,modifier: Modifier) {
    val snapshot=ui.snapshot ?: return;val draft=snapshot.document.draft ?: return
    val locked=ui.pending || ui.blocked || ui.unresolved!=null || ui.dirty
    LazyColumn(modifier,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("Зафиксировано: ${draft.counts.count {it.fixed!=null}} из ${draft.products.size}",Modifier.testTag("inventoryActive"))}
        draft.products.groupBy {it.category}.forEach {(category,products)->
            item(key="category:$category") {Text(category,style=MaterialTheme.typography.titleMedium)}
            items(products,key={it.id}) {p->
                val count=draft.counts.find {it.product.id==p.id};val fixed=count?.fixed
                val live=snapshot.products.find {it.id==p.id}
                Surface(shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(fixed?.product?.name ?: live?.name ?: p.name,style=MaterialTheme.typography.titleMedium)
                        if(fixed!=null) {
                            Text("Зафиксировано: ${fixed.actual} ${unitLabel(fixed.product.unit)} · разница ${fixed.delta}",Modifier.testTag("inventoryFixed:${p.id}"))
                            Text("Учётный остаток при фиксации: ${fixed.product.stock}. Последующие движения не меняют этот снимок.")
                        } else {
                            Text("Учётный остаток: ${live?.stock ?: "недоступен"} ${unitLabel(p.unit)}")
                            if(count!=null) Text("Сохранённый факт: ${count.raw}",Modifier.testTag("inventorySaved:${p.id}"))
                            val fresh=count!=null && count.generation==snapshot.generation && live!=null && InventoryRules.sameStock(count.product,live)
                            if(count!=null && !fresh) Text("Нужно пересчитать товар заново: остаток изменился или приложение перезапущено.")
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                MposButton(if(count==null) "Ввести факт" else "Пересчитать",{model.openCount(p.id)},enabled=!locked && live!=null,style=ActionStyle.Secondary,modifier=Modifier.testTag("inventoryCount:${p.id}"))
                                MposButton("Фиксировать",{onFix(p.id)},enabled=!locked && fresh,modifier=Modifier.testTag("inventoryFix:${p.id}"))
                            }
                        }
                    }
                }
            }
        }
    }
}
