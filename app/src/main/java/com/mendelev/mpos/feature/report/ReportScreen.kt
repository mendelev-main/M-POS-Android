package com.mendelev.mpos.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

fun reportMoney(minor: BigInteger)=BigDecimal(minor,2).toPlainString().replace('.',',')+" BYN"

@Composable fun ReportScreen(model: ReportViewModel,onSettings: ()->Unit,onBack: ()->Unit) {
    val ui by model.state.collectAsStateWithLifecycle()
    val admin by Administration.session.state.collectAsStateWithLifecycle()
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
                item {MposAnalyticsCard(Modifier.fillMaxWidth()) {
                    Text("Расширенная аналитика",style=MaterialTheme.typography.titleMedium)
                    Text("Прибыль, продажи сотрудников, категории и топ товаров будут подключены на следующем этапе.")
                }}
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
            LinearProgressIndicator(progress={ratio},modifier=Modifier.fillMaxWidth().height(8.dp))
        }
        HorizontalDivider()
        Text("Доставка включена в сумму",style=MaterialTheme.typography.bodySmall)
        Text(reportMoney(amounts.delivery),Modifier.testTag("report${tag}Delivery"))
        Text("Оплаты без вычета расходов кассы",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
