package com.mendelev.mpos.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mendelev.mpos.domain.report.*
import java.math.BigInteger
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable internal fun AnalyticsTrendChart(report: SalesReport,profit: Boolean,onProfit: (Boolean)->Unit) {
    var selected by remember(report.period,profit) {mutableStateOf<Int?>(null)}
    val points=report.trend
    val format=remember {DateTimeFormatter.ofPattern("dd.MM")}
    fun label(point: ReportTrendPoint)=if(point.from==point.through) point.from.format(format) else "${point.from.format(format)}–${point.through.format(format)}"
    PanoramaCard(Modifier.fillMaxWidth(),if(profit) "Динамика прибыли" else "Динамика выручки") {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(points?.any {it.from!=it.through}==true) "BYN / период" else "BYN / день",Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(false to "Выручка",true to "Прибыль").forEach {(value,title)->
                Surface(onClick={onProfit(value)},shape=RoundedCornerShape(7.dp),color=if(profit==value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Text(title,Modifier.padding(horizontal=8.dp,vertical=6.dp),fontSize=12.sp,color=if(profit==value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(points.isNullOrEmpty()) Text("Динамика недоступна",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val values=points.map {if(profit) it.profit else it.revenue}
            val min=minOf(BigInteger.ZERO,values.min());val max=maxOf(BigInteger.ZERO,values.max())
            val span=(max-min).takeIf {it.signum()!=0} ?: BigInteger.ONE
            fun ratio(value: BigInteger)=value.toBigDecimal().divide(span.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat()
            val ink=MaterialTheme.colorScheme.primary;val grid=MaterialTheme.colorScheme.outline
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(reportNumber(max),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(min.signum()<0) Text(reportNumber(min),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.fillMaxWidth().height(160.dp)
                .semantics {contentDescription=points.mapIndexed {i,p->"${label(p)}: ${reportMoney(values[i])}"}.joinToString("; ")}
                .pointerInput(points,profit) {
                    detectTapGestures {tap->
                        selected=if(points.size==1) 0 else (((tap.x-8.dp.toPx())/(size.width-16.dp.toPx()).coerceAtLeast(1f))*(points.size-1)).roundToInt().coerceIn(points.indices)
                    }
                }) {
                val pad=8.dp.toPx();val width=(size.width-2*pad).coerceAtLeast(1f);val height=(size.height-2*pad).coerceAtLeast(1f)
                fun y(value: BigInteger)=pad+ratio(max-value)*height
                fun x(index: Int)=if(points.size==1) size.width/2 else pad+index.toFloat()/(points.size-1)*width
                listOf(pad,size.height/2,size.height-pad).forEach {yy->drawLine(grid,Offset(pad,yy),Offset(size.width-pad,yy),1.dp.toPx())}
                val baseline=y(BigInteger.ZERO)
                if(min.signum()<0) drawLine(grid,Offset(pad,baseline),Offset(size.width-pad,baseline),1.dp.toPx())
                val path=Path().apply {values.forEachIndexed {i,v->if(i==0) moveTo(x(i),y(v)) else lineTo(x(i),y(v))}}
                if(points.size>1) {
                    val area=Path().apply {moveTo(x(0),baseline);values.forEachIndexed {i,v->lineTo(x(i),y(v))};lineTo(x(points.lastIndex),baseline);close()}
                    drawPath(area,ink.copy(alpha=0.1f));drawPath(path,ink,style=Stroke(3.dp.toPx()))
                }
                values.forEachIndexed {i,v->drawCircle(ink,if(selected==i) 6.dp.toPx() else 3.dp.toPx(),Offset(x(i),y(v)))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                val indices=if(points.size<=7) points.indices.toList() else listOf(0,points.size/2,points.lastIndex)
                indices.forEach {i->Text(label(points[i]),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                val index=selected
                Text(if(index==null) (if(profit) "Прибыль за период" else "Выручка за период") else label(points[index]),Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val total=if(index==null) values.fold(BigInteger.ZERO) {sum,v->sum+v} else values[index]
                Text(reportMoney(total),fontSize=14.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}
