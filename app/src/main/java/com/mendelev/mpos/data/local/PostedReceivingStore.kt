package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONArray
import org.json.JSONObject

object PostedReceivingCodec {
    const val KEY="receivings"
    fun encode(rows: List<PostedReceiving>): String=JSONObject().put("version",2).put("items",JSONArray(rows.map {r->
        JSONObject().put("id",r.id).put("commandId",r.commandId).put("actorId",r.actorId).put("at",r.at)
            .put("costPolicy",r.costPolicy).put("overageConfirmed",r.overageConfirmed).put("totalMinor",r.result.totalMinor)
            .put("changes",JSONArray(r.result.lines.map {l->JSONObject().put("productId",l.productId).put("name",l.name).put("actual",l.actual)
                .put("expected",l.expected ?: JSONObject.NULL).put("shortage",l.shortage ?: JSONObject.NULL).put("overage",l.overage ?: JSONObject.NULL)
                .put("oldStock",l.oldStock).put("newStock",l.newStock).put("oldCost",l.oldCost).put("newCost",l.newCost).put("noStockTracking",l.noStockTracking).put("totalMinor",l.totalMinor)}))
            .put("invoice",JSONObject(ReceivingCodec.encode(listOf(ReceivingRecord(r.draft.key,r.draftRevision,r.draft)))))
    })).toString()
    fun decode(raw: String): List<PostedReceiving> {
        try {
            val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString() in setOf("1","2"))
            val version=j.get("version").toString();val a=j.getJSONArray("items")
            return (0 until a.length()).map {i->val r=a.getJSONObject(i)
                require(r.get("at") is Number && r.get("overageConfirmed") is Boolean)
                fun long(o: JSONObject,k: String): Long {require(o.get(k) is Number);return o.get(k).toString().toLong()}
                fun optional(o: JSONObject,k: String): String? {require(o.has(k));return if(o.isNull(k)) null else o.getString(k)}
                val changes=r.getJSONArray("changes")
                val result=ReceivingPreview((0 until changes.length()).map {n->val l=changes.getJSONObject(n);require(l.get("noStockTracking") is Boolean)
                    ReceivingPreviewLine(l.getString("productId"),l.getString("name"),l.getString("actual"),optional(l,"expected"),optional(l,"shortage"),optional(l,"overage"),l.getString("oldStock"),l.getString("newStock"),l.getString("oldCost"),l.getString("newCost"),l.getBoolean("noStockTracking"),long(l,"totalMinor"))
                },long(r,"totalMinor"))
                val record=ReceivingCodec.decode(r.getJSONObject("invoice").toString()).single()
                PostedReceiving(r.getString("id"),r.getString("commandId"),r.getString("actorId"),r.get("at").toString().toLong(),record.revision,record.draft!!,r.getBoolean("overageConfirmed"),if(version=="1") 1 else long(r,"costPolicy").also {require(it in 1..2)}.toInt(),result).also {
                    ReceivingPostingRules.validate(it)
                }
            }.also {rows->
                require(rows.map {it.id}.distinct().size==rows.size && rows.map {it.commandId}.distinct().size==rows.size && rows.map {it.draft.id}.distinct().size==rows.size && rows.mapNotNull {it.draft.order?.id}.distinct().size==rows.count {it.draft.order!=null})
            }
        } catch(_:Exception) {throw CatalogFailure("receivingsFormat","Не удалось прочитать историю приходов. Данные не сброшены")}
    }
}
class PostedReceivingStore(private val db: CatalogDatabase) {
    suspend fun all(): List<PostedReceiving> = db.workspace().document(PostedReceivingCodec.KEY)?.let {PostedReceivingCodec.decode(it.payload)}.orEmpty()
}
interface ReceivingPostingAccess {
    suspend fun history(): List<PostedReceiving>
    suspend fun outcome(c: ReceivingPostCommand): String?
    suspend fun commit(c: ReceivingPostCommand): String
}
