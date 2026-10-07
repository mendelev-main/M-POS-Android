package com.mendelev.mpos.print

import androidx.room.withTransaction
import com.mendelev.mpos.data.LegacyStorageShadowEntity
import com.mendelev.mpos.data.MPosDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Internal outcomes are intentionally excluded from backups and automatic replay. */
class MPosPrintJobRepository(private val database:MPosDatabase) {
    companion object {const val KEY="mpos_print_jobs_v1"}
    private val documents=database.legacyStorageShadowDao()
    private suspend fun read()=documents.get(KEY)?.let{JSONArray(it.payload)}?:JSONArray()
    private suspend fun write(rows:JSONArray)=documents.upsert(LegacyStorageShadowEntity(KEY,rows.toString(),System.currentTimeMillis()))
    suspend fun recover()=database.withTransaction {
        val rows=read();for(i in 0 until rows.length()){val row=rows.getJSONObject(i);when(row.opt("status")){"sending"->row.put("status","uncertain");"queued"->row.put("status","cancelled")}}
        if(rows.length()>0)write(rows)
    }
    suspend fun append(input:JSONObject,jobs:JSONArray):List<JSONObject> =database.withTransaction {
        val rows=read();val existing=(0 until rows.length()).map{rows.getJSONObject(it)}
        val pending=existing.filter{it.opt("status") in setOf("queued","sending")};check(pending.size+jobs.length()<=128){"Очередь печати заполнена"}
        val result=(0 until jobs.length()).map{i->JSONObject().put("id",UUID.randomUUID().toString()).put("requestId",input.optString("requestId")).put("trigger",input.getString("trigger")).put("sourceId",input.optJSONObject("order")?.opt("id")?:JSONObject.NULL).put("status","queued").put("createdAt",System.currentTimeMillis()).put("order",jobs.getJSONObject(i))}
        val history=existing.filter{it.opt("status") !in setOf("queued","sending")}.takeLast(maxOf(0,200-pending.size-result.size))
        write(JSONArray(history+pending+result.map{JSONObject(it.toString()).apply{remove("order")}}));result
    }
    suspend fun transition(id:String,to:String)=database.withTransaction {
        val rows=read();val row=(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{it.getString("id")==id}?:error("print job missing")
        val from=row.getString("status");check(when(to){"sending"->from=="queued";"sent","failed","uncertain"->from=="sending";"cancelled"->from=="queued";else->false}){"invalid print job transition"}
        row.put("status",to).put("updatedAt",System.currentTimeMillis());write(rows)
    }
}
