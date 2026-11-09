package com.mendelev.mpos.telegram

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mendelev.mpos.domain.catalog.unitLabel
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

enum class OwnerPageKind { OVERVIEW, CATEGORIES, PRODUCTS }
data class OwnerPage(val kind: OwnerPageKind,val rows: List<OwnerRank>)
object OwnerAnalyticsImage {
    private const val WIDTH=1080
    private val bg=Color.rgb(13,22,38);private val card=Color.rgb(27,41,60);private val ink=Color.rgb(236,241,250)
    private val muted=Color.rgb(169,184,205)
    fun money(v: BigInteger)=BigDecimal(v,2).toPlainString()+" BYN"
    fun quantities(values: Map<String,BigDecimal>)=values.entries.sortedBy {it.key}.joinToString("; ") {(unit,value)->value.stripTrailingZeros().toPlainString()+" "+if(unit=="batch") "порц." else unitLabel(unit)}.ifBlank {"0"}
    fun pages(report: OwnerAnalyticsReport): List<OwnerPage> = listOf(OwnerPage(OwnerPageKind.OVERVIEW,report.current.products.filter {it.sold.isNotEmpty()}.take(8)))+
        report.current.categories.filter {it.sold.isNotEmpty()}.drop(8).chunked(24).map {OwnerPage(OwnerPageKind.CATEGORIES,it)}+
        report.current.products.filter {it.sold.isNotEmpty()}.drop(8).chunked(24).map {OwnerPage(OwnerPageKind.PRODUCTS,it)}
    private class Surface(private val canvas: Canvas?) {
        var y=50f
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun round(x: Float,top: Float,w: Float,h: Float,color: Int=card,radius: Float=22f) {paint.color=color;canvas?.drawRoundRect(x,top,x+w,top+h,radius,radius,paint)}
        fun text(value: String,x: Float,top: Float,width: Float,size: Float=23f,bold: Boolean=false,color: Int=ink,align: Layout.Alignment=Layout.Alignment.ALIGN_NORMAL): Float {
            val p=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color;typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)}
            val layout=StaticLayout.Builder.obtain(value,0,value.length,p,width.toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(align).setLineSpacing(3f,1f).build()
            if(top>=0) canvas?.let {val save=it.save();it.translate(x,top);layout.draw(it);it.restoreToCount(save)}
            return layout.height.toFloat()
        }
        fun heading(value: String) {y+=text(value,58f,y,964f,26f,true)+22f}
        fun header(report: OwnerAnalyticsReport,index: Int,total: Int) {
            text("M / POS",58f,y,420f,30f,true)
            val label=if(report.monthly) "Итоги месяца" else "Итоги смены"
            val page=if(total>1) " · ${index+1}/$total" else ""
            text(label+page,500f,y+5,522f,23f,true,align=Layout.Alignment.ALIGN_OPPOSITE)
            y+=70f
        }
        fun metrics(report: OwnerAnalyticsReport) {
            val c=report.current
            val values=listOf("Выручка" to money(c.net),"Прибыль" to money(c.profit),"Чеков" to c.checks.toString(),"Возвраты" to money(c.refunds))
            values.chunked(2).forEach {row->
                val h=maxOf(132f,row.maxOf {text(it.second,0f,-10000f,420f,38f,true)+70f})
                row.forEachIndexed {index,(label,value)->
                    val x=58f+index*492f
                    round(x,y,472f,h)
                    text(label,x+24,y+20,424f,21f,color=muted)
                    text(value,x+24,y+57,424f,38f,true)
                }
                y+=h+16f
            }
            y+=20f
        }
        fun ranks(rows: List<OwnerRank>,all: List<OwnerRank>,title: String,others: Boolean=false) {
            heading(title)
            text("Наименование",76f,y,480f,18f,color=muted)
            text("Доля",600f,y,100f,18f,color=muted,align=Layout.Alignment.ALIGN_OPPOSITE)
            text("Сумма",730f,y,272f,18f,color=muted,align=Layout.Alignment.ALIGN_OPPOSITE);y+=36f
            val shown=rows.toMutableList()
            if(others && all.size>rows.size) shown+=OwnerRank("other","Остальные категории",all.drop(rows.size).fold(BigInteger.ZERO) {a,r->a+r.sales},BigInteger.ZERO,emptyMap(),emptyMap())
            val shares=OwnerAnalyticsRules.percentageTenths(all.map {it.sales})
            val byKey=all.mapIndexed {i,r->r.key to shares[i]}.toMap()
            if(shown.isEmpty()) {y+=text("Нет продаж",76f,y+12,920f,22f,color=muted)+40f;return}
            shown.forEach {r->
                val share=if(r.key=="other") shares.drop(rows.size).sum() else byKey[r.key] ?: 0
                val nameHeight=text(r.name,0f,-10000f,480f,23f)
                val amountHeight=text(money(r.sales),0f,-10000f,272f,23f)
                val h=maxOf(nameHeight,amountHeight)+48f
                text(r.name,76f,y+14,480f,23f)
                text(BigDecimal.valueOf(share.toLong(),1).stripTrailingZeros().toPlainString()+"%",600f,y+14,100f,23f,true,align=Layout.Alignment.ALIGN_OPPOSITE)
                text(money(r.sales),730f,y+14,272f,23f,true,align=Layout.Alignment.ALIGN_OPPOSITE)
                y+=h+7f
            }
            y+=26f
        }
        fun footer(report: OwnerAnalyticsReport) {
            y+=text("Доли — от продаж товаров после скидок, без доставки.",58f,y,964f,17f,color=muted)+18f
            report.warnings.forEach {y+=text(it,58f,y,964f,17f,color=muted)+12f}
            y+=22f
        }
    }
    private fun draw(report: OwnerAnalyticsReport,index: Int,surface: Surface) {
        val pages=pages(report);val page=pages[index]
            surface.header(report,index,pages.size)
            when(page.kind) {
                OwnerPageKind.OVERVIEW->{surface.metrics(report);surface.ranks(report.current.categories.filter {it.sold.isNotEmpty()}.take(8),report.current.categories.filter {it.sold.isNotEmpty()},"Продажи категорий",true);surface.ranks(page.rows,report.current.products.filter {it.sold.isNotEmpty()},"Продажи позиций")}
                OwnerPageKind.CATEGORIES->surface.ranks(page.rows,report.current.categories.filter {it.sold.isNotEmpty()},"Категории · продолжение")
                OwnerPageKind.PRODUCTS->surface.ranks(page.rows,report.current.products.filter {it.sold.isNotEmpty()},"Позиции · продолжение")
            }
            surface.footer(report)
        }
    fun render(report: OwnerAnalyticsReport,index: Int): ByteArray {
        fun draw(surface: Surface)=draw(report,index,surface)
        val measured=Surface(null).also(::draw);val height=measured.y.toInt()+1
        require(height in 1..(10000-WIDTH)) {"Слишком длинная страница аналитики для Telegram"}
        val bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888)
        return try {
            val canvas=Canvas(bitmap);canvas.drawColor(bg);draw(Surface(canvas))
            ByteArrayOutputStream().use {out->check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}
        } finally {bitmap.recycle()}
    }
    fun pdf(report: OwnerAnalyticsReport): ByteArray {
        val document=android.graphics.pdf.PdfDocument()
        try {
            pages(report).indices.forEach {index->
                val measured=Surface(null).also {draw(report,index,it)}
                val height=measured.y.toInt()+1
                require(height in 1..(10000-WIDTH))
                val page=document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(WIDTH,height,index+1).create())
                page.canvas.drawColor(bg);draw(report,index,Surface(page.canvas));document.finishPage(page)
            }
            return ByteArrayOutputStream().use {out->document.writeTo(out);out.toByteArray()}
        } finally {document.close()}
    }
}
