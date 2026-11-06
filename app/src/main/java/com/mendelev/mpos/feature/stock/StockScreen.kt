package com.mendelev.mpos.feature.stock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.catalog.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun StockScreen(model: StockViewModel,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    var query by rememberSaveable {mutableStateOf("")};var category by rememberSaveable {mutableStateOf("")};var low by rememberSaveable {mutableStateOf(false)}
    var from by rememberSaveable {mutableStateOf("")};var to by rememberSaveable {mutableStateOf("")}
    val s=ui.snapshot;val selected=s?.rows?.find {it.id==s.selectedId}
    val back={if(ui.documentId!=null) model.closeDocument() else if(selected!=null) model.load(null) else onBack()}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("Склад",navigationEnabled=!ui.pending && ui.documentId==null) {}
        Column(Modifier.padding(20.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton(if(selected!=null) "К остаткам" else "Настройки",back,enabled=!ui.pending,style=ActionStyle.Secondary,modifier=Modifier.testTag("stockBack"))
                Text(selected?.name ?: "Остатки и движения",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                MposButton("Перечитать",model::refresh,enabled=!ui.pending,style=ActionStyle.Secondary)
            }
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.error!=null) {MposNotice(ui.error!!,true);MposButton("Вернуться к списку",{model.load(null)},enabled=!ui.pending,style=ActionStyle.Secondary)}
            if(s!=null && ui.error==null) {
                Text("Снимок: ${stockTime(s.at,s.zone)}")
                if(selected==null) {
                    MposField(query,{query=it},"Поиск товара",Modifier.testTag("stockSearch"),!ui.pending)
                    MposSelect("Категория",category,listOf("" to "Все категории")+s.rows.map {it.category}.distinct().sorted().map {it to it},{category=it},!ui.pending)
                    Row {Checkbox(low,{low=it},enabled=!ui.pending,modifier=Modifier.testTag("stockLow"));Text("Только низкие остатки")}
                    val rows=s.rows.filter {CatalogRules.key(it.name).contains(CatalogRules.key(query)) && (category.isEmpty() || it.category==category) && (!low || it.low)}
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(rows.isEmpty()) item {Text("Нет подходящих товаров",Modifier.testTag("stockEmpty"))}
                        items(rows,key={it.id}) {p->
                            MposButton("${p.name} · ${p.quantity?.let {it+" "+unitLabel(p.unit)} ?: "Без учёта"}${if(p.low) " · Низкий остаток" else ""}",{model.load(p.id)},enabled=!ui.pending,style=ActionStyle.Secondary,modifier=Modifier.fillMaxWidth().testTag("stockProduct:${p.id}"))
                        }
                    }
                } else {
                    Text("Сейчас: ${selected.quantity?.let {it+" "+unitLabel(selected.unit)} ?: "Без учёта"}",Modifier.testTag("stockQuantity"))
                    selected.minimum?.let {Text("Минимум: $it ${unitLabel(selected.unit)}")}
                    Text("История движений",style=MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        MposButton("За всё время",{model.period(null)},enabled=!ui.pending,style=ActionStyle.Secondary,modifier=Modifier.testTag("stockPeriodAll"))
                        listOf("today" to "Сегодня","7" to "7 дней","month" to "Месяц","quarter" to "Квартал").forEach {(key,label)->MposButton(label,{model.preset(key)},enabled=!ui.pending,style=ActionStyle.Secondary,modifier=Modifier.testTag("stockPreset:$key"))}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        MposField(from,{from=it},"С (ГГГГ-ММ-ДД)",Modifier.weight(1f).testTag("stockFrom"),!ui.pending)
                        MposField(to,{to=it},"По (ГГГГ-ММ-ДД)",Modifier.weight(1f).testTag("stockTo"),!ui.pending)
                        MposButton("Применить",{model.period(from,to)},enabled=!ui.pending,modifier=Modifier.testTag("stockPeriodApply"))
                    }
                    Text("Период: ${ui.period?.let {"${it.from} — ${it.to}"} ?: "за всё время"} · ${s.zone}",Modifier.testTag("stockPeriod"))
                    if(ui.filterError!=null) MposNotice(ui.filterError!!,true)
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(ui.movements.isEmpty()) item {Text("Движений за выбранный период нет",Modifier.testTag("stockMovementsEmpty"))}
                        items(ui.movements,key={it.id}) {m->
                            Column {
                                Text("${stockTime(m.createdAt,s.zone)} · ${stockSource(m.source)}")
                                Text("Изменение: ${m.delta}",Modifier.testTag("stockMovement:${m.id}"))
                                if(m.source in setOf("sale","refund","receiving","inventory")) MposButton("Открыть документ",{model.openDocument(m.id)},enabled=!ui.pending,style=ActionStyle.Secondary,modifier=Modifier.testTag("stockDocument:${m.id}"))
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    val documentId=ui.documentId;val document=ui.document
    if(documentId!=null) MposDialog(document?.title ?: "Связанный документ",model::closeDocument,!ui.pending) {
        if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(ui.documentError!=null) {MposNotice(ui.documentError!!,true);MposButton("Повторить чтение",{model.openDocument(documentId)},enabled=!ui.pending,modifier=Modifier.testTag("stockDocumentRetry"))}
        if(document!=null) {
            Text(stockTime(document.at,ui.snapshot?.zone ?: ZoneId.systemDefault().id));Text("Сохранённый документ · только просмотр")
            LazyColumn(Modifier.heightIn(max=400.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {items(document.lines.size) {i->Text(document.lines[i])}}
        }
    }
}
private fun stockTime(at: Long,zone: String)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.of(zone)).format(Instant.ofEpochMilli(at))
private fun stockSource(source: String)=when(source) {"opening"->"Начальный остаток";"manual","product_card"->"Правка остатка";"sale"->"Продажа";"refund"->"Возврат";"receiving"->"Приёмка";"inventory"->"Инвентаризация";else->"Корректировка"}
