package com.mendelev.mpos.feature.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.mendelev.mpos.R
import com.mendelev.mpos.domain.report.*
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max

/** Native vector counterpart of Panorama: all rows, one page, no screenshot bitmap or database writes. */
internal object AnalyticsPdf {
    private data class Palette(val bg: Int,val panel: Int,val ink: Int,val sub: Int,val line: Int,val green: Int,val blue: Int,val yellow: Int,val soft: Int,val red: Int)
    private fun palette(dark: Boolean)=if(dark) Palette(0xff171a21.toInt(),0xff222731.toInt(),0xffeef1f5.toInt(),0xffa5adbb.toInt(),0xff363e4b.toInt(),0xff32c99f.toInt(),0xff79a9ec.toInt(),0xffe5bd66.toInt(),0xff1e3a32.toInt(),0xffed8b8b.toInt())
        else Palette(0xfff3f4f6.toInt(),Color.WHITE,0xff202633.toInt(),0xff616b7a.toInt(),0xffdce0e6.toInt(),0xff087c60.toInt(),0xff3474bb.toInt(),0xff986b19.toInt(),0xffe2f4ec.toInt(),0xffb54549.toInt())
    data class Options(val dark: Boolean,val width: Int=1280,val profitChart: Boolean=false,val title: String="Учёт и аналитика",val subtitle: String?=null)
    fun newFile(context: Context,report: SalesReport): File {
        val dir=File(context.cacheDir,"analytics-share");check(dir.isDirectory || dir.mkdirs())
        val oldest=System.currentTimeMillis()-7L*24*60*60*1000
        dir.listFiles()?.filter {it.isFile && it.lastModified()<oldest}?.forEach {it.delete()}
        return File(dir,"M-POS_Analytics_${report.period.from}_${report.period.through}_${UUID.randomUUID().toString().take(8)}.pdf")
    }
    fun shareIntent(context: Context,file: File): Intent {
        require(file.isFile && file.extension=="pdf")
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.reports",file)
        return Intent(Intent.ACTION_SEND).apply {
            type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_SUBJECT,"M-POS — Учёт и аналитика")
            clipData=ClipData.newRawUri("M-POS Analytics",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    fun write(context: Context,file: File,report: SalesReport,options: Options,checkCancelled: ()->Unit={}) {
        require(report.details!=null && report.consumption!=null)
        val width=options.width.coerceIn(960,1920)
        val font=ResourcesCompat.getFont(context,R.font.manrope) ?: Typeface.DEFAULT
        val colors=palette(options.dark)
        val measured=draw(null,report,options,width.toFloat(),font,colors,checkCancelled)
        require(measured.isFinite() && measured>0 && measured<Int.MAX_VALUE-1)
        val fit=ReportExportLayout.fit(width,ceil(measured.toDouble()).toInt())
        val temporary=File(file.parentFile,file.name+".partial")
        try {
            val doc=PdfDocument()
            var pending: PdfDocument.Page?=null
            try {
                val page=doc.startPage(PdfDocument.PageInfo.Builder(fit.width,fit.height,1).create())
                pending=page
                page.canvas.scale(fit.scale,fit.scale)
                page.canvas.drawColor(colors.bg)
                draw(page.canvas,report,options,width.toFloat(),font,colors,checkCancelled)
                doc.finishPage(page);pending=null;checkCancelled()
                temporary.outputStream().use {doc.writeTo(it)}
            } finally {pending?.let {runCatching {doc.finishPage(it)}};doc.close()}
            checkCancelled();check(temporary.renameTo(file))
        } catch(e: Throwable) {temporary.delete();file.delete();throw e}
    }
    private class Draw(val canvas: Canvas?,val font: Typeface,val c: Palette,val checkCancelled: ()->Unit) {
        private val fonts=mutableMapOf<Int,Typeface>()
        fun measuring()=Draw(null,font,c,checkCancelled)
        fun text(value: String,x: Float,y: Float,width: Float,size: Float=12f,bold: Boolean=false,color: Int=c.ink,right: Boolean=false): Float {
            val weight=if(bold) 700 else 400
            val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color;typeface=fonts.getOrPut(weight) {Typeface.create(font,weight,false)}}
            val layout=StaticLayout.Builder.obtain(value,0,value.length,p,width.toInt().coerceAtLeast(1)).setIncludePad(false)
                .setAlignment(if(right) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL).build()
            canvas?.let {val save=it.save();it.translate(x,y);layout.draw(it);it.restoreToCount(save)}
            return layout.height.toFloat()
        }
        fun rect(x: Float,y: Float,w: Float,h: Float,color: Int,r: Float=0f,stroke: Boolean=false) {
            canvas?.drawRoundRect(x,y,x+w,y+h,r,r,Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;style=if(stroke) Paint.Style.STROKE else Paint.Style.FILL;strokeWidth=1f})
        }
        fun line(x: Float,y: Float,w: Float) {canvas?.drawLine(x,y,x+w,y,Paint().apply {color=c.line;strokeWidth=1f})}
        fun card(x: Float,y: Float,w: Float,h: Float) {rect(x,y,w,h,c.panel,19f);rect(x,y,w,h,c.line,19f,true)}
        fun dot(x: Float,y: Float,color: Int) {canvas?.drawCircle(x,y,4f,Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color})}
        fun table(x: Float,y: Float,w: Float,headers: List<String>,rows: List<List<String>>): Float {
            val weights=listOf(1.7f)+List(headers.size-1) {1f};val total=weights.sum()
            val widths=weights.map {(w-14f-12f*(headers.size-1))*it/total}
            var top=y
            (listOf(headers)+rows).forEachIndexed {index,row->
                checkCancelled();val heading=index==0
                if(!heading) line(x,top,w)
                val pad=if(heading) 9f else 12f
                var left=x+7;var h=0f
                row.forEachIndexed {i,value->h=max(h,text(value,left,top+pad,widths[i],12f,false,if(heading) c.sub else c.ink,i>0));left+=widths[i]+12}
                top+=h+pad*2
            }
            if(rows.isEmpty()) top+=text("Нет данных",x+7,top+12,w-14,12f,color=c.sub)+24
            return top-y
        }
        fun tableCard(x: Float,y: Float,w: Float,title: String,headers: List<String>,rows: List<List<String>>): Float {
            val measure=measuring()
            val h=20+measure.text(title,0f,0f,w-40,16f,true)+12+measure.table(0f,0f,w-40,headers,rows)+20
            card(x,y,w,h);val header=text(title,x+20,y+20,w-40,16f,true)
            table(x+20,y+20+header+12,w-40,headers,rows)
            return h
        }
    }
    private fun draw(canvas: Canvas?,report: SalesReport,options: Options,width: Float,font: Typeface,c: Palette,checkCancelled: ()->Unit): Float {
        val d=Draw(canvas,font,c,checkCancelled);val m=d.measuring();val details=requireNotNull(report.details)
        val available=width-48;var y=24f
        val dates="${report.period.from.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} — ${report.period.through.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))} · BYN"
        val titleHeight=m.text(options.title,0f,0f,available*.5f,25f,true)
        val dateWidth=available*.45f
        val dateHeight=m.text(dates,0f,0f,dateWidth-26,13f)+18
        d.text(options.title,24f,y,available*.5f,25f,true)
        d.rect(width-24-dateWidth,y,dateWidth,dateHeight,c.panel,11f)
        d.rect(width-24-dateWidth,y,dateWidth,dateHeight,c.line,11f,true)
        d.text(dates,width-24-dateWidth+13,y+9,dateWidth-26,13f,color=c.sub)
        y+=max(titleHeight,dateHeight)
        options.subtitle?.let {y+=8;y+=d.text(it,24f,y,available,12f,color=c.sub)}
        y+=20
        val summaryWidth=(available-28)/3
        val metrics=listOf(
            listOf("Выручка",reportMoney(report.net.total),"Продажи ${reportNumber(report.sales.total)}","Возвраты −${reportNumber(report.refunds.total)}"),
            listOf("Валовая прибыль",ReportAnalytics.grossProfit(report)?.let(::reportMoney) ?: "—","Себестоимость ${reportNumber(details.netCost)}","Рентабельность ${ReportAnalytics.margin(report)?.let {"$it %"} ?: "—"}"),
            listOf("Средний чек",reportMoney(report.averageSale),"Чеков: ${report.saleCount}","Возвратов: ${report.refundCount}"))
        val metricHeight=metrics.maxOf {v->20+m.text(v[0],0f,0f,summaryWidth-40,12f)+12+m.text(v[1],0f,0f,summaryWidth-40,28f,true)+12+max(m.text(v[2],0f,0f,(summaryWidth-52)/2,12f),m.text(v[3],0f,0f,(summaryWidth-52)/2,12f))+20}
        metrics.forEachIndexed {i,m->
            val x=24+i*(summaryWidth+14);d.card(x,y,summaryWidth,metricHeight)
            var top=y+20;top+=d.text(m[0],x+20,top,summaryWidth-40,12f,color=c.sub)+12
            top+=d.text(m[1],x+20,top,summaryWidth-40,28f,true,if(i==0) c.green else c.ink)+12
            d.text(m[2],x+20,top,(summaryWidth-52)/2,12f,color=c.sub);d.text(m[3],x+summaryWidth/2+6,top,(summaryWidth-52)/2,12f,color=c.sub,right=true)
        }
        y+=metricHeight+20
        val leftWidth=(available-16)*1.7f/2.7f;val rightWidth=available-16-leftWidth;val rightX=24+leftWidth+16
        var leftY=y;var rightY=y
        leftY+=trend(d,report,24f,leftY,leftWidth,options.profitChart)+16
        leftY+=categories(d,report,24f,leftY,leftWidth)
        rightY+=payments(d,report,rightX,rightY,rightWidth)+16
        rightY+=fulfillment(d,report,rightX,rightY,rightWidth)+16
        rightY+=employees(d,report,rightX,rightY,rightWidth)
        y=max(leftY,rightY)+20
        val products=ReportAnalytics.products(details.products,"",true).map {listOf(it.names.joinToString(" / "),reportQuantities(it.sold),reportQuantities(it.returned),reportQuantities(netQuantities(it)),reportNumber(it.net))}
        y+=d.tableCard(24f,y,available,"Товары",listOf("Товар","Продано","Возвращено","Итого","Выручка"),products)+20
        val usage=ConsumptionStockReports.build(requireNotNull(report.consumption),report.warehouse).map {
            listOf(it.names.joinToString(" / "),it.received?.let {q->reportQuantities(mapOf(it.unit to q))} ?: "—",reportQuantities(mapOf(it.unit to it.consumed)))
        }
        y+=d.tableCard(24f,y,available,"Движение товаров",listOf("Товар","Поступило","Израсходовано"),usage)
        return y+24
    }
    private fun trend(d: Draw,report: SalesReport,x: Float,y: Float,w: Float,profit: Boolean): Float {
        val points=report.trend.orEmpty();val values=points.map {if(profit) it.profit else it.revenue}
        val h=320f;d.card(x,y,w,h);d.text(if(profit) "Динамика прибыли" else "Динамика выручки",x+20,y+20,w-40,16f,true)
        d.text(if(points.any {it.from!=it.through}) "BYN / период" else "BYN / день",x+20,y+56,w-40,12f,color=d.c.sub)
        if(values.isNotEmpty()) {
            val low=minOf(BigInteger.ZERO,values.min());val high=maxOf(BigInteger.ZERO,values.max());val span=(high-low).takeIf {it.signum()!=0} ?: BigInteger.ONE
            val plotX=x+28;val plotY=y+90;val plotW=w-56;val plotH=150f
            fun yy(v: BigInteger)=plotY+(high-v).toBigDecimal().divide(span.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat()*plotH
            fun xx(i: Int)=if(values.size==1) plotX+plotW/2 else plotX+i.toFloat()/(values.size-1)*plotW
            d.text(reportNumber(high),plotX,plotY-18,plotW/2,11f,color=d.c.sub)
            if(low.signum()<0) {d.text(reportNumber(low),plotX+plotW/2,plotY-18,plotW/2,11f,color=d.c.sub,right=true);d.line(plotX,yy(BigInteger.ZERO),plotW)}
            listOf(plotY,plotY+plotH/2,plotY+plotH).forEach {d.line(plotX,it,plotW)}
            val line=Path();values.forEachIndexed {i,v->if(i==0) line.moveTo(xx(i),yy(v)) else line.lineTo(xx(i),yy(v))}
            d.canvas?.let {c->
                if(values.size>1) {
                    val area=Path().apply {moveTo(xx(0),yy(BigInteger.ZERO));values.forEachIndexed {i,v->lineTo(xx(i),yy(v))};lineTo(xx(values.lastIndex),yy(BigInteger.ZERO));close()}
                    c.drawPath(area,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=d.c.green;alpha=26})
                    c.drawPath(line,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=d.c.green;style=Paint.Style.STROKE;strokeWidth=3f})
                }
                values.forEachIndexed {i,v->c.drawCircle(xx(i),yy(v),3f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=d.c.green})}
            }
            val indices=if(points.size<=7) points.indices.toList() else listOf(0,points.size/2,points.lastIndex)
            indices.forEachIndexed {i,index->
                val p=points[index];val f=DateTimeFormatter.ofPattern("dd.MM");val label=if(p.from==p.through) p.from.format(f) else "${p.from.format(f)}–${p.through.format(f)}"
                val cell=plotW/indices.size;d.text(label,plotX+i*cell,plotY+plotH+12,cell,11f,color=d.c.sub)
            }
        } else d.text("Динамика недоступна",x+20,y+110,w-40,12f,color=d.c.sub)
        d.text(if(profit) "Прибыль за период" else "Выручка за период",x+20,y+h-42,(w-40)/2,12f,color=d.c.sub)
        d.text(reportMoney(if(profit) ReportAnalytics.grossProfit(report) ?: BigInteger.ZERO else report.net.total),x+w/2,y+h-42,w/2-20,14f,true,right=true)
        return h
    }
    private fun categories(d: Draw,report: SalesReport,x: Float,y: Float,w: Float): Float {
        val rows=report.details!!.categories.sortedWith(compareByDescending<ReportGroup> {it.net}.thenBy {it.id})
        val data=rows.map {listOf(it.names.joinToString(" / "),reportQuantities(netQuantities(it)),reportNumber(it.sales),reportNumber(it.refunds),reportNumber(it.net))}
        val measure=d.measuring()
        val title=measure.text("Категории",0f,0f,w-40,16f,true)
        val heights=rows.map {max(28f,measure.text(it.names.joinToString(" / "),0f,0f,(w-64)*.35f,13f)+8)}
        val h=20+title+16+heights.sum()+12+measure.table(0f,0f,w-40,listOf("Категория","Количество","Продажи","Возвраты","Выручка"),data)+20
        d.card(x,y,w,h);d.text("Категории",x+20,y+20,w-40,16f,true)
        var top=y+20+title+16;val max=rows.maxOfOrNull {it.net.abs()} ?: BigInteger.ZERO
        rows.forEachIndexed {i,row->
            d.checkCancelled();val nameW=(w-64)*.35f;val barW=(w-64)*.4f;val valueW=(w-64)*.25f
            d.text(row.names.joinToString(" / "),x+20,top,nameW,13f)
            val barX=x+20+nameW+12;d.rect(barX,top+5,barW,8f,d.c.bg,5f)
            if(max.signum()>0) d.rect(barX,top+5,barW*row.net.abs().toBigDecimal().divide(max.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat(),8f,if(row.net.signum()<0) d.c.red else d.c.green,5f)
            d.text(reportNumber(row.net),barX+barW+12,top,valueW,12f,right=true);top+=heights[i]
        }
        d.table(x+20,top+12,w-40,listOf("Категория","Количество","Продажи","Возвраты","Выручка"),data)
        return h
    }
    private fun payments(d: Draw,report: SalesReport,x: Float,y: Float,w: Float): Float {
        val rows=ReportAnalytics.payments(report).reversed();val colors=listOf(d.c.green,d.c.blue);val h=218f
        d.card(x,y,w,h);d.text("Оплата",x+20,y+20,w-40,16f,true)
        val cx=x+20+63;val cy=y+64+63
        d.canvas?.let {canvas->
            val rect=RectF(cx-55.5f,cy-55.5f,cx+55.5f,cy+55.5f)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=d.c.line;style=Paint.Style.STROKE;strokeWidth=15f}
            canvas.drawArc(rect,-90f,360f,false,paint)
            if(rows.all {it.percent!=null}) {var start=-90f;rows.forEachIndexed {i,row->
                val sweep=row.amount.toBigDecimal().divide(report.net.total.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat()*360
                paint.color=colors[i];canvas.drawArc(rect,start,sweep,false,paint);start+=sweep
            }}
        }
        d.text(reportNumber(report.net.total),cx-44,cy-15,88f,12f,true)
        d.text("BYN",cx-20,cy+7,40f,11f,color=d.c.sub)
        val labelX=x+168;val labelW=w-188
        rows.forEachIndexed {i,row->
            val top=y+76+i*55;d.dot(labelX,top+7,colors[i]);d.text(row.label,labelX+12,top,labelW-12,12f)
            d.text("${reportNumber(row.amount)} · ${row.percent?.let {"$it %"} ?: "—"}",labelX+12,top+20,labelW-12,12f,true)
        }
        return h
    }
    private fun fulfillment(d: Draw,report: SalesReport,x: Float,y: Float,w: Float): Float {
        val rows=ReportAnalytics.fulfillment(report);val colors=listOf(d.c.green,d.c.blue,d.c.yellow);val h=258f
        d.card(x,y,w,h);d.text("Получение заказа",x+20,y+20,w-40,16f,true)
        if(rows.all {it.percent!=null}) {
            var start=x+20;rows.forEachIndexed {i,row->if(row.amount.signum()>0) {
                val width=(w-46)*row.amount.toBigDecimal().divide(report.net.total.toBigDecimal(),8,RoundingMode.HALF_UP).toFloat()
                d.rect(start,y+57,width,12f,colors[i],7f);start+=width+3
            }}
        }
        rows.forEachIndexed {i,row->
            val top=y+87+i*50;if(i>0) d.line(x+20,top-8,w-40)
            d.dot(x+24,top+7,colors[i]);d.text(row.label,x+36,top,(w-52)/2,13f)
            d.text("Чеков: ${row.checks}",x+36,top+20,(w-52)/2,11f,color=d.c.sub)
            d.text(reportNumber(row.amount),x+w/2,top,w/2-20,13f,true,right=true)
            d.text(row.percent?.let {"$it %"} ?: "—",x+w/2,top+20,w/2-20,12f,color=d.c.sub,right=true)
        }
        return h
    }
    private fun employees(d: Draw,report: SalesReport,x: Float,y: Float,w: Float): Float {
        val rows=report.details!!.employees;val measure=d.measuring()
        fun short(row: ReportGroup)=row.names.joinToString(" / ") {full->val p=full.split(Regex("\\s+")).filter {it.isNotBlank()};p.firstOrNull().orEmpty()+p.drop(1).take(2).joinToString("",prefix=if(p.size>1) " " else "") {it.first().uppercaseChar().toString()+"."}}
        fun meta(row: ReportGroup)="Чеков: ${row.checks} · средний ${reportNumber(row.average)}"
        val nameWidth=w-176
        val heights=rows.map {row->maxOf(32f,measure.text(short(row),0f,0f,nameWidth,13f)+4+measure.text(meta(row),0f,0f,w-82,11f),measure.text(reportNumber(row.net),0f,0f,82f,13f,true))+16}
        val title=measure.text("Сотрудники",0f,0f,w-40,16f,true)
        val h=20+title+12+heights.sum()+20+(if(rows.isEmpty()) 24 else 0)
        d.card(x,y,w,h);d.text("Сотрудники",x+20,y+20,w-40,16f,true)
        var top=y+20+title+12
        if(rows.isEmpty()) d.text("Нет данных",x+20,top,w-40,12f,color=d.c.sub)
        rows.forEachIndexed {i,row->
            d.checkCancelled();if(i>0) d.line(x+20,top-8,w-40)
            val parts=row.names.first().split(Regex("\\s+")).filter {it.isNotBlank()}
            d.rect(x+20,top,32f,32f,d.c.soft,10f)
            d.text(parts.take(2).joinToString("") {it.take(1)}.uppercase(),x+24,top+9,24f,11f,true,d.c.green)
            val nameHeight=d.text(short(row),x+62,top,nameWidth,13f)
            d.text(meta(row),x+62,top+nameHeight+4,w-82,11f,color=d.c.sub)
            d.text(reportNumber(row.net),x+w-102,top,82f,13f,true,right=true)
            top+=heights[i]
        }
        return h
    }
}
