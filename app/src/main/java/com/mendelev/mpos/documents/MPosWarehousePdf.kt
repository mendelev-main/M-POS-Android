package com.mendelev.mpos.documents

import android.graphics.Color
import org.json.JSONObject
import java.io.File
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Landscape warehouse template from WarehouseReportPDF.swift. */
internal object MPosWarehousePdf {
    fun write(report:JSONObject,file:File,surface:MPosDocumentPages=MPosPdfPages(842,595))=surface.use{pages->
        val d=MPosDocumentCanvas;val ink=Color.rgb(31,31,31);val muted=Color.rgb(107,107,107);val paper=Color.rgb(245,245,245)
        var y=0f
        fun text(s:String,x:Float,top:Float,w:Float,size:Float=10f,bold:Boolean=false,color:Int=ink)=d.text(pages.canvas,s,x,top,w,size,if(bold)700 else 400,color)
        fun next(){
            pages.next();y=36f
            if(pages.number==1){
                val brand=report.optString("company").trim().ifBlank{"Название заведения не указано"}
                val lines=d.lines(brand,738f,18f,700)
                text(lines.firstOrNull().orEmpty()+if(lines.size>1)"…" else "",52f,34f,738f,18f,true,ink)
                text(report.optString("title","Складской учёт")+"  ·  "+report.optString("period"),52f,69f,738f,11f,false,muted);y=116f
            }
            text("Сформировано: "+report.optString("generatedAt")+if(pages.number>1)"  ·  Период: "+report.optString("period") else "",36f,568f,705f,10f,false,muted)
            text(pages.number.toString(),776f,568f,30f,10f,false,muted)
        }
        next()
        d.objects(report.optJSONArray("sections")).forEachIndexed { sectionIndex,section ->
            val headers=d.strings(section.optJSONArray("headers"))
            if(headers.isEmpty())return@forEachIndexed
            val widths=if(headers.size<=6)List(headers.size){770f/headers.size}else listOf(170f)+List(headers.size-1){600f/(headers.size-1)}
            val headerLines=headers.mapIndexed{i,s->d.lines(s,widths[i]-12f,9f,700)}
            val headerHeight=headerLines.maxOf{it.size}*15f+12f
            if(y+headerHeight+65f>550f)next()
            fun header(){
                text(section.optString("title"),36f,y,770f,13f,true);y+=26f
                d.round(pages.canvas,36f,y,770f,headerHeight,6f,paper)
                var x=36f;headerLines.forEachIndexed{i,lines->lines.forEachIndexed{n,s->text(s,x+6f,y+6f+n*15f,widths[i]-12f,9f,true,ink)};x+=widths[i]};y+=headerHeight+6f
            }
            fun continuation(){next();header()}
            y+=12f;header()
            val rows=section.optJSONArray("rows")
            val count=max(1,rows?.length()?:0)
            for(index in 0 until count){
                val row=rows?.optJSONArray(index)
                val cells=headers.indices.map{i->d.lines(if(row==null)"—" else row.optString(i),widths[i]-12f)}
                val lines=cells.maxOf{it.size};val fullHeight=lines*15f+12f
                if(fullHeight<=595f-45f-36f-26f-headerHeight-6f&&y+fullHeight>550f)continuation()
                var offset=0
                while(offset<lines){
                    var capacity=floor((550f-y-12f)/15f).toInt()
                    if(capacity<1){continuation();capacity=floor((550f-y-12f)/15f).toInt()}
                    val take=min(lines-offset,capacity.coerceAtLeast(1));val h=take*15f+12f
                    var x=36f;cells.forEachIndexed{i,cell->cell.drop(offset).take(take).forEachIndexed{n,s->text(s,x+6f,y+6f+n*15f,widths[i]-12f)};x+=widths[i]};y+=h+4f;offset+=take
                    if(offset<lines)continuation()
                }
            }
            y+=30f
        }
        val notes=d.strings(report.optJSONArray("notes"))
        if(notes.isNotEmpty()){
            next();text("Как читать отчёт",36f,y,770f,16f,true);y+=38f
            notes.forEach{note->d.lines(note,742f).chunked(18).forEach{lines->
                val h=lines.size*15f+24f;if(y+h>550f)next()
                d.round(pages.canvas,36f,y,770f,h,8f,paper)
                lines.forEachIndexed{n,s->text(s,50f,y+12f+n*15f,742f,10f,false,muted)};y+=h+12f
            }}
        }
        pages.save(file)
    }
}
