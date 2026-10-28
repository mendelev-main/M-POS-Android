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
    val s=ui.snapshot;val selected=s?.rows?.find {it.id==s.selectedId}
    val back={if(selected!=null) model.load(null) else onBack()}
    BackHandler {back()}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("Склад",navigationEnabled=!ui.pending) {}
        Column(Modifier.padding(20.dp).weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MposButton(if(selected!=null) "К остаткам" else "Настройки",back,enabled=!ui.pending,style=ActionStyle.Secondary,modifier=Modifier.testTag("stockBack"))
                Text(selected?.name ?: "Остатки и движения",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                MposButton("Перечитать",model::refresh,enabled=!ui.pending,style=ActionStyle.Secondary)
            }
            if(ui.pending) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(ui.error!=null) {MposNotice(ui.error!!,true);MposButton("Вернуться к списку",{model.load(null)},enabled=!ui.pending,style=ActionStyle.Secondary)}
            if(s!=null && ui.error==null) {
                Text("Снимок: ${stockTime(s.at)}")
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
                    Text("Сейчас: ${selected.quantity?.let {it+" "+unitLabel(selected.unit)} ?: "Без учёта"} · версия ${selected.revision}",Modifier.testTag("stockQuantity"))
                    selected.minimum?.let {Text("Минимум: $it ${unitLabel(selected.unit)}")}
                    Text("История движений",style=MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(s.movements.isEmpty()) item {Text("Движений пока нет")}
                        items(s.movements,key={it.id}) {m->
                            Column {
                                Text("${stockTime(m.createdAt)} · ${stockSource(m.source)}")
                                Text("Изменение: ${m.delta}",Modifier.testTag("stockMovement:${m.id}"))
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun stockTime(at: Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
private fun stockSource(source: String)=when(source) {"opening"->"Начальный остаток";"manual","product_card"->"Правка остатка";"sale"->"Продажа";"refund"->"Возврат";"receiving"->"Приёмка";"inventory"->"Инвентаризация";else->"Корректировка"}
