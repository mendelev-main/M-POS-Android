package com.mendelev.mpos.documents

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sizes are source document pixels/points, independent of tablet density and UI theme. */
internal object MPosDocumentCanvas {
    fun paint(size:Float,weight:Int=400,color:Int=Color.BLACK,mono:Boolean=false)=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=size;this.color=color;typeface=Typeface.create(if(mono)Typeface.MONOSPACE else Typeface.create("sans-serif",Typeface.NORMAL),weight,false)
    }
    fun layout(text:String,width:Float,size:Float,weight:Int=400,color:Int=Color.BLACK,align:Layout.Alignment=Layout.Alignment.ALIGN_NORMAL,spacing:Float=0f,mono:Boolean=false)=
        StaticLayout.Builder.obtain(text,0,text.length,paint(size,weight,color,mono),width.toInt().coerceAtLeast(1))
            .setIncludePad(false).setAlignment(align).setLineSpacing(spacing,1f).build()
    fun text(canvas:Canvas,text:String,x:Float,y:Float,width:Float,size:Float=10f,weight:Int=400,color:Int=Color.BLACK,align:Layout.Alignment=Layout.Alignment.ALIGN_NORMAL,spacing:Float=0f,mono:Boolean=false):Float {
        val block=layout(text,width,size,weight,color,align,spacing,mono)
        val save=canvas.save();canvas.translate(x,y);block.draw(canvas);canvas.restoreToCount(save);return block.height.toFloat()
    }
    fun lines(text:String,width:Float,size:Float=10f,weight:Int=400):List<String> {
        val block=layout(text,width,size,weight)
        return (0 until block.lineCount).map{ text.substring(block.getLineStart(it),block.getLineEnd(it)).trimEnd('\n') }
    }
    fun round(canvas:Canvas,x:Float,y:Float,w:Float,h:Float,radius:Float,color:Int,stroke:Float=0f) {
        canvas.drawRoundRect(x,y,x+w,y+h,radius,radius,Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;style=if(stroke>0)Paint.Style.STROKE else Paint.Style.FILL;strokeWidth=stroke})
    }
    fun date(at:Long)=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("ru","RU")).format(Date(at))
    fun number(obj:JSONObject,key:String,fallback:Double=0.0):Double = when(val value=obj.opt(key)) {
        is Number->value.toDouble();is String->value.replace(',','.').toDoubleOrNull() ?: fallback;else->fallback
    }
    fun objects(values:JSONArray?):List<JSONObject> = if(values==null)emptyList() else (0 until values.length()).mapNotNull{values.optJSONObject(it)}
    fun strings(values:JSONArray?):List<String> = if(values==null)emptyList() else (0 until values.length()).map{values.optString(it)}
}

/** Shared page surface lets CI render the real layout without mocking PDF serialization. */
internal interface MPosDocumentPages:AutoCloseable {
    val number:Int
    val canvas:Canvas
    fun next()
    fun save(file:File)
}

internal class MPosPdfPages(private val width:Int,private val height:Int):MPosDocumentPages {
    private val document=PdfDocument();private var page:PdfDocument.Page?=null
    override var number=0;private set
    override val canvas:Canvas get()=page!!.canvas
    override fun next(){page?.let(document::finishPage);number++;page=document.startPage(PdfDocument.PageInfo.Builder(width,height,number).create());canvas.drawColor(Color.WHITE)}
    override fun save(file:File){page?.let(document::finishPage);page=null;file.outputStream().use{document.writeTo(it)}}
    override fun close(){page?.let(document::finishPage);page=null;document.close()}
}
