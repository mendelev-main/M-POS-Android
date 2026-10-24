package com.mendelev.mpos.data.local

import com.mendelev.mpos.domain.catalog.*
import com.mendelev.mpos.domain.workspace.*
import org.json.JSONArray
import org.json.JSONObject

/** Storage-only codecs. An unknown payload never becomes an empty customer order. */
object WorkspaceCodec {
    private fun root(raw: String)=JSONObject(raw).also {
        if(it.getInt("version")!=1) throw CatalogFailure("workspace","Неизвестный формат рабочей зоны")
    }
    private fun <T> JSONArray.list(block: (JSONObject)->T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
    private fun objects(values: List<JSONObject>)=JSONArray().also { a -> values.forEach(a::put) }
    fun layout(items: List<LayoutItem>): String=JSONObject().put("version",1).put("items",objects(items.map {
        JSONObject().put("type",it.type).put("id",it.id).put("categoryId",it.categoryId).put("folderId",it.folderId).put("name",it.name)
    })).toString()
    fun layout(raw: String): List<LayoutItem> = root(raw).getJSONArray("items").list {
        LayoutItem(it.getString("type"),it.getString("id"),it.getString("categoryId"),it.getString("folderId"),it.getString("name"))
    }
    fun cart(cart: CurrentCart): String=JSONObject().put("version",1).put("lines",objects(cart.lines.map { line ->
        JSONObject().put("id",line.id).put("productId",line.productId).put("name",line.name).put("basePrice",line.basePrice)
            .put("manual",line.manual).put("unitPrice",line.unitPrice).put("quantity",line.quantity).put("unit",line.unit)
            .put("options",objects(line.options.map { JSONObject().put("id",it.id).put("name",it.name).put("quantity",it.quantity).put("priceDelta",it.priceDelta) }))
            .put("requests",objects(line.requests.map { JSONObject().put("id",it.productId).put("quantity",CatalogRules.canonical(it.quantity)).put("unit",it.unit.name) }))
            .put("references",objects(line.references.toSortedMap().map { (id,rev) -> JSONObject().put("id",id).put("revision",rev) }))
    })).put("graph",objects(cart.graph.map { p ->
        val j=JSONObject().put("id",p.id).put("unit",p.unit.name)
        when(p) {
            is RecipeProduct.Simple -> j.put("type","simple").put("cost",CatalogRules.canonical(p.cost)).put("noStock",p.noStockTracking).put("negative",p.allowNegativeStock)
            is RecipeProduct.Composite -> j.put("type","composite").put("yield",CatalogRules.canonical(p.yield)).put("components",objects(p.components.map {
                JSONObject().put("id",it.productId).put("quantity",CatalogRules.canonical(it.quantity)).put("unit",it.unit.name)
            }))
        }
        j
    })).toString()
    fun cart(raw: String): CurrentCart {
        val j=root(raw)
        val graph=j.getJSONArray("graph").list { p ->
            val id=p.getString("id");val unit=QuantityUnit.valueOf(p.getString("unit"))
            when(p.getString("type")) {
                "simple" -> RecipeProduct.Simple(id,unit,p.getString("cost").toBigDecimal(),p.getBoolean("noStock"),p.getBoolean("negative"))
                "composite" -> RecipeProduct.Composite(id,unit,p.getString("yield").toBigDecimal(),p.getJSONArray("components").list {
                    RecipeComponent(it.getString("id"),it.getString("quantity").toBigDecimal(),QuantityUnit.valueOf(it.getString("unit")))
                })
                else -> throw CatalogFailure("workspace","Некорректный тип товара в заказе")
            }
        }
        return CurrentCart(j.getJSONArray("lines").list { l ->
            CartLine(l.getString("id"),l.getString("productId"),l.getString("name"),l.getString("basePrice"),l.getBoolean("manual"),
                l.getString("unitPrice"),l.getString("quantity"),l.getString("unit"),l.getJSONArray("options").list {
                    ChosenOption(it.getString("id"),it.getString("name"),it.getString("quantity"),it.getString("priceDelta"))
                },l.getJSONArray("requests").list {
                    RecipeRequest(it.getString("id"),it.getString("quantity").toBigDecimal(),QuantityUnit.valueOf(it.getString("unit")))
                },l.getJSONArray("references").list { it.getString("id") to it.getLong("revision") }.toMap())
        },graph)
    }
}
