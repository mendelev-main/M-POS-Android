package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** ACK eligibility/receipt proof and conflict-safe journal patches; printing stays in reviewed handlers. */
class MPosWebJournalCommand(private val database:MPosDatabase){
    private val storage=MPosWebJournalStorage(database)
    private val docs=database.legacyStorageShadowDao()
    private fun same(a:Any?,b:Any?):Boolean=when{
        a is JSONObject&&b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet()&&a.keys().asSequence().all{same(a.opt(it),b.opt(it))}
        a is JSONArray&&b is JSONArray -> a.length()==b.length()&&(0 until a.length()).all{same(a.opt(it),b.opt(it))}
        a is Number&&b is Number -> a.toDouble()==b.toDouble()
        else -> a==b
    }
    private fun proof(key:String,id:String)="mpos_web_ack_v1:$key:$id"
    private suspend fun journal(key:String):JSONObject{val e=storage.read(key);return if(!e.getBoolean("found")||e.getString("payload")=="null")JSONObject() else JSONObject(e.getString("payload"))}
    private suspend fun locallyAccepted(record:JSONObject):Boolean {
        val parked=MPosParkedOrderStorage(database);check(parked.isAuthoritative())
        val e=parked.read();val rows=if(!e.getBoolean("found")||e.getString("payload")=="null")JSONArray() else JSONArray(e.getString("payload"))
        return (0 until rows.length()).any{same(rows.getJSONObject(it).opt("webOrderId"),record.optJSONObject("parked")?.opt("webOrderId"))}
    }
    private fun validEstimate(value:String)=value in setOf("5m","15m","30m","40m","60plus")||Regex("at:([01][0-9]|2[0-3]):[0-5][0-9]").matches(value)
    suspend fun execute(raw:String):JSONObject=database.withTransaction{
        val c=JSONObject(raw);require(c.getInt("version")==1);val key=c.getString("key");require(key in MPosWebJournalStorage.KEYS)
        val current=journal(key);val out=JSONObject().put("ok",true).put("authoritative",true).put("source","room-web-command")
        when(c.getString("operation")){
            "patch"->{
                val expected=c.getJSONObject("expected");val next=c.getJSONObject("next");val keys=expected.keys().asSequence().toSet()+next.keys().asSequence().toSet()
                for(id in keys){
                    if(expected.has(id)==next.has(id)&&same(expected.opt(id),next.opt(id)))continue
                    check(current.has(id)==expected.has(id)&&same(current.opt(id),expected.opt(id))){"WEB journal record changed"}
                    val row=next.optJSONObject(id);val stage=row?.optString("stage")
                    if(key=="webOrderAcceptances"&&stage=="local")check(locallyAccepted(row!!)){"WEB order not locally saved"}
                    if(stage=="confirmed"&&current.optJSONObject(id)?.optString("stage")!="confirmed"){
                        val receipt=docs.get(proof(key,id))?.let{JSONObject(it.payload)}
                        check(receipt?.optBoolean("acknowledged")==true&&same(receipt.opt("record"),current.opt(id))){"WEB ACK not recorded"}
                        docs.delete(proof(key,id))
                    }
                    if(key=="webOrderReadyJournal"&&stage=="pending"&&current.optJSONObject(id)?.optString("stage")!="pending"){
                        val recovery=MPosRecoveryStorage(database);val e=recovery.read("currentOrderSession");val session=JSONObject(e.getString("payload"))
                        check(session.opt("source")=="web"&&session.opt("webOrderId")==id&&session.opt("webOrderStatus")=="ready"){"WEB ready not locally saved"}
                    }
                    if(!next.has(id)&&key=="webOrderReadyJournal")check(current.optJSONObject(id)?.optString("stage")=="confirmed"){"Only durably confirmed WEB ready records may be removed"}
                    if(next.has(id))current.put(id,next.get(id)) else current.remove(id)
                }
                storage.write(key,current.toString());out.put("journal",current)
            }
            "gate"->{
                val id=c.getString("id");val row=current.getJSONObject(id)
                if(row.optString("stage")=="confirmed")return@withTransaction out.put("confirmed",true)
                if(key=="webOrderAcceptances"){
                    check(row.opt("stage")=="local"&&locallyAccepted(row)){"WEB acceptance not locally saved"}
                    check(validEstimate(row.optString("readyEstimate"))&&same(row.opt("readyEstimate"),c.getJSONObject("body").opt("readyEstimate"))){"WEB ready estimate changed"}
                }else check(row.opt("stage")=="pending"){"WEB ready intent is not pending"}
                val token=UUID.randomUUID().toString();docs.upsert(LegacyStorageShadowEntity(proof(key,id),JSONObject().put("token",token).put("record",row).put("acknowledged",false).toString(),System.currentTimeMillis()))
                out.put("confirmed",false).put("token",token)
            }
            "ack"->{
                val id=c.getString("id");val receipt=JSONObject(requireNotNull(docs.get(proof(key,id))).payload)
                check(receipt.getString("token")==c.getString("token")&&same(receipt.opt("record"),current.opt(id))){"WEB ACK belongs to another record"}
                receipt.put("acknowledged",true);docs.upsert(LegacyStorageShadowEntity(proof(key,id),receipt.toString(),System.currentTimeMillis()));out
            }
            "markReady"->{
                require(key=="webOrderReadyJournal");val id=c.getString("id");val recovery=MPosRecoveryStorage(database);val e=recovery.read("currentOrderSession")
                val before=JSONObject(e.getString("payload"));check(same(before,c.getJSONObject("expectedSession"))){"WEB session changed"}
                val session=c.getJSONObject("session");check(session.opt("source")=="web"&&session.opt("webOrderId")==id&&session.opt("webOrderStatus")=="ready")
                check(before.opt("source")=="web"&&before.opt("webOrderId")==id)
                current.put(id,JSONObject().put("stage","pending").put("createdAt",c.getLong("at")).put("localSavedAt",c.getLong("at")))
                recovery.write("currentOrderSession",session.toString());storage.write(key,current.toString());out.put("journal",current)
            }
            else->throw IllegalArgumentException("unsupported WEB operation")
        }
        out
    }
}
