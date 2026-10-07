package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONTokener

/** Full compatible WEB documents with atomic Room ownership and supporting indexes. */
class MPosWebJournalStorage(private val database:MPosDatabase){
    companion object {val KEYS=setOf("webOrderAcceptances","webOrderReadyJournal")}
    private val documents=database.legacyStorageShadowDao()
    private fun marker(key:String):String{require(key in KEYS);return "mpos_web_journal_authority_v1:$key"}
    suspend fun isAuthoritative(key:String)=documents.get(marker(key))!=null
    private fun ack(key:String)=JSONObject().put("ok",true).put("authoritative",true).put("source","room-web-journal").put("key",key)
    suspend fun initialize(key:String,raw:String?):JSONObject=database.withTransaction{if(!isAuthoritative(key)){replace(key,raw);documents.upsert(LegacyStorageShadowEntity(marker(key),"{\"version\":1}",System.currentTimeMillis()))};ack(key)}
    suspend fun read(key:String):JSONObject=database.withTransaction{check(isAuthoritative(key));val doc=documents.get(key);ack(key).put("found",doc!=null).put("payload",doc?.payload?:JSONObject.NULL)}
    suspend fun write(key:String,raw:String):JSONObject=database.withTransaction{check(isAuthoritative(key));replace(key,raw);ack(key)}
    suspend fun remove(key:String):JSONObject=database.withTransaction{check(isAuthoritative(key));replace(key,null);ack(key)}
    private suspend fun replace(key:String,raw:String?){
        require(key in KEYS)
        val value=raw?.let{val p=JSONTokener(it);p.nextValue().also{require(p.nextClean()=='\u0000')}}
        require(value==null||value===JSONObject.NULL||value is JSONObject)
        if(raw==null)documents.delete(key) else documents.upsert(LegacyStorageShadowEntity(key,raw,System.currentTimeMillis()))
        val source=value as? JSONObject?:JSONObject();val now=System.currentTimeMillis()
        if(key=="webOrderAcceptances"){
            val rows=source.keys().asSequence().mapNotNull{id->source.optJSONObject(id)?.let{r->WebAcceptanceProjectionEntity(id,r.optString("stage"),r.optString("readyEstimate"),r.optJSONObject("parked")?.optString("id").orEmpty(),r.optLong("preparedAt"),r.optLong("confirmedAt"),r.toString(),now)}}.toList()
            database.webAcceptanceProjectionDao().clear();if(rows.isNotEmpty())database.webAcceptanceProjectionDao().insertAll(rows)
        }else{
            val rows=source.keys().asSequence().mapNotNull{id->source.optJSONObject(id)?.let{r->WebReadyProjectionEntity(id,r.optString("stage"),r.optLong("createdAt"),r.optLong("confirmedAt"),r.toString(),now)}}.toList()
            database.webReadyProjectionDao().clear();if(rows.isNotEmpty())database.webReadyProjectionDao().insertAll(rows)
        }
    }
}
