package com.mendelev.mpos.feature.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.core.ui.*
import com.mendelev.mpos.domain.report.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Owner-approved Panorama tokens; scoped to analytics, including both application themes. */
@Composable internal fun PanoramaTheme(content: @Composable ()->Unit) {
    val dark=isMposDarkTheme();val base=MaterialTheme.colorScheme
    MaterialTheme(colorScheme=base.copy(
        background=if(dark) Color(0xFF171A21) else Color(0xFFF3F4F6),
        surface=if(dark) Color(0xFF222731) else Color.White,
        onSurface=if(dark) Color(0xFFEEF1F5) else Color(0xFF202633),
        onBackground=if(dark) Color(0xFFEEF1F5) else Color(0xFF202633),
        onSurfaceVariant=if(dark) Color(0xFFA5ADBB) else Color(0xFF616B7A),
        outline=if(dark) Color(0xFF363E4B) else Color(0xFFDCE0E6),
        primary=if(dark) Color(0xFF32C99F) else Color(0xFF087C60),
        primaryContainer=if(dark) Color(0xFF1E3A32) else Color(0xFFE2F4EC)),content=content)
}
@Composable internal fun PanoramaCard(modifier: Modifier=Modifier,title: String?=null,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier,shape=RoundedCornerShape(19.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(title!=null) Text(title,fontSize=16.sp,fontWeight=FontWeight.Bold)
            content()
        }
    }
}
@Composable internal fun AnalyticsSummary(report: SalesReport,wide: Boolean) {
    @Composable fun metric(label: String,value: String,left: String,right: String,modifier: Modifier,accent: Boolean=false) {
        PanoramaCard(modifier) {
            Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=if(accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(left,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(right,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    @Composable fun revenue(m: Modifier)=metric("Выручка",reportMoney(report.net.total),"Продажи ${reportNumber(report.sales.total)}","Возвраты −${reportNumber(report.refunds.total)}",m,true)
    @Composable fun profit(m: Modifier)=metric("Валовая прибыль",ReportAnalytics.grossProfit(report)?.let(::reportMoney) ?: "—","Себестоимость ${report.details?.netCost?.let(::reportNumber) ?: "—"}","Рентабельность ${ReportAnalytics.margin(report)?.let {"$it %"} ?: "—"}",m)
    @Composable fun average(m: Modifier)=metric("Средний чек",reportMoney(report.averageSale),"Чеков: ${report.saleCount}","Возвратов: ${report.refundCount}",m)
    if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        revenue(Modifier.weight(1f));profit(Modifier.weight(1f));average(Modifier.weight(1f))
    } else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        revenue(Modifier.fillMaxWidth());profit(Modifier.fillMaxWidth());average(Modifier.fillMaxWidth())
    }
}
internal fun reportNumber(value: BigInteger)=java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("ru-RU")).apply {
    minimumFractionDigits=2;maximumFractionDigits=2
}.format(BigDecimal(value,2))
@Composable private fun seriesColors()=listOf(MaterialTheme.colorScheme.primary,
    if(isMposDarkTheme()) Color(0xFF79A9EC) else Color(0xFF3474BB),
    if(isMposDarkTheme()) Color(0xFFE5BD66) else Color(0xFF986B19))
@Composable private fun Dot(color: Color) {Box(Modifier.size(8.dp).background(color,RoundedCornerShape(50)))}
@Composable private fun ShareRows(rows: List<ReportShare>,colors: List<Color>,checks: Boolean=false) {
    rows.forEachIndexed {index,row->
        if(index>0) HorizontalDivider(color=MaterialTheme.colorScheme.outline,thickness=1.dp)
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Dot(colors[index])
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(row.label,fontSize=13.sp)
                if(checks) Text("${row.checks} чеков",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text(reportNumber(row.amount),fontSize=13.sp,fontWeight=FontWeight.Bold)
                Text(row.percent?.let {"$it %"} ?: "—",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable private fun Payments(report: SalesReport) {
    // Card first, matching the approved mockup. Return allocation still comes from original methods.
    val rows=ReportAnalytics.payments(report).reversed();val colors=seriesColors()
    PanoramaCard(Modifier.fillMaxWidth(),"Оплата") {
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(126.dp).semantics {contentDescription=rows.joinToString {"${it.label}: ${reportMoney(it.amount)}, ${it.percent ?: "—"}%"}},contentAlignment=Alignment.Center) {
                val track=MaterialTheme.colorScheme.outline
                Canvas(Modifier.fillMaxSize()) {
                    val stroke=15.dp.toPx();val inset=stroke/2
                    val arcSize=androidx.compose.ui.geometry.Size(size.width-stroke,size.height-stroke)
                    val origin=androidx.compose.ui.geometry.Offset(inset,inset)
                    drawArc(track,-90f,360f,false,origin,arcSize,style=Stroke(stroke))
                    if(rows.all {it.percent!=null}) {
                        var start=-90f
                        rows.forEachIndexed {i,row->
                            val sweep=row.amount.toBigDecimal().divide(report.net.total.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat()*360f
                            drawArc(colors[i],start,sweep,false,origin,arcSize,style=Stroke(stroke));start+=sweep
                        }
                    }
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(reportNumber(report.net.total),fontSize=13.sp,fontWeight=FontWeight.Bold)
                    Text("BYN",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).widthIn(min=140.dp)) {ShareRows(rows,colors)}
        }
    }
}
@Composable private fun Fulfillment(report: SalesReport) {
    val rows=ReportAnalytics.fulfillment(report);val colors=seriesColors()
    PanoramaCard(Modifier.fillMaxWidth(),"Получение заказа") {
        if(rows.all {it.percent!=null}) Row(Modifier.fillMaxWidth().height(12.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            rows.forEachIndexed {i,row->if(row.amount.signum()>0) {
                val weight=row.amount.toBigDecimal().divide(report.net.total.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat().coerceAtLeast(0.000001f)
                Box(Modifier.weight(weight).fillMaxHeight().background(colors[i],RoundedCornerShape(7.dp)))
            }}
        }
        ShareRows(rows,colors,true)
    }
}
@Composable internal fun AnalyticsPanorama(report: SalesReport,wide: Boolean,profitChart: Boolean,onProfitChart: (Boolean)->Unit) {
    @Composable fun left(m: Modifier) {Column(m,verticalArrangement=Arrangement.spacedBy(16.dp)) {AnalyticsTrendChart(report,profitChart,onProfitChart);Categories(report)}}
    @Composable fun right(m: Modifier) {Column(m,verticalArrangement=Arrangement.spacedBy(16.dp)) {Payments(report);Fulfillment(report);Employees(report)}}
    if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        left(Modifier.weight(1.7f));right(Modifier.weight(1f))
    } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {left(Modifier.fillMaxWidth());right(Modifier.fillMaxWidth())}
}
internal fun reportQuantities(values: Map<String,BigDecimal>)=values.entries.joinToString("; ") {(unit,q)->
    (if(unit=="kg" || unit=="l") q.setScale(2,RoundingMode.HALF_UP).toPlainString() else q.stripTrailingZeros().toPlainString()).replace('.',',')+" "+when(unit) {"batch"->"порц.";"piece"->"шт.";"kg"->"кг";"g"->"г";"l"->"л";"ml"->"мл";else->unit}
}.ifEmpty {"0"}
internal fun netQuantities(row: ReportGroup)=(row.sold.keys+row.returned.keys).associateWith {(row.sold[it] ?: BigDecimal.ZERO)-(row.returned[it] ?: BigDecimal.ZERO)}
@Composable private fun Table(headers: List<String>,rows: List<List<String>>,minimumWidth: androidx.compose.ui.unit.Dp) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(rememberScrollState()).width(maxOf(maxWidth,minimumWidth))) {
            @Composable fun line(values: List<String>,heading: Boolean) {
                Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=if(heading) 9.dp else 12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    values.forEachIndexed {index,value->Text(value,Modifier.weight(if(index==0) 1.7f else 1f),
                        textAlign=if(index==0) TextAlign.Start else TextAlign.End,fontSize=12.sp,
                        fontWeight=if(heading) FontWeight.Medium else FontWeight.Normal,
                        color=if(heading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)}
                }
            }
            line(headers,true)
            if(rows.isEmpty()) Text("Нет данных",Modifier.padding(vertical=12.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach {HorizontalDivider(color=MaterialTheme.colorScheme.outline);line(it,false)}
        }
    }
}
@Composable private fun Categories(report: SalesReport) {
    val rows=report.details?.categories.orEmpty().sortedWith(compareByDescending<ReportGroup> {it.net}.thenBy {it.id})
    val max=rows.maxOfOrNull {it.net.abs()} ?: BigInteger.ZERO
    val primary=MaterialTheme.colorScheme.primary;val neutral=MaterialTheme.colorScheme.background
    PanoramaCard(Modifier.fillMaxWidth(),"Категории") {
        rows.forEach {row->
            val ratio=if(max.signum()==0) 0f else row.net.abs().toBigDecimal().divide(max.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat()
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(row.names.joinToString(" / "),Modifier.weight(1f),fontSize=13.sp)
                val barColor=if(row.net.signum()<0) MaterialTheme.colorScheme.error else primary
                Canvas(Modifier.weight(1.4f).height(8.dp).semantics {contentDescription=reportMoney(row.net)}) {
                    drawRoundRect(neutral,cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                    if(ratio>0f) drawRoundRect(barColor,size=androidx.compose.ui.geometry.Size(size.width*ratio,size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                }
                Text(reportNumber(row.net),Modifier.weight(0.8f),textAlign=TextAlign.End,fontSize=12.sp)
            }
        }
        Table(listOf("Категория","Количество","Продажи","Возвраты","Выручка"),rows.map {
            listOf(it.names.joinToString(" / "),reportQuantities(netQuantities(it)),reportNumber(it.sales),reportNumber(it.refunds),reportNumber(it.net))
        },600.dp)
    }
}
@Composable private fun Employees(report: SalesReport) {
    var expanded by remember(report.period) {mutableStateOf<String?>(null)}
    PanoramaCard(Modifier.fillMaxWidth(),"Сотрудники") {
        val rows=report.details?.employees.orEmpty()
        if(rows.isEmpty()) Text("Нет данных",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEachIndexed {index,row->
            if(index>0) HorizontalDivider(color=MaterialTheme.colorScheme.outline)
            Surface(onClick={expanded=if(expanded==row.id) null else row.id},color=Color.Transparent,
                modifier=Modifier.fillMaxWidth().semantics {contentDescription="${row.names.joinToString()}. Показать продажи и возвраты"}) {
                Column(Modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        val name=row.names.first();val words=name.split(Regex("\\s+")).filter {it.isNotBlank()}
                        Surface(Modifier.size(32.dp),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment=Alignment.Center) {Text(words.take(2).joinToString("") {it.take(1)}.uppercase(),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}
                        }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(row.names.joinToString(" / ") {full->val parts=full.split(Regex("\\s+")).filter {it.isNotBlank()};parts.firstOrNull().orEmpty()+parts.drop(1).take(2).joinToString("",prefix=if(parts.size>1) " " else "") {it.first().uppercaseChar().toString()+"."}},fontSize=13.sp)
                            Text("Чеков: ${row.checks} · средний ${reportNumber(row.average)}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(reportNumber(row.net),fontSize=13.sp,fontWeight=FontWeight.Bold)
                    }
                    if(expanded==row.id) Text("Продажи: ${reportMoney(row.sales)}\nВозвраты: ${reportMoney(row.refunds)}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable private fun CollapsiblePanoramaCard(title: String,content: @Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable {mutableStateOf(true)}
    PanoramaCard(Modifier.fillMaxWidth()) {
        Surface(onClick={expanded=!expanded},color=Color.Transparent,modifier=Modifier.fillMaxWidth().semantics {
            stateDescription=if(expanded) "Развёрнуто" else "Свёрнуто"
        }) {
            Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold)
                MposPosIcon("next",Modifier.size(18.dp).rotate(if(expanded) 90f else 0f),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(expanded) content()
    }
}
@Composable internal fun AnalyticsTables(report: SalesReport) {
    val details=report.details ?: return
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        CollapsiblePanoramaCard("Товары") {
            Table(listOf("Товар","Продано","Возвращено","Итого","Выручка"),ReportAnalytics.products(details.products,"",true).map {
                listOf(it.names.joinToString(" / "),reportQuantities(it.sold),reportQuantities(it.returned),reportQuantities(netQuantities(it)),reportNumber(it.net))
            },760.dp)
        }
        CollapsiblePanoramaCard("Движение товаров") {
            val rows=report.consumption
            fun quantity(value: BigDecimal?,unit: String)=value?.let {reportQuantities(mapOf(unit to it))} ?: "—"
            if(rows==null) Text("Исторические данные расхода недоступны",fontSize=12.sp)
            else Table(listOf("Товар","Поступило","Израсходовано"),ConsumptionStockReports.build(rows,report.warehouse).map {
                listOf(it.names.joinToString(" / "),quantity(it.received,it.unit),quantity(it.consumed,it.unit))
            },420.dp)
        }
    }
}
