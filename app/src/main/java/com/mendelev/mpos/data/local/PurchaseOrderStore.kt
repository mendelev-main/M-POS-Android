package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.CatalogFailure
import com.mendelev.mpos.domain.warehouse.*
import org.json.JSONArray
import org.json.JSONObject

object PurchaseOrderCodec {
    const val KEY="purchase-orders"
    fun encode(rows: List<PurchaseOrder>): String=JSONObject().put("version",3).put("items",JSONArray(rows.map {o->
        JSONObject().put("id",o.id).put("source",JSONObject(PurchaseDraftCodec.encode(o.source)))
            .put("createdAt",o.createdAt).put("requestVersion",o.requestVersion).put("status",o.status)
            .put("expected",JSONArray(o.expectedQuantities.map {it ?: JSONObject.NULL}))
            .put("revision",o.revision).put("updatedAt",o.updatedAt ?: JSONObject.NULL).put("cancelledAt",o.cancelledAt ?: JSONObject.NULL).put("cancelledBy",o.cancelledBy ?: JSONObject.NULL)
            .put("receivingId",o.receivingId ?: JSONObject.NULL).put("receivedAt",o.receivedAt ?: JSONObject.NULL).put("receivedBy",o.receivedBy ?: JSONObject.NULL)
            .put("history",JSONArray(o.history.map {h->JSONObject().put("source",JSONObject(PurchaseDraftCodec.encode(h.source))).put("expected",JSONArray(h.expectedQuantities.map {it ?: JSONObject.NULL})).put("requestVersion",h.requestVersion).put("at",h.at)}))
    })).toString()
    fun decode(raw: String): List<PurchaseOrder> {
        try {
            val j=JSONObject(raw);require(j.get("version") is Number && j.get("version").toString() in setOf("1","2","3"));val version=j.get("version").toString()
            val a=j.getJSONArray("items")
            fun long(j: JSONObject,key: String): Long {require(j.get(key) is Number);return j.get(key).toString().toLong()}
            return (0 until a.length()).map {i->
                val o=a.getJSONObject(i);val expected=o.getJSONArray("expected")
                val order=PurchaseOrder(o.getString("id"),PurchaseDraftCodec.decode(o.getJSONObject("source").toString())!!,long(o,"createdAt"),
                    (0 until expected.length()).map {if(expected.isNull(it)) null else expected.getString(it)},long(o,"requestVersion"),o.getString("status"))
                if(version=="1") {
                    require(order.status=="pending" && order.requestVersion==1L)
                    order.also(PurchaseOrderRules::validate)
                } else {
                    listOf("updatedAt","cancelledAt","cancelledBy","history").forEach {require(o.has(it))}
                    if(version=="3") listOf("receivingId","receivedAt","receivedBy").forEach {require(o.has(it))}
                    val history=o.getJSONArray("history")
                    order.copy(receivingId=if(version=="3" && !o.isNull("receivingId")) o.getString("receivingId") else null,receivedAt=if(version=="3" && !o.isNull("receivedAt")) long(o,"receivedAt") else null,receivedBy=if(version=="3" && !o.isNull("receivedBy")) o.getString("receivedBy") else null,revision=long(o,"revision"),updatedAt=if(o.isNull("updatedAt")) null else long(o,"updatedAt"),cancelledAt=if(o.isNull("cancelledAt")) null else long(o,"cancelledAt"),cancelledBy=if(o.isNull("cancelledBy")) null else o.getString("cancelledBy"),
                        history=(0 until history.length()).map {k->val h=history.getJSONObject(k);val e=h.getJSONArray("expected")
                            PurchaseRequestRevision(PurchaseDraftCodec.decode(h.getJSONObject("source").toString())!!,(0 until e.length()).map {if(e.isNull(it)) null else e.getString(it)},long(h,"requestVersion"),long(h,"at"))
                        }).also(PurchaseOrderRules::validate)
                }
            }.also {require(it.map {o->o.id}.distinct().size==it.size && it.map {o->o.source.id}.distinct().size==it.size)}
        } catch(_:Exception) {throw CatalogFailure("purchaseOrdersFormat","Не удалось прочитать историю закупок. Данные не сброшены")}
    }
}
class PurchaseOrderStore(private val db: CatalogDatabase) {
    suspend fun all(): List<PurchaseOrder> = db.workspace().document(PurchaseOrderCodec.KEY)?.let {PurchaseOrderCodec.decode(it.payload)}.orEmpty()
    suspend fun append(order: PurchaseOrder) {
        PurchaseOrderRules.validate(order)
        val doc=db.workspace().document(PurchaseOrderCodec.KEY) ?: WorkspaceDocument(PurchaseOrderCodec.KEY,0,PurchaseOrderCodec.encode(emptyList()))
        val rows=PurchaseOrderCodec.decode(doc.payload)
        if(rows.any {it.id==order.id || it.source.id==order.source.id}) throw CatalogFailure("conflict","По этому черновику уже сформирован заказ")
        db.workspace().put(doc.copy(revision=Math.addExact(doc.revision,1),payload=PurchaseOrderCodec.encode(rows+order)))
    }
    suspend fun requireSupplierUnused(id: String) {
        if(all().any {it.source.supplierId==id}) throw CatalogFailure("used","Поставщик используется в истории закупок")
    }
    suspend fun requireProductUnused(id: String) {
        if(all().any {o->(o.source.lines+o.history.flatMap {it.source.lines}).any {it.productId==id}}) throw CatalogFailure("used","Товар используется в истории закупок")
    }
}
