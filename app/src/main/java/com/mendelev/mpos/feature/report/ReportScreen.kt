package com.mendelev.mpos.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mendelev.mpos.core.access.Administration
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.report.ReportAmounts
import com.mendelev.mpos.domain.report.ReportGroup
import com.mendelev.mpos.domain.report.ShiftReportRow
import com.mendelev.mpos.domain.report.WarehouseRow
import com.mendelev.mpos.domain.catalog.unitLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

fun reportMoney(minor: BigInteger)=BigDecimal(minor,2).toPlainString().replace('.',',')+" BYN"

@Composable fun ReportScreen(model: ReportViewModel,onSettings: ()->Unit,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val admin by Administration.session.state.collectAsStateWithLifecycle()
    var dimension by rememberSaveable(admin.generation) {mutableStateOf("employees")}
    var periodDialog by rememberSaveable(admin.generation) {mutableStateOf(false)}
    var from by rememberSaveable(admin.generation) {mutableStateOf("")}
    var through by rememberSaveable(admin.generation) {mutableStateOf("")}
    LaunchedEffect(model,admin.generation) {model.open()}
    val report=ui.report?.takeIf {admin.active && ui.epoch==admin.generation}
    val editPeriod={from=ui.from;through=ui.through;model.clearFilterError();periodDialog=true}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        MposAppBar("Аналитика") {MposButton("Назад",onBack,style=ActionStyle.Secondary)}
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val effectiveWidth=maxWidth.value/LocalDensity.current.fontScale
            val columns=if(effectiveWidth>=1000) 4 else if(effectiveWidth>=600) 2 else 1
            LazyColumn(Modifier.fillMaxSize().testTag("reportScroll"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {
                    MposAnalyticsCard(Modifier.fillMaxWidth()) {
                        Text("Период аналитики",style=MaterialTheme.typography.titleMedium)
                        Text("Выберите готовый период или задайте даты вручную.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            MposButton("От: ${ui.from}",editPeriod,Modifier.testTag("reportFrom"),admin.active,ActionStyle.Outline)
                            MposButton("До: ${ui.through}",editPeriod,Modifier.testTag("reportThrough"),admin.active,ActionStyle.Outline)
                            listOf(1L to "Сегодня",7L to "7 дней",30L to "30 дней").forEach {(days,label)->
                                MposButton(label,{model.preset(days)},Modifier.testTag("reportPreset:$days"),admin.active,ActionStyle.Secondary)
                            }
                            MposButton("Обновить",{model.refresh()},Modifier.testTag("reportRefresh"),admin.active && !ui.pending,ActionStyle.Outline)
                        }
                        Text("Часовой пояс: ${ui.zone}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("reportZone"))
                    }
                }
                if(!admin.active) item {
                    MposAnalyticsCard(Modifier.fillMaxWidth()) {
                        Text("Войдите в администрирование для просмотра денежных показателей",Modifier.testTag("reportLocked"))
                        MposButton("Открыть настройки",onSettings,Modifier.testTag("reportSettings"))
                    }
                } else if(ui.pending) item {Text("Загружаем отчёт…",Modifier.testTag("reportLoading"))}
                else if(ui.error!=null) item {
                    MposNotice(ui.error!!,true)
                    MposButton("Повторить чтение",{model.refresh()},Modifier.testTag("reportRetry"))
                } else if(report!=null) {
                    if(report.archivedWebReceipts>0) item {MposNotice("Архив Web содержит ${report.archivedWebReceipts} чеков. Эта аналитика пока учитывает только native-операции; исторические Web-чеки в суммы не включены",false)}
                    val metrics=listOf(Triple("Продажи",reportMoney(report.sales.total),"Sales"),Triple("Возвраты",reportMoney(report.refunds.total),"Refunds"),
                        Triple("Итог за период",reportMoney(report.net.total),"Net"),Triple("Новых чеков",report.saleCount.toString(),"Count"),
                        Triple("Средний чек продаж",reportMoney(report.averageSale),"Average"),Triple("Возвратов",report.refundCount.toString(),"RefundCount"))
                    metrics.chunked(columns).forEach {row->item {
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                            row.forEach {(label,value,tag)->MposAnalyticsCard(Modifier.weight(1f),metric=true) {
                                Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value,Modifier.testTag("report$tag"),style=MaterialTheme.typography.headlineSmall.copy(fontSize=24.sp,fontWeight=FontWeight.ExtraBold))
                            }}
                            repeat(columns-row.size) {Spacer(Modifier.weight(1f))}
                        }
                    }}
                    item {
                        if(effectiveWidth>=900) Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            PaymentPanel("Способы оплаты · продажи",report.sales,"Sales",Modifier.weight(1f))
                            PaymentPanel("Способы оплаты · возвраты",report.refunds,"Refunds",Modifier.weight(1f))
                        } else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            PaymentPanel("Способы оплаты · продажи",report.sales,"Sales",Modifier.fillMaxWidth())
                            PaymentPanel("Способы оплаты · возвраты",report.refunds,"Refunds",Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        Text("Итог = продажи − возвраты. Возврат учитывается в день возврата. Средний чек рассчитан по новым продажам.",style=MaterialTheme.typography.bodySmall)
                        if(report.saleCount==0L && report.refundCount==0L) Text("За выбранный период продаж и возвратов нет",Modifier.testTag("reportEmpty"))
                    }
                }
                if(report!=null && !ui.pending && ui.error==null) {
                    val details=report.details
                    if(details==null) item {Text("Историческая детализация недоступна")}
                    else {
                        item {MposAnalyticsCard(Modifier.fillMaxWidth()) {
                            Text("Прибыль за период",style=MaterialTheme.typography.titleMedium)
                            Text(reportMoney(details.profit),Modifier.testTag("reportProfit"),style=MaterialTheme.typography.headlineSmall)
                            Text("Продажи − возвраты − историческая себестоимость − расход курьеру с учётом отмены при возврате.",style=MaterialTheme.typography.bodySmall)
                            Text("Себестоимость продаж: ${reportMoney(details.saleCost)}")
                            Text("Себестоимость возвратов: ${reportMoney(details.refundCost)}")
                            Text("Курьеру: ${reportMoney(details.courierOut)}; отменено: ${reportMoney(details.courierCredit)}")
                        }}
                        item {MposAnalyticsCard(Modifier.fillMaxWidth()) {
                            Text("Историческая детализация",style=MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf("employees" to "Сотрудники","categories" to "Категории","products" to "Товары","shifts" to "Смены","warehouse" to "Склад").forEach {(id,label)->
                                    MposButton(label,{dimension=id},Modifier.testTag("reportGroup:$id"),style=if(dimension==id) ActionStyle.Primary else ActionStyle.Outline)
                                }
                            }
                            Text(if(dimension=="warehouse") "Данные по выбранному периоду; товары по текущим названиям. Количества разных единиц не суммируются." else if(dimension=="shifts") "Смены по дате открытия, сначала новые. Кассовый возврат учитывается в смене выдачи денег." else "По сумме продаж, по убыванию. Сотрудники — с доставкой; категории и товары — без доставки. Возврат относится к автору исходной продажи.",style=MaterialTheme.typography.bodySmall)
                            Text(if(dimension=="warehouse") "Исторические единицы подтверждаются движениями и сохранёнными документами." else "Названия и категории на момент продажи; разные единицы не суммируются.",style=MaterialTheme.typography.bodySmall)
                        }}
                        if(dimension=="warehouse") {
                            item {Text("Текущие названия товаров. Остатки на начало и конец выбранных дат восстановлены по журналу; прочерк означает недоступные данные.")}
                            val warehouse=report.warehouse
                            if(warehouse==null) item {Text("Складской отчёт недоступен")}
                            else {
                                item {MposAnalyticsCard(Modifier.fillMaxWidth()) {
                                    Text("Оценка по текущей себестоимости",style=MaterialTheme.typography.titleMedium)
                                    Text("На конец периода: ${warehouse.endValue?.let(::reportMoney) ?: "—"}",Modifier.testTag("reportWarehouseTotalValue"))
                                    Text("Сейчас: ${warehouse.currentValue?.let(::reportMoney) ?: "—"}")
                                    Text("Оценено на конец: ${warehouse.valuedEndCount} из ${warehouse.tracked.size} товаров с учётом. Без учёта исключено: ${warehouse.rows.size-warehouse.tracked.size}.")
                                    Text("Это оценка по сегодняшней стоимости, не историческая себестоимость. HALF_UP до копейки по товару, затем сумма. При недоступной строке полный итог — прочерк.",style=MaterialTheme.typography.bodySmall)
                                    Text("Фактическая сумма накладных за период: ${reportMoney(warehouse.invoiceValue)}",Modifier.testTag("reportWarehouseInvoices"))
                                }}
                                if(warehouse.rows.isEmpty()) item {Text("Складских товаров нет")}
                                items(warehouse.rows,key={"warehouse:${it.product.id}"}) {WarehouseReportPanel(it)}
                            }
                        } else if(dimension=="shifts") {
                            item {Text("Смены, пересекающие выбранные даты. Каждая строка — вся смена, а не только выбранные дни. Открытая смена: предварительный итог.",Modifier.testTag("reportShiftScope"))}
                            val shifts=report.shifts
                            if(shifts==null) item {Text("Сводка смен недоступна")}
                            else {
                                if(shifts.isEmpty()) item {Text("За период смен нет")}
                                items(shifts,key={"shift:${it.shift.id}"}) {ShiftReportPanel(it)}
                            }
                        } else {
                            val groups=when(dimension) {"categories"->details.categories;"products"->details.products;else->details.employees}
                            if(groups.isEmpty()) item {Text("За период нет данных для детализации")}
                            items(groups,key={"$dimension:${it.id}"}) {group->ReportGroupPanel(group,dimension)}
                        }
                        report.stock?.let {stock->
                            item {MposAnalyticsCard(Modifier.fillMaxWidth()) {
                                Text("Сверка склада · продажи и возвраты",style=MaterialTheme.typography.titleMedium)
                                Text("Совпало движений: ${stock.checked} · Расхождений: ${stock.issues.size}",Modifier.testTag("reportStockStatus"))
                                Text("За выбранные даты. Сравниваются сохранённые документы и движения; остатки не исправляются. Приёмка и инвентаризация сюда не входят.",style=MaterialTheme.typography.bodySmall)
                            }}
                            items(stock.issues) {issue->MposAnalyticsCard(Modifier.fillMaxWidth()) {
                                MposNotice(issue.problem,true)
                                Text("${if(issue.source=="sale") "Продажа" else "Возврат"} · документ ${issue.documentId ?: "без ссылки"}")
                                Text("Товар: ${issue.productId}")
                            }}
                        }
                    }
                }
            }
        }
    }
    if(periodDialog && admin.active) MposDialog("Выбор периода",{periodDialog=false;model.clearFilterError()}) {
        Text("Введите даты в формате ГГГГ-ММ-ДД")
        MposField(from,{from=it},"Дата начала",Modifier.testTag("reportPeriodFrom"))
        MposField(through,{through=it},"Дата окончания",Modifier.testTag("reportPeriodThrough"))
        ui.filterError?.let {MposNotice(it,true)}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            MposButton("Отмена",{periodDialog=false;model.clearFilterError()},style=ActionStyle.Secondary)
            MposButton("Применить",{if(model.dates(from,through)) periodDialog=false},Modifier.testTag("reportApply"))
        }
    }
}
@Composable private fun PaymentPanel(title: String,amounts: ReportAmounts,tag: String,modifier: Modifier) {
    MposAnalyticsCard(modifier) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        listOf(Triple("Наличные",amounts.cash,"Cash"),Triple("Карта",amounts.card,"Card")).forEach {(label,value,key)->
            Text(label,style=MaterialTheme.typography.bodySmall)
            Text(reportMoney(value),Modifier.testTag("report$tag$key"),style=MaterialTheme.typography.titleMedium)
            val ratio=if(amounts.total.signum()==0) 0f else value.toBigDecimal().divide(amounts.total.toBigDecimal(),6,RoundingMode.HALF_UP).toFloat().coerceIn(0f,1f)
            LinearProgressIndicator(progress={ratio},modifier=Modifier.fillMaxWidth().height(8.dp),
                trackColor=MaterialTheme.colorScheme.outline,gapSize=0.dp,drawStopIndicator={})
        }
        HorizontalDivider()
        Text("Доставка включена в сумму",style=MaterialTheme.typography.bodySmall)
        Text(reportMoney(amounts.delivery),Modifier.testTag("report${tag}Delivery"))
        Text("Оплаты без вычета расходов кассы",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ReportGroupPanel(group: ReportGroup,dimension: String) {
    fun quantities(values: Map<String,BigDecimal>)=values.entries.joinToString("; ") {(unit,qty)->
        val label=when(unit) {"batch"->"порц.";"piece"->"шт.";"kg"->"кг";"g"->"г";"l"->"л";"ml"->"мл";else->unit}
        "${qty.stripTrailingZeros().toPlainString().replace('.',',')} $label"
    }
    MposAnalyticsCard(Modifier.fillMaxWidth().testTag("reportRow:$dimension:${group.id}")) {
        Text(group.names.joinToString(" / "),style=MaterialTheme.typography.titleMedium)
        Text("Продажи: ${reportMoney(group.sales)} · Возвраты: ${reportMoney(group.refunds)}")
        Text("Итог: ${reportMoney(group.net)}",style=MaterialTheme.typography.titleMedium)
        if(group.sold.isNotEmpty()) Text("Продано: ${quantities(group.sold)}")
        if(group.returned.isNotEmpty()) Text("Возвращено: ${quantities(group.returned)}")
    }
}

@Composable private fun ShiftReportPanel(row: ShiftReportRow) {
    val s=row.shift
    fun date(at: Long)=Instant.ofEpochMilli(at).atZone(ZoneId.of(s.zoneId)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    MposAnalyticsCard(Modifier.fillMaxWidth().testTag("reportShift:${s.id}")) {
        Text(s.actorName,style=MaterialTheme.typography.titleMedium)
        Text("${date(s.openedAt)} — ${s.closedAt?.let(::date) ?: "Открыта"} · ${s.zoneId}")
        Text("Продажи: ${reportMoney(row.sales)} · Возвраты: ${reportMoney(row.refunds)}")
        Text("Наличные: продажи ${reportMoney(row.saleCash)} · возвраты ${reportMoney(row.refundCash)}")
        Text("Карта: продажи ${reportMoney(row.saleCard)} · возвраты ${reportMoney(row.refundCard)}")
        Text("Начало: ${reportMoney(s.openingCash.toBigInteger())}")
        Text("Внесения: ${reportMoney(row.deposits)} · Изъятия: ${reportMoney(row.withdrawals)}")
        Text("Курьеру: ${reportMoney(row.courierOut)} · Отмена: ${reportMoney(row.courierCredit)}")
        Text("Расчётная касса: ${reportMoney(row.calculatedCash)}",Modifier.testTag("reportShiftCash:${s.id}"),style=MaterialTheme.typography.titleMedium)
        if(!s.isOpen) {
            Text("Сохранено при закрытии: ${reportMoney(requireNotNull(s.expectedCash).toBigInteger())}")
            Text("Пересчитано: ${reportMoney(requireNotNull(s.countedCash).toBigInteger())} · Разница: ${reportMoney(requireNotNull(s.difference).toBigInteger())}")
            if(row.savedCashMatches==false) MposNotice("Расчёт не совпадает с сохранённым итогом смены",true)
        }
    }
}

@Composable private fun WarehouseReportPanel(row: WarehouseRow) {
    val p=row.product
    fun quantity(value: BigDecimal?)=value?.let {it.stripTrailingZeros().toPlainString().replace('.',',')+" "+unitLabel(p.unit)} ?: "—"
    MposAnalyticsCard(Modifier.fillMaxWidth().testTag("reportWarehouse:${p.id}")) {
        Text(p.name,style=MaterialTheme.typography.titleMedium)
        Text("На начало: ${quantity(row.start)}")
        Text("Складской приход: ${quantity(row.incoming)} · Списано: ${quantity(row.outgoing)}")
        Text("Возвращено: ${quantity(row.returned)} · Прочие изменения: ${quantity(row.adjustments)}")
        Text("На конец: ${quantity(row.end)}",Modifier.testTag("reportWarehouseEnd:${p.id}"),style=MaterialTheme.typography.titleMedium)
        Text("Сейчас: ${quantity(p.current)}")
        Text("Текущая себестоимость: ${p.cost.toPlainString().replace('.',',')} BYN/${unitLabel(p.unit)}")
        Text("Оценка на начало: ${row.startValue?.let(::reportMoney) ?: "—"}")
        Text("Оценка на конец: ${row.endValue?.let(::reportMoney) ?: "—"}",Modifier.testTag("reportWarehouseEndValue:${p.id}"))
        Text("Оценка списаний: ${row.outgoingValue?.let(::reportMoney) ?: "—"} · Возвратов: ${row.returnedValue?.let(::reportMoney) ?: "—"}")
        Text("Получено по накладным: ${quantity(row.invoiceQuantity)} · ${reportMoney(row.invoiceValue)}")
        Text("Складской приход и количество по накладным могут различаться при отключённом учёте.",style=MaterialTheme.typography.bodySmall)
        row.warnings.forEach {MposNotice(it,true)}
    }
}
