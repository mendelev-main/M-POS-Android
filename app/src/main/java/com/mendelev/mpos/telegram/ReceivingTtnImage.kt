package com.mendelev.mpos.telegram

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.MetricAffectingSpan
import com.mendelev.mpos.documents.MPosDocumentCanvas as Doc
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Approved «Паспорт накладной»: fixed approved dark document palette, independent of the tablet theme. */
internal class ReceivingTtnImage(private val report: ReceivingTtn,private val font: Typeface=Typeface.create("sans-serif",Typeface.NORMAL)) {
    companion object {private const val WIDTH=1200;private const val LIMIT=1600;private const val LEFT=40f;private const val BODY=1120f}
    private val ink=Color.rgb(238,241,245);private val muted=Color.rgb(170,178,191);private val line=Color.rgb(55,63,77)
    private val paper=Color.rgb(34,38,49);private val soft=Color.rgb(27,31,39)
    private val green=Color.rgb(143,224,190);private val greenSoft=Color.rgb(38,62,53)
    private inner class WeightSpan(private val weight: Int): MetricAffectingSpan() {
        private fun apply(p: TextPaint) {p.typeface=font;p.fontVariationSettings="'wght' $weight"}
        override fun updateDrawState(tp: TextPaint)=apply(tp)
        override fun updateMeasureState(tp: TextPaint)=apply(tp)
    }
    private val title=SpannableStringBuilder("ТТН № ${report.number} ").apply {
        val start=length;append("от")
        setSpan(RelativeSizeSpan(13f/21f),start,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(muted),start,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(WeightSpan(500),start,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        append(" ${report.invoiceDate}")
    }
    private fun layout(value: CharSequence,width: Float,size: Float=21f,weight: Int=400,color: Int=ink,align: Layout.Alignment=Layout.Alignment.ALIGN_NORMAL): StaticLayout {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=size;this.color=color;typeface=font;fontVariationSettings="'wght' $weight"}
        return StaticLayout.Builder.obtain(value,0,value.length,paint,width.toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(align).build()
    }
    private val headH=maxOf(44,layout(title,BODY-190,33.6f,750).height)
    private val passportTop=28+headH+28
    private val sumWidth=BODY*1.05f/3.05f
    private val detailsWidth=BODY-sumWidth
    private val supplierHeight=layout(report.supplier,detailsWidth-50,27.2f,700).height
    private val supplierBand=maxOf(105,22+20+7+supplierHeight+22)
    private val detailBand=maxOf(97,22+20+8+maxOf(layout(report.accepted,detailsWidth/2-50,20.8f,600).height,layout(report.employee,detailsWidth/2-50,20.8f,600).height)+22)
    private val sumText=SpannableStringBuilder("${report.total} ").apply {
        val start=length;append("BYN")
        setSpan(RelativeSizeSpan(14f/29f),start,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(muted),start,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(WeightSpan(600),start,length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    private val sumAmountHeight=layout(sumText,sumWidth-50,46.4f,750).height
    private val countLabel=report.rows.size.let {n->"$n "+when {n%100 in 11..14->"позиций";n%10==1->"позиция";n%10 in 2..4->"позиции";else->"позиций"}}
    private val passportH=maxOf(supplierBand+detailBand,22+20+8+sumAmountHeight+8+20+22)
    private val tableTop=passportTop+passportH+32
    private val start=tableTop+36
    private val renderedRows=report.rows+report.note.chunked(100).mapIndexed {i,text->TtnRow(if(i==0) "Комментарий" else "",text,"","","")}
    // Same four column proportions and right-aligned numeric values as the approved mockup.
    private val widths=floatArrayOf(BODY*.39f,BODY*.19f,BODY*.23f,BODY*.19f)
    private val heights=renderedRows.map {r->ceil(maxOf(
        layout(r.name,widths[0]-16,20.8f,650).height+layout(r.packing,widths[0]-16,17.6f).height+5,
        layout(r.quantity,widths[1]-16,20.8f).height,layout(r.price,widths[2]-16,20.8f).height,layout(r.amount,widths[3]-8,20.8f,650).height)+24.0).toInt()}
    private val ranges=TtnPages.paginate(heights,LIMIT-start-28)
    val pages get()=ranges.size
    fun render(index: Int): ByteArray {
        val range=ranges[index];val height=(start+range.sumOf {heights[it]}+28).coerceAtLeast(320)
        val bitmap=Bitmap.createBitmap(WIDTH,height,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap);canvas.drawColor(paper)
            fun text(value: CharSequence,x: Float,y: Float,w: Float,size: Float=21f,weight: Int=400,color: Int=ink,align: Layout.Alignment=Layout.Alignment.ALIGN_NORMAL): Float {
                val block=layout(value,w,size,weight,color,align);val save=canvas.save();canvas.translate(x,y);block.draw(canvas);canvas.restoreToCount(save);return block.height.toFloat()
            }
            fun rule(x1: Float,y: Float,x2: Float)=canvas.drawLine(x1,y,x2,y,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=line;strokeWidth=1.6f})
            text(title,LEFT,28f,BODY-190,33.6f,750)
            Doc.round(canvas,WIDTH-190f,30f,150f,44f,12f,greenSoft)
            val check=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=green;strokeWidth=2.5f;strokeCap=Paint.Cap.ROUND}
            canvas.drawLine(WIDTH-174f,51f,WIDTH-168f,57f,check);canvas.drawLine(WIDTH-168f,57f,WIDTH-157f,45f,check)
            text("Принята",WIDTH-147f,38f,105f,19.2f,650,green)
            val top=passportTop.toFloat();val split=LEFT+detailsWidth
            // Rounded clipping preserves the shared outer contour and the grey total column.
            val clip=android.graphics.Path().apply {addRoundRect(LEFT,top,WIDTH-LEFT,top+passportH,20.8f,20.8f,android.graphics.Path.Direction.CW)}
            val saved=canvas.save();canvas.clipPath(clip)
            canvas.drawRect(LEFT,top,WIDTH-LEFT,top+passportH,Paint().apply {color=paper})
            canvas.drawRect(split,top,WIDTH-LEFT,top+passportH,Paint().apply {color=soft})
            canvas.restoreToCount(saved)
            Doc.round(canvas,LEFT,top,BODY,passportH.toFloat(),20.8f,line,1.6f)
            canvas.drawLine(split,top,split,top+passportH,Paint().apply {color=line;strokeWidth=1.6f})
            rule(LEFT,top+supplierBand,split)
            text("Поставщик",LEFT+25.6f,top+22.4f,detailsWidth-51.2f,19.2f,color=muted)
            text(report.supplier,LEFT+25.6f,top+49.6f,detailsWidth-51.2f,27.2f,700)
            val lower=top+supplierBand
            text("Принято",LEFT+25.6f,lower+22.4f,detailsWidth/2-51.2f,19.2f,color=muted)
            text(report.accepted,LEFT+25.6f,lower+50.4f,detailsWidth/2-51.2f,20.8f,600)
            text("Сотрудник",LEFT+detailsWidth/2+25.6f,lower+22.4f,detailsWidth/2-51.2f,19.2f,color=muted)
            text(report.employee,LEFT+detailsWidth/2+25.6f,lower+50.4f,detailsWidth/2-51.2f,20.8f,600)
            val sumTop=top+(passportH-(20+8+sumAmountHeight+8+20))/2f
            text("Итого по ТТН",split+25.6f,sumTop,sumWidth-51.2f,19.2f,color=muted)
            text(sumText,split+25.6f,sumTop+28,sumWidth-51.2f,46.4f,750)
            text(countLabel,split+25.6f,sumTop+28+sumAmountHeight+8,sumWidth-51.2f,19.2f,color=muted)
            var x=LEFT
            listOf("Товары","Принято","Цена за ед","Сумма").forEachIndexed {i,label->
                text(label,x,tableTop.toFloat(),widths[i]-if(i==3) 0 else 8,17.6f,600,muted,if(i==0) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE);x+=widths[i]
            }
            var y=start.toFloat();rule(LEFT,y-10,WIDTH-LEFT)
            for(i in range) {
                val row=renderedRows[i];x=LEFT
                val nameH=text(row.name,x,y+8,widths[0]-16,20.8f,650)
                text(row.packing,x,y+8+nameH+5,widths[0]-16,17.6f,color=muted);x+=widths[0]
                listOf(row.quantity,row.price,row.amount).forEachIndexed {n,value->
                    val col=n+1;text(value,x,y+8,widths[col]-if(col==3) 0 else 8,20.8f,if(col==3) 650 else 400,align=Layout.Alignment.ALIGN_OPPOSITE);x+=widths[col]
                }
                y+=heights[i];rule(LEFT,y-4,WIDTH-LEFT)
            }
            if(pages>1) text("${index+1} / $pages",WIDTH-160f,height-22f,120f,16f,color=muted,align=Layout.Alignment.ALIGN_OPPOSITE)
            return ByteArrayOutputStream().use {out->check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray().also {require(it.size<=10_000_000)}}
        } finally {bitmap.recycle()}
    }
}
