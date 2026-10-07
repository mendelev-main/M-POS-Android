package com.mendelev.mpos.data

import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Reviewed table/booking commands. No shift, role, capacity or past-date policy is added. */
object MPosHallEngine {
    private val iso=DateTimeFormatterBuilder().appendInstant(3).toFormatter()
    private fun number(v:Any?)=MPosPurchaseEngine.number(v)
    private fun numeric(v:Any?)=number(v).let{if(it.isNaN()||it==0.0)0.0 else it}
    private fun trim(v:Any?)=MPosOrderContextEngine.trim(MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(v,"")))
    private fun rows(a:JSONArray)=(0 until a.length()).map{a.getJSONObject(it)}
    private fun parse(raw:Any?,zone:ZoneId):Long? {
        if(raw==null)return null
        if(raw===JSONObject.NULL)return 0L
        if(raw is Number)return raw.toDouble().takeIf{it.isFinite()}?.toLong()
        if(raw is Boolean)return if(raw)1L else 0L
        val text=MPosAvailabilityEngine.text(raw)
        return runCatching{Instant.parse(text).toEpochMilli()}.getOrNull()
            ?:runCatching{java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli()}.getOrNull()
            ?:runCatching{LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()}.getOrNull()
            ?:runCatching{LocalDate.parse(text).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()}.getOrNull()
    }
    private fun window(c:JSONObject):Pair<Long,Long> {
        val date=c.getString("date");val time=c.getString("time");val d=date.split('-').map{it.toInt()};val t=time.split(':').map{it.toInt()}
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date)&&Regex("[0-9]{2}:[0-9]{2}").matches(time))
        require(d[1] in 1..12&&d[2] in 1..31&&t[0] in 0..24&&t[1] in 0..59&&(t[0]!=24||t[1]==0))
        val start=LocalDate.of(d[0],d[1],1).plusDays((d[2]-1).toLong()).atStartOfDay().plusHours(t[0].toLong()).plusMinutes(t[1].toLong()).atZone(ZoneId.of(c.getString("zone"))).toInstant().toEpochMilli()
        val duration=number(c.opt("duration"));require(duration.isFinite());val end=start+ (duration*60000).toLong();return start to end
    }
    fun calculate(c:JSONObject,hall:JSONArray,bookings:JSONArray):JSONObject {
        require(c.getInt("version")==1);val tables=JSONArray(hall.toString());val records=JSONArray(bookings.toString());val id=c.opt("id");val operation=c.getString("operation")
        var nextTables=tables;var nextBookings=records
        val changed=JSONArray()
        when(operation){
            "table-create"->{
                val name=trim(c.opt("name"));require(name.isNotEmpty()){"Введите название стола"}
                check(rows(tables).none{MPosSupplyParity.same(it.opt("id"),id)}){"Стол уже существует"}
                val n=max(0.0,rows(tables).maxOfOrNull{numeric(it.opt("number"))}?:0.0)+1
                tables.put(JSONObject().put("id",id).put("number",n).put("name",name).put("shape",if(c.opt("shape")=="rectangle")"rectangle" else "square").put("rotation",0).put("x",7+(n-1)%4*23).put("y",8+floor((n-1)/4)%5*17));changed.put("hallTables")
            }
            "table-delete"->{nextTables=JSONArray(rows(tables).filterNot{MPosSupplyParity.same(it.opt("id"),id)});nextBookings=JSONArray(rows(records).filterNot{MPosSupplyParity.same(it.opt("tableId"),id)});changed.put("hallTables").put("bookings")}
            "table-edit","table-rotate","table-move"->{
                val table=MPosReceivingEngine.find(tables,id);check(table!=null){"Стол не найден"}
                when(operation){
                    "table-edit"->{val name=trim(c.opt("name"));require(name.isNotEmpty()){"Введите название стола"};table.put("name",name)}
                    "table-rotate"->{val delta=number(c.opt("delta"));require(delta.isFinite());table.put("rotation",(numeric(table.opt("rotation"))+delta+360)%360)}
                    else->{val x=number(c.opt("x"));val y=number(c.opt("y"));require(x.isFinite()&&y.isFinite());table.put("x",max(0.0,min(94.0,x))).put("y",max(0.0,min(88.0,y)))}
                };changed.put("hallTables")
            }
            "booking-cancel"->{val booking=MPosReceivingEngine.find(records,id);check(booking!=null){"Бронирование не найдено"};booking.put("status","cancelled");changed.put("bookings")}
            "booking-create","booking-edit"->{
                val editing=operation=="booking-edit";val booking=if(editing)MPosReceivingEngine.find(records,id) else null;check(!editing||booking!=null){"Бронирование не найдено"}
                val name=trim(c.opt("guestName"));if(!editing)require(name.isNotEmpty()){"Введите имя гостя"}
                if(!editing)check(rows(records).none{MPosSupplyParity.same(it.opt("id"),id)}){"Бронирование уже существует"}
                val tableId=if(editing)booking!!.opt("tableId") else c.opt("tableId")
                // Legacy permits editing cancelled/orphan bookings; do not add a table-existence gate.
                val (start,end)=window(c);val zone=ZoneId.of(c.getString("zone"))
                check(rows(records).none{b->(!editing||!MPosSupplyParity.same(b.opt("id"),id))&&MPosSupplyParity.same(b.opt("tableId"),tableId)&&b.opt("status")!="cancelled"&&parse(b.opt("startAt"),zone)?.let{bs->parse(b.opt("endAt"),zone)?.let{be->start<be&&end>bs}}==true}){"На выбранное время стол уже забронирован"}
                require(name.isNotEmpty()){"Введите имя гостя"};val guests=max(1.0,number(MPosJsonNumbers.fallback(c.opt("guests"),2)));require(guests.isFinite())
                val b=booking?:JSONObject().put("id",id).put("tableId",tableId)
                b.put("date",c.getString("date")).put("startAt",iso.format(Instant.ofEpochMilli(start))).put("endAt",iso.format(Instant.ofEpochMilli(end))).put("guestName",name).put("phone",trim(c.opt("phone"))).put("guests",guests).put("note",trim(c.opt("note")))
                if(!editing){b.put("status","confirmed").put("createdAt",c.getLong("now"));records.put(b)};changed.put("bookings")
            }
            else->throw IllegalArgumentException("unsupported hall operation")
        }
        return JSONObject().put("hallTables",nextTables).put("bookings",nextBookings).put("changedKeys",changed)
    }
}
