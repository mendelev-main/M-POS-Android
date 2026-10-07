package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Compatible supply documents; full JSON remains authoritative, including extensions. */
class MPosSupplyStorage(private val database:MPosDatabase) {
    companion object {val KEYS=setOf("suppliers","purchaseOrders","receivings","receivingDraft")}
    private val documents=database.legacyStorageShadowDao()
    private fun marker(key:String):String {require(key in KEYS);return "mpos_supply_authority_v1:$key"}
    suspend fun isAuthoritative(key:String)=documents.get(marker(key))!=null
    private fun ack(key:String)=JSONObject().put("ok",true).put("authoritative",true).put("source","room-supply").put("key",key)
    suspend fun initialize(key:String,legacy:String?):JSONObject=database.withTransaction {
        if(!isAuthoritative(key)){replace(key,legacy);documents.upsert(LegacyStorageShadowEntity(marker(key),"{\"version\":1}",System.currentTimeMillis()))};ack(key)
    }
    suspend fun read(key:String):JSONObject=database.withTransaction {check(isAuthoritative(key));val row=documents.get(key);ack(key).put("found",row!=null).put("payload",row?.payload?:JSONObject.NULL)}
    suspend fun write(key:String,raw:String):JSONObject=database.withTransaction {check(isAuthoritative(key));replace(key,raw);ack(key)}
    suspend fun remove(key:String):JSONObject=database.withTransaction {check(isAuthoritative(key));replace(key,null);ack(key)}
    private suspend fun replace(key:String,raw:String?){
        marker(key)
        val parsed=raw?.let{val p=JSONTokener(it);p.nextValue().also{require(p.nextClean()=='\u0000')}}
        require(parsed==null||parsed===JSONObject.NULL||(if(key=="receivingDraft")parsed is JSONObject else parsed is JSONArray))
        if(raw==null)documents.delete(key) else documents.upsert(LegacyStorageShadowEntity(key,raw,System.currentTimeMillis()))
        if(key=="receivings")MPosStockEventRepository(database).project(key,(parsed as? JSONArray?:JSONArray()).toString())
    }
}
