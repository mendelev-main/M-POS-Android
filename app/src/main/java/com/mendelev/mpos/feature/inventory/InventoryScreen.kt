package com.mendelev.mpos.feature.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.warehouse.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun date(raw: String)=LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
private fun money(minor: Long?)=minor?.let(com.mendelev.mpos.domain.shift.ShiftRules::money) ?: "Оценка вне диапазона"
@Composable private fun Caption(text: String)=Text(text,fontSize=13.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
@Composable private fun InventoryCard(content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun InventoryScreen(model: InventoryViewModel,onBack: ()->Unit,onWork: ()->Unit={},work: Boolean=false,onPause: ()->Unit=onBack,history: Boolean=false,onHistory: ()->Unit={}) {
    val administration by com.mendelev.mpos.core.access.Administration.session.state.collectAsStateWithLifecycle()
    val ui by model.state.collectAsStateWithLifecycle()
    val locked=ui.pending || ui.unresolved!=null
    var correction by rememberSaveable(administration.generation) {mutableStateOf<String?>(null)}
    var correctedRaw by rememberSaveable(administration.generation) {mutableStateOf("")}
    var correctedExpected by rememberSaveable(administration.generation) {mutableStateOf("")}
    var correcting by rememberSaveable(administration.generation) {mutableStateOf(false)}
    var summary by rememberSaveable(administration.generation) {mutableStateOf(false)}
    var cancel by rememberSaveable(administration.generation) {mutableStateOf(false)}
    var discard by rememberSaveable(administration.generation) {mutableStateOf(false)}
    var starting by rememberSaveable(administration.generation) {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    var recordId by rememberSaveable {mutableStateOf<String?>(null)}
    val close: ()->Unit={if(!locked) {if(work) model.flushInline(onPause) else if(ui.dirty) discard=true else onBack()}}
    BackHandler(onBack=close)
    LaunchedEffect(model,work,history,administration.generation) {model.refresh()}
    LaunchedEffect(starting,ui.pending,ui.snapshot?.document?.draft?.id) {
        if(starting && !ui.pending && ui.snapshot?.document?.draft!=null) {starting=false;onWork()}
        else if(starting && !ui.pending && ui.message!=null) starting=false
    }
    LaunchedEffect(ui.closedId,ui.pending) {
        if(ui.closedId!=null && !ui.pending) {val wasCancelled=cancel;model.consumeClosed();summary=false;cancel=false;if(work) {if(wasCancelled) onBack() else onPause()}}
    }
    val s=ui.snapshot;val form=ui.form ?: s?.document?.config;val draft=s?.document?.draft
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("inventoryScreen")) {
        MposAppBar("Инвентаризация",navigationEnabled=!locked) {}
        Column(Modifier.padding(22.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(!work) MposPosControl("Назад",close,Modifier.testTag("inventoryBack"),!locked,height=44.dp)
                Column(Modifier.weight(1f).widthIn(min=240.dp)) {
                    Text(if(work && draft?.type=="adhoc") "Внеплановая инвентаризация" else "Инвентаризация",fontSize=22.sp,fontWeight=FontWeight.ExtraBold)
                    if(work && draft!=null) Text("Зафиксировано: ${draft.counts.count {it.fixed!=null}} из ${draft.products.size}",Modifier.testTag("inventoryActive"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(work && draft!=null) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MposPosControl("Продолжить позже",{model.flushInline(onPause)},Modifier.testTag("inventoryPause"),!locked,PosControl.Warning,44.dp,radius=16.dp,fontSize=13f)
                    MposPosControl("Завершить инвентаризацию",{if(InventoryClosingRules.summary(draft).remaining==0) summary=true else model.explainIncomplete()},Modifier.testTag("inventoryComplete"),!locked && ui.canEdit,PosControl.Cash,44.dp,radius=16.dp,fontSize=13f)
                    if(ui.canEdit) MposPosControl("",{cancel=true},Modifier.size(44.dp).testTag("inventoryCancel"),!locked,PosControl.Close,44.dp,radius=16.dp,icon="close")
                }
            }
            if(ui.message!=null && ui.message!="Изменение инвентаризации сохранено") Text(ui.message!!,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
            if(ui.unresolved!=null) MposButton("Проверить результат",model::retry,Modifier.testTag("inventoryRetry"),!ui.pending)
            if(s==null || form==null) {if(ui.pending) CircularProgressIndicator() else MposButton("Повторить",model::refresh)}
            else if(work) {
                if(draft==null) Caption("Нет активной инвентаризации.") else {
                    InventoryWork(ui,model,{id,actual->correction=id;correctedRaw=actual;correctedExpected=actual},Modifier.weight(1f))

                }
            } else LazyColumn(Modifier.weight(1f).testTag("inventorySettingsList"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(!history) {
                    item {
                        InventoryCard {
                            Text("График",fontSize=17.sp,fontWeight=FontWeight.ExtraBold)
                            val schedule=InventorySchedule.describe(form,LocalDate.parse(s.today))
                            Caption(schedule.description+" На проведение даётся 7 дней с начала периода.")
                            Text("Следующая инвентаризация: ${schedule.next?.let {date(it.toString())} ?: "—"}",fontSize=15.sp,fontWeight=FontWeight.ExtraBold)
                            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                listOf("weekly" to "Раз в неделю","monthly" to "Раз в месяц","quarterly" to "Раз в квартал").forEach {(key,label)->
                                    MposPosControl(label,{model.change {it.copy(frequency=key)}},Modifier.weight(1f).testTag("inventoryFrequency:$key"),!locked && ui.canEdit,if(form.frequency==key) PosControl.Cash else PosControl.Secondary,46.dp,fontSize=13f)
                                }
                            }
                            MposPosControl(if(draft==null) "Внеплановая инвентаризация" else "Продолжить инвентаризацию",{
                                if(draft!=null) onWork() else {starting=true;model.startConfigured("adhoc")}
                            },Modifier.fillMaxWidth().testTag("inventoryStart"),!locked && ui.canEdit,PosControl.Secondary,46.dp)
                            if(draft==null && InventoryRules.due(s.document.config,LocalDate.parse(s.today))) MposPosControl("Начать плановую инвентаризацию",{starting=true;model.startConfigured("scheduled")},Modifier.fillMaxWidth().testTag("inventoryScheduled"),!locked && ui.canEdit,PosControl.Cash)
                        }
                    }
                    item {
                        InventoryCard {
                            Text("Товары для инвентаризации",fontSize=17.sp,fontWeight=FontWeight.ExtraBold)
                            Caption("Выберите товары, которые нужно пересчитывать. Товары с остатком ∞ исключены.")
                            MposField(query,{query=it},"Поиск товара",Modifier.testTag("inventorySearch"),!locked)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                (listOf("")+s.products.map {it.category}.distinct().sorted()).forEach {c->MposPosControl(c.ifBlank {"Все"},{category=c},enabled=!locked,kind=if(category==c) PosControl.Cash else PosControl.Outline,height=38.dp,fontSize=12f)}
                            }
                            val rows=s.products.filter {(category.isEmpty() || it.category==category) && CatalogRules.key(it.name).contains(CatalogRules.key(query))}.sortedBy {CatalogRules.key(it.name)}
                            if(rows.isEmpty()) Caption(if(s.products.isEmpty()) "Нет товаров с учётом остатков" else "Товары не найдены")
                            rows.forEach {p->
                                Row(Modifier.fillMaxWidth().clickable(enabled=!locked && ui.canEdit) {model.change {it.copy(productIds=if(p.id in it.productIds) it.productIds-p.id else it.productIds+p.id)}}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Checkbox(p.id in form.productIds,{v->model.change {it.copy(productIds=if(v) it.productIds+p.id else it.productIds-p.id)}},enabled=!locked && ui.canEdit,modifier=Modifier.testTag("inventoryProduct:${p.id}"))
                                    Column {Text(p.name,fontSize=14.sp,fontWeight=FontWeight.Bold);Caption("Остаток: ${p.stock} ${unitLabel(p.unit)}")}
                                }
                            }
                            (form.productIds-s.products.map {it.id}.toSet()).forEach {id->MposButton("Убрать недоступный товар",{model.change {it.copy(productIds=it.productIds-id)}},enabled=!locked && ui.canEdit,style=ActionStyle.Secondary)}
                            MposPosControl("Сохранить настройки",model::save,Modifier.fillMaxWidth().testTag("inventorySave"),!locked && ui.canEdit,PosControl.Cash,46.dp)
                        }
                    }
                }
                item {
                    InventoryCard {
                        Text("История",fontSize=17.sp,fontWeight=FontWeight.ExtraBold)
                        if(s.document.history.isEmpty()) Caption("Инвентаризаций пока не было")
                        s.document.history.asReversed().take(50).forEach {r->
                            val totals=InventoryClosingRules.summary(r.draft)
                            Column(Modifier.fillMaxWidth().clickable {recordId=r.id}.padding(vertical=10.dp).testTag("inventoryRecord:${r.id}"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(date(r.date)+(if(r.draft.type=="adhoc") " · Внеплановая" else "")+(if(r.cancelled) " · Отменена" else ""),fontWeight=FontWeight.Bold,fontSize=14.sp)
                                Caption(if(r.cancelled) "Фиксации сохранены: ${totals.fixed}; не зафиксировано: ${totals.remaining}" else "${totals.fixed} товаров · расхождений: ${totals.shortages+totals.surpluses} · недостач: ${totals.shortages}")
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(ui.pending,correcting) {
        if(correcting && !ui.pending) {
            if(ui.snapshot?.document?.draft?.counts?.find {it.product.id==correction}?.effectiveActual==runCatching {InventoryRules.actual(correctedRaw)}.getOrNull()) correction=null
            correcting=false
        }
    }
    if(administration.active && correction!=null) MposDialog("Исправить остаток",{if(!locked) correction=null},!locked) {
        val c=draft?.counts?.find {it.product.id==correction}
        Text(c?.product?.name ?: "",fontWeight=FontWeight.Bold)
        Caption("Зафиксировано: $correctedExpected ${c?.product?.unit?.let(::unitLabel) ?: ""}")
        MposField(correctedRaw,{correctedRaw=it},"Верное количество при пересчёте",Modifier.testTag("inventoryCorrectionActual"),!locked,numeric=true)
        Caption("Исправьте количество, которое было при фиксации. Продажи и приёмки после неё сохранятся. Для нового пересчёта текущего склада начните отдельную инвентаризацию.")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Отмена",{correction=null},Modifier.weight(1f),!locked,ActionStyle.Secondary)
            MposButton("Сохранить исправление",{correcting=true;model.correct(correction!!,correctedExpected,correctedRaw)},Modifier.weight(1f).testTag("inventoryCorrectionSave"),!locked && ui.canEdit)
        }
    }
    if(administration.active && summary && draft!=null) MposDialog("Сводка инвентаризации",{summary=false},!locked,wide=true) {
        InventorySummaryContent(draft)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Вернуться",{summary=false},Modifier.weight(1f),!locked,ActionStyle.Secondary)
            MposButton("Завершить инвентаризацию",{model.finish(false)},Modifier.weight(1f).testTag("inventoryFinishConfirm"),!locked && ui.canEdit)
        }
    }
    if(administration.active && cancel && draft!=null) MposDialog("Отменить инвентаризацию?",{cancel=false},!locked) {
        Caption("Незафиксированный пересчёт будет прекращён. Уже зафиксированные остатки (${draft.counts.count {it.fixed!=null}}) останутся на складе и в истории. Последующие продажи и приёмки не откатываются.")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            MposButton("Нет, продолжить",{cancel=false},Modifier.weight(1f),!locked,ActionStyle.Secondary)
            MposButton("Да, отменить",{model.finish(true)},Modifier.weight(1f).testTag("inventoryFinishConfirm"),!locked && ui.canEdit,ActionStyle.Danger)
        }
    }
    if(discard) MposDialog("Закрыть без сохранения?",{discard=false},!locked) {
        Caption("Изменения настроек ещё не сохранены.")
        MposButton("Продолжить редактирование",{discard=false},style=ActionStyle.Secondary)
        MposButton("Закрыть без сохранения",{model.discard();discard=false;onBack()},Modifier.testTag("inventoryDiscard"),style=ActionStyle.Danger)
    }
    s?.document?.history?.find {it.id==recordId}?.let {r->MposDialog(if(r.cancelled) "Инвентаризация отменена" else "Сводка инвентаризации",{recordId=null},wide=true) {Text((if(r.cancelled) "Отменена" else "Завершена")+" · "+date(r.date),Modifier.testTag("inventoryRecordStatus"));InventorySummaryContent(r.draft)}}
}

@Composable private fun InventoryWork(ui: InventoryUi,model: InventoryViewModel,onCorrect: (String,String)->Unit,modifier: Modifier) {
    val s=ui.snapshot!!;val draft=s.document.draft!!
    val enabled=ui.canEdit && (!ui.blocked || ui.pending) && (ui.unresolved==null || ui.pending)
    LazyColumn(modifier.fillMaxWidth().testTag("inventoryWorkList"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            draft.products.groupBy {it.category}.forEach {(category,products)->
                item(key="category:$category") {
                  Surface(Modifier.fillMaxWidth().testTag("inventoryCategory:$category"),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
                   Column(Modifier.padding(horizontal=16.dp,vertical=8.dp)) {
                    Row(Modifier.padding(top=12.dp,bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {Text(category,fontSize=14.sp,fontWeight=FontWeight.ExtraBold);Caption("${products.size}")}
                products.forEach {p->
                    val count=draft.counts.find {it.product.id==p.id};val fixed=count?.fixed;val live=s.products.find {it.id==p.id}
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical=10.dp)) {
                        val name: @Composable ()->Unit={
                            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(p.name,fontSize=14.sp,fontWeight=FontWeight.Bold)
                                Caption("${if(fixed==null) "Учётный" else "Расчётный"} остаток: ${fixed?.product?.stock ?: live?.stock ?: "—"} ${unitLabel(p.unit)}")
                                if(fixed!=null) Text("Разница: ${if(count!!.effectiveDelta.toBigDecimal().signum()>0) "+" else ""}${count!!.effectiveDelta} ${unitLabel(p.unit)}",fontSize=12.sp,color=if(count!!.effectiveDelta.toBigDecimal().signum()<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                        val controls: @Composable ()->Unit={
                            if(fixed!=null) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {Caption("Зафиксированный остаток");Text("${count!!.effectiveActual} ${unitLabel(p.unit)}",Modifier.testTag("inventoryFixed:${p.id}"),fontSize=16.sp,fontWeight=FontWeight.ExtraBold);MposPosControl("Изменить",{onCorrect(p.id,count!!.effectiveActual)},Modifier.testTag("inventoryCorrect:${p.id}"),enabled && !ui.pending,PosControl.Secondary,44.dp,fontSize=13f)}
                            else Column(Modifier.widthIn(max=360.dp)) {
                                val input=ui.inlineInputs[p.id];val raw=input?.raw ?: count?.raw ?: ""
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Caption("Факт, ${unitLabel(p.unit)}")
                                    OutlinedTextField(raw,{model.inlineRaw(p.id,it)},Modifier.weight(1f).testTag("inventoryActual:${p.id}"),enabled=enabled,singleLine=true,shape=RoundedCornerShape(50),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
                                    MposPosControl("Фиксировать",{model.fixInline(p.id)},Modifier.testTag("inventoryFix:${p.id}"),enabled && !ui.pending && raw.isNotBlank(),PosControl.Cash,44.dp,radius=22.dp,fontSize=13f)
                                }
                                val baseline=input?.product ?: count?.product;val token=input?.generation ?: count?.generation
                                if(baseline!=null && (live==null || !InventoryRules.sameStock(baseline,live) || token!=s.generation)) Caption("Остаток изменился — пересчитайте и введите факт заново.")
                            }
                        }
                        if(maxWidth<650.dp) Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {name();controls()} else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {Box(Modifier.weight(1f)) {name()};controls()}
                    }
                }
                   }
                  }
                }
            }
    }
}

@Composable private fun InventorySummaryContent(draft: InventoryDraft) {
    val summary=InventoryClosingRules.summary(draft)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("Недостачи" to summary.shortages.toString(),"Излишки" to summary.surpluses.toString(),"Примерный убыток" to money(summary.lossMinor)).forEach {(label,value)->
            Surface(Modifier.weight(1f),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.background) {Column(Modifier.padding(12.dp)) {Caption(label);Text(value,if(label=="Примерный убыток") Modifier.testTag("inventoryLoss") else Modifier,fontSize=20.sp,fontWeight=FontWeight.ExtraBold)}}
        }
    }
    val changed=draft.counts.filter {it.fixed!=null && it.effectiveDelta.toBigDecimal().signum()!=0}
    LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(changed.isEmpty()) item {Caption("Расхождений нет")}
        items(changed,key={it.product.id}) {c->val f=c.fixed!!
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {Text(f.product.name,fontWeight=FontWeight.Bold);Caption("Было: ${f.product.stock} ${unitLabel(f.product.unit)} · Стало: ${c.effectiveActual} ${unitLabel(f.product.unit)}")}
                Column(horizontalAlignment=Alignment.End) {
                    Text("${if(c.effectiveDelta.toBigDecimal().signum()>0) "+" else ""}${c.effectiveDelta} ${unitLabel(f.product.unit)}",color=if(c.effectiveDelta.toBigDecimal().signum()<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    if(c.effectiveDelta.toBigDecimal().signum()<0) Caption("≈ −${money(InventoryClosingRules.lineLoss(c))}")
                }
            }
        }
    }
}
