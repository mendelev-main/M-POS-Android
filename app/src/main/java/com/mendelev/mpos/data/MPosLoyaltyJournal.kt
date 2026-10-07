package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Partial per-receipt loyalty updates; financial/return fields always come from current Room data. */
class MPosLoyaltyJournal(private val database: MPosDatabase) {
    private val archive=MPosOrderStorage(database)
    private val documents=database.legacyStorageShadowDao()
    private fun field(kind:String)=when(kind){"sale"->"loyaltySync";"reversal"->"loyaltyReversal";else->throw IllegalArgumentException("unsupported loyalty kind")}
    private fun marker(id:String,kind:String)="mpos_loyalty_attempt_v1:$kind:$id"
    private fun status(order:JSONObject,key:String)=order.optJSONObject(key)?.optString("status").orEmpty()
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray && b is JSONArray -> a.length()==b.length()&&(0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number && b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    private fun payload(order:JSONObject,kind:String):JSONObject {
        val result=JSONObject().put("orderId",order.get("id")).put("customerId",order.getJSONObject("customer").get("id"))
        if(kind=="sale"){
            val lines=JSONArray();val items=order.optJSONArray("items")?:JSONArray()
            for(i in 0 until items.length()) {val row=items.getJSONObject(i);val line=JSONObject();for((to,from) in listOf("productId" to "productId","quantity" to "qty"))if(row.has(from))line.put(to,row.get(from));lines.put(line)}
            result.put("items",lines).put("redemptions",MPosJsonNumbers.fallback(order.opt("loyaltyRedemptions"),JSONObject()))
                .put("rewardAllocations",MPosJsonNumbers.fallback(order.opt("loyaltyRewardAllocations"),JSONObject()))
        }
        return result
    }
    private suspend fun save(order:JSONObject){
        val revision=archive.revision()
        archive.upsert(JSONObject().put("expectedRevision",revision).put("receipt",order).toString())
    }
    private fun action(order:JSONObject):String? {
        if(!MPosJsonNumbers.truthy(order.optJSONObject("customer")?.opt("id")))return null
        if(MPosJsonNumbers.truthy(order.opt("returnedAt"))){
            if(status(order,"loyaltySync")=="pending")return "sale"
            if(status(order,"loyaltySync")=="sending")return null
            if(status(order,"loyaltySync")=="synced"&&status(order,"loyaltyReversal")!="synced")return "reversal"
        }else if(status(order,"loyaltySync")=="pending")return "sale"
        return null
    }
    suspend fun execute(raw:String):JSONObject=database.withTransaction {
        val c=JSONObject(raw);require(c.getInt("version")==1);check(archive.isAuthoritative())
        val out=JSONObject().put("ok",true).put("authoritative",true).put("source","room-loyalty-journal")
        when(c.getString("operation")){
            "recover"->{
                val active=c.getJSONArray("active");val live=(0 until active.length()).map{active.getString(it)}.toSet();val changes=JSONArray();val actions=JSONArray()
                for(record in database.orderProjectionDao().allOrders()){
                    val order=JSONObject(record.payload);val id=order.getString("id");val changed=JSONArray()
                    if(!MPosJsonNumbers.truthy(order.optJSONObject("customer")?.opt("id")))continue
                    for(kind in listOf("sale","reversal")){
                        val key=field(kind)
                        if(status(order,key)=="sending"&&"$kind:$id" !in live){
                            order.getJSONObject(key).put("status","pending").put("recoveredAt",c.getLong("at"));documents.delete(marker(id,kind));changed.put(key)
                        }
                    }
                    if(changed.length()>0){save(order);changes.put(JSONObject().put("order",order).put("fields",changed))}
                    action(order)?.let{kind->if("$kind:$id" !in live)actions.put(JSONObject().put("id",id).put("kind",kind))}
                }
                out.put("orders",changes).put("actions",actions)
            }
            "claim"->{
                val id=c.getString("id");val record=database.orderProjectionDao().get(id)
                if(record==null)return@withTransaction out.put("send",false)
                val order=JSONObject(record.payload);val requestedKind=c.getString("kind")
                if(requestedKind=="settle"&&!MPosJsonNumbers.truthy(order.opt("returnedAt")))return@withTransaction out.put("send",false)
                val kind=if(requestedKind=="settle")action(order) else requestedKind
                if(kind==null||!MPosJsonNumbers.truthy(order.optJSONObject("customer")?.opt("id")))return@withTransaction out.put("send",false)
                val key=field(kind)
                if(status(order,key)=="sending"||(kind=="reversal"&&status(order,key)=="synced"))return@withTransaction out.put("send",false).put("kind",kind).put("order",order)
                val body=payload(order,kind);val token=UUID.randomUUID().toString();val state=JSONObject(order.optJSONObject(key)?.toString()?:"{}")
                state.put("status","sending").put("attemptedAt",c.getLong("at"));order.put(key,state);save(order)
                documents.upsert(LegacyStorageShadowEntity(marker(id,kind),JSONObject().put("token",token).put("payload",body).toString(),System.currentTimeMillis()))
                out.put("send",true).put("kind",kind).put("token",token).put("payload",body).put("order",order)
            }
            "finish"->{
                val id=c.getString("id");val kind=c.getString("kind");val key=field(kind);val record=database.orderProjectionDao().get(id)
                val attempt=documents.get(marker(id,kind))?.let{JSONObject(it.payload)}
                if(record==null||attempt==null||attempt.optString("token")!=c.getString("token"))return@withTransaction out.put("applied",false)
                val order=JSONObject(record.payload)
                if(status(order,key)!="sending"||!same(payload(order,kind),attempt.getJSONObject("payload")))return@withTransaction out.put("applied",false)
                val state=JSONObject().put("status",if(c.getBoolean("success"))"synced" else "pending").put("at",c.getLong("at"))
                if(c.getBoolean("success")&&kind=="sale")state.put("events",MPosJsonNumbers.fallback(c.optJSONObject("data")?.opt("events"),JSONArray()))
                if(!c.getBoolean("success"))state.put("error",c.getString("error"))
                order.put(key,state);save(order);documents.delete(marker(id,kind))
                out.put("applied",true).put("order",order).put("reverseNext",kind=="sale"&&c.getBoolean("success")&&MPosJsonNumbers.truthy(order.opt("returnedAt"))&&status(order,"loyaltyReversal")=="pending")
            }
            else->throw IllegalArgumentException("unsupported loyalty journal operation")
        }
        out
    }
}
