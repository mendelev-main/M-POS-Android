package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.backup.*
import com.mendelev.mpos.domain.catalog.CatalogRules
import com.mendelev.mpos.domain.sale.*
import org.json.JSONArray
import org.json.JSONObject

/** Immutable initial inputs for unpaid/resumed source carts, not payment authority. */
internal object WebOrderDefaults {
    const val KEY="web-order-defaults"
    data class Inputs(val discounts: Map<String,SaleDiscount>,val delivery: DeliveryCharge?)
    fun inputs(op: WebOpenOperation): Inputs=Inputs(op.lines.mapNotNull {line->line.discount?.let {
        CatalogRules.fingerprint("web.cart.line.v1",op.origin,line.position) to SaleDiscount(it.type,it.value,it.name ?: "Скидка из Web")
    }}.toMap(),if(op.orderType=="Доставка")DeliveryCharge(CatalogRules.fingerprint("web.delivery.v1",op.origin),"Доставка из Web",op.delivery ?: error("missing source delivery"),1) else null)
    fun encode(source: WebBackupDocument): String=JSONObject().put("version",1).put("archivedReceipts",source.preview.sections["orders"] ?: 0).put("items",JSONArray(WebOpenOperations.map(source).map {op->
        val values=inputs(op)
        JSONObject().put("origin",op.origin).put("discounts",JSONArray(values.discounts.map {(id,d)->JSONObject().put("id",id).put("type",d.type).put("value",d.value).put("name",d.name)}))
            .put("delivery",values.delivery?.let {JSONObject().put("id",it.id).put("name",it.name).put("fee",it.fee).put("revision",it.revision)} ?: JSONObject.NULL)
    })).toString()
    fun historyCount(raw: String)=JSONObject(raw).getInt("archivedReceipts").also {require(it>=0)}
    fun read(raw: String,origin: String): Inputs? {
        val j=JSONObject(raw);require(j.getInt("version")==1)
        val a=j.getJSONArray("items")
        val found=(0 until a.length()).map {a.getJSONObject(it)}.filter {it.getString("origin")==origin}
        require(found.size<=1);val row=found.singleOrNull() ?: return null
        val ds=row.getJSONArray("discounts")
        val discounts=(0 until ds.length()).map {ds.getJSONObject(it)}.associate {it.getString("id") to SaleDiscount(it.getString("type"),it.getString("value"),it.getString("name"))}
        require(discounts.size==ds.length())
        return Inputs(discounts,if(row.isNull("delivery"))null else row.getJSONObject("delivery").let {DeliveryCharge(it.getString("id"),it.getString("name"),it.getLong("fee"),it.getLong("revision"))})
    }
}
