package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Durable uncertainty gate scoped to backend/customer/program; never replays or blocks cash operations. */
class MPosLoyaltyAdjustmentVerification(private val database:MPosDatabase) {
    private val dao=database.legacyStorageShadowDao()
    private val key="mpos_loyalty_adjustment_verification_v1"
    private suspend fun records()=dao.get(key)?.let{JSONObject(it.payload)} ?: JSONObject()
    private fun scope(config:JSONObject,customerId:String,programId:Any):String {
        val target=config.getString("backendUrl").trim().trimEnd('/')+"\n"+config.getString("deviceKey")
        val fingerprint=MessageDigest.getInstance("SHA-256").digest(target.toByteArray()).joinToString(""){"%02x".format(it)}
        return org.json.JSONArray().put(fingerprint).put(customerId).put(programId).toString()
    }
    suspend fun ticket(config:JSONObject,customerId:String,programId:Any):String?=database.withTransaction {
        records().optString(scope(config,customerId,programId)).takeIf{it.isNotEmpty()}
    }
    suspend fun begin(config:JSONObject,customerId:String,programId:Any):String=database.withTransaction {
        val records=records();val scope=scope(config,customerId,programId)
        check(!records.has(scope)){"Перед повторной корректировкой проверьте актуальный баланс клиента"}
        val token=UUID.randomUUID().toString();records.put(scope,token)
        dao.upsert(LegacyStorageShadowEntity(key,records.toString(),System.currentTimeMillis()));token
    }
    suspend fun finish(config:JSONObject,customerId:String,programId:Any,token:String)=database.withTransaction {
        val records=records();val scope=scope(config,customerId,programId)
        if(records.optString(scope)!=token)return@withTransaction
        records.remove(scope)
        if(records.length()==0)dao.delete(key) else dao.upsert(LegacyStorageShadowEntity(key,records.toString(),System.currentTimeMillis()))
    }
}
