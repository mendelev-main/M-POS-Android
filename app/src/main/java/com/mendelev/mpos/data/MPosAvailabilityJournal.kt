package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A receipt grants one publication attempt. Restart/reconnect cannot grant another. */
class MPosAvailabilityJournal(private val database:MPosDatabase) {
    companion object {const val TICKET="mpos_availability_ticket_v1";const val REVISION="mpos_availability_revision_v1"}
    private val docs=database.legacyStorageShadowDao()
    private fun marker(id:String)="mpos_availability_payment_v1:$id"
    private fun safe(value:Double)=value.isFinite()&&value>=0&&value<=9007199254740991.0&&value==kotlin.math.floor(value)
    suspend fun prepare(raw:String):JSONObject=database.withTransaction {
        val c=JSONObject(raw);require(c.getInt("version")==1)
        check(MPosOrderStorage(database).isAuthoritative());val id=c.getString("id")
        check(database.orderProjectionDao().get(id)!=null){"payment not persisted"}
        val out=JSONObject().put("ok",true).put("authoritative",true).put("send",false)
        if(docs.get(marker(id))!=null)return@withTransaction out
        val network=c.getJSONObject("network");check(network.getString("backendUrl").trim().toHttpUrl().isHttps);check(network.optString("deviceKey").isNotBlank())
        check(c.get("previous") is Number);val previous=c.getDouble("previous");check(safe(previous))
        val own=docs.get(REVISION)?.let{JSONObject(it.payload).getDouble("revision")}?:0.0;check(safe(own))
        val at=c.getLong("at");val revision=maxOf(at.toDouble(),previous+1,own+1);check(safe(revision))
        val envelope=MPosCatalogStorage(database).read();check(envelope.getBoolean("found"));val products=JSONArray(envelope.getString("payload"))
        val settlements=linkedSetOf<String>();val ids=c.getJSONArray("ids")
        fun add(value:Any?){MPosAvailabilityEngine.text(value).trim{it.isWhitespace()||it=='\uFEFF'}.takeIf{it.isNotEmpty()}?.let(settlements::add)}
        for(i in 0 until ids.length())add(ids.opt(i))
        val orders=database.orderProjectionDao().allOrders().map{JSONObject(it.payload)}.filter{MPosJsonNumbers.truthy(it.opt("webOrderId"))}
            .sortedWith{a,b->val diff=MPosJsonNumbers.amount(b,"timestamp")-MPosJsonNumbers.amount(a,"timestamp");if(diff.isNaN()||diff==0.0)0 else if(diff>0)1 else -1}
        orders.forEach{add(it.opt("webOrderId"))}
        val body=JSONObject().put("version",1).put("revision",revision.toLong()).put("sampledAt",DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(at)))
            .put("items",MPosAvailabilityEngine.items(products)).put("settledWebOrderIds",JSONArray(settlements.take(100)))
        val token=UUID.randomUUID().toString()
        docs.upsert(LegacyStorageShadowEntity(REVISION,JSONObject().put("revision",revision.toLong()).toString(),at))
        docs.upsert(LegacyStorageShadowEntity(marker(id),"{\"attempted\":true}",at))
        docs.upsert(LegacyStorageShadowEntity(TICKET,JSONObject().put("token",token).put("id",id).put("network",network).put("body",body).toString(),at))
        out.put("send",true).put("token",token).put("body",body)
    }
    suspend fun consume(token:String,ordered:JSONObject):JSONObject=database.withTransaction {
        val value=docs.get(TICKET)?:error("no payment publication ticket");val ticket=JSONObject(value.payload)
        check(ticket.getString("token")==token);check(database.orderProjectionDao().get(ticket.getString("id"))!=null)
        val original=ticket.getJSONObject("body")
        check(ordered.keys().asSequence().toSet()==original.keys().asSequence().toSet())
        check(ordered.getLong("revision")==original.getLong("revision")&&ordered.getString("sampledAt")==original.getString("sampledAt")&&ordered.getInt("version")==1)
        check(ordered.getJSONArray("settledWebOrderIds").toString()==original.getJSONArray("settledWebOrderIds").toString())
        fun counts(rows:JSONArray):Map<Pair<String,Double?>,Int>{
            val counts=mutableMapOf<Pair<String,Double?>,Int>()
            for(i in 0 until rows.length()){
                val row=rows.getJSONObject(i);check(row.keys().asSequence().toSet()==setOf("externalId","quantity"))
                check(row.get("externalId") is String);val qty=row.get("quantity");check(qty===JSONObject.NULL||qty is Number)
                val key=row.getString("externalId") to if(qty===JSONObject.NULL)null else (qty as Number).toDouble()
                counts[key]=(counts[key]?:0)+1
            };return counts
        }
        check(counts(ordered.getJSONArray("items"))==counts(original.getJSONArray("items")))
        original.put("items",ordered.getJSONArray("items"))
        docs.delete(TICKET) // Socket failure/interruption cannot reuse the permit.
        ticket
    }
}
