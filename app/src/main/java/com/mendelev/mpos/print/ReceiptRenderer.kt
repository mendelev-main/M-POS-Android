package com.mendelev.mpos.print

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mendelev.mpos.data.local.PrinterConfiguration
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

object ReceiptRenderer {
    private fun layout(text: String,width: Float,row: PrintRow,align: Layout.Alignment): StaticLayout {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {textSize=row.size;color=Color.BLACK;typeface=Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),row.weight,false)}
        return StaticLayout.Builder.obtain(text,0,text.length,paint,width.toInt().coerceAtLeast(1)).setIncludePad(false).setAlignment(align).build()
    }
    fun render(page: ReceiptPage): Bitmap {
        val content=page.width-page.margin*2
        data class Measured(val row: PrintRow,val left: StaticLayout?,val right: StaticLayout?,val height: Int)
        require(page.rows.size<=5000) {"Слишком длинный чек"}
        val measured=page.rows.map {r->
            val left=if(r.separator || r.left.isEmpty()) null else layout(r.left,if(r.right!=null)content*.55f else content,r,if(r.centered)Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            val right=r.right?.let {layout(it,content*.43f,r,Layout.Alignment.ALIGN_OPPOSITE)}
            Measured(r,left,right,if(r.separator)1 else maxOf(left?.height ?: 0,right?.height ?: 0)+2)
        }
        val height=ceil(measured.sumOf {(it.height+it.row.gap).toDouble()}+page.top+page.bottom).toInt()
        require(height in 1..20000) {"Чек слишком длинный; уменьшите количество строк"}
        return Bitmap.createBitmap(page.width,height,Bitmap.Config.ARGB_8888).also {bitmap->
            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);var y=page.top
            val rule=Paint().apply {color=Color.BLACK;strokeWidth=1f;pathEffect=DashPathEffect(floatArrayOf(3f,3f),0f)}
            measured.forEach {m->
                if(m.row.separator) canvas.drawLine(page.margin,y,page.width-page.margin,y,rule)
                else {val save=canvas.save();canvas.translate(page.margin,y);m.left?.draw(canvas);canvas.restoreToCount(save)
                    m.right?.let {val r=canvas.save();canvas.translate(page.margin+content*.57f,y);it.draw(canvas);canvas.restoreToCount(r)}}
                y+=m.height+m.row.gap
            }
        }
    }
    fun encode(document: PrintDocument,p: PrinterConfiguration): ByteArray {
        val page=PrintDocuments.page(document,p)
        val body=render(page).let {bitmap->try {raster(bitmap)} finally {bitmap.recycle()}}
        return EscPosCommands.hex(p.initCommand)+body+byteArrayOf(10)+EscPosCommands.hex(p.cutCommand)
    }
    internal fun raster(bitmap: Bitmap): ByteArray {
        val width=(bitmap.width+7)/8
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(29,118,48,0,(width and 255).toByte(),(width shr 8).toByte(),(bitmap.height and 255).toByte(),(bitmap.height shr 8).toByte()))
            for(y in 0 until bitmap.height) for(xb in 0 until width) {
                var value=0
                for(bit in 0..7) {val x=xb*8+bit;if(x<bitmap.width) {val pixel=bitmap.getPixel(x,y);if((Color.red(pixel)*299+Color.green(pixel)*587+Color.blue(pixel)*114)<180000) value=value or (128 shr bit)}}
                write(value)
            }
        }.toByteArray()
    }
}
