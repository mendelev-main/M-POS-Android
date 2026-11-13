package com.mendelev.mpos.backend

import org.json.JSONObject

internal data class OrderHistoryPage(val orders: List<JSONObject>,val nextCursor: String?)
/** Read-only device routes. A failed/partial read is never permission to clear the cached inbox. */
internal class OrderDirectory(private val http: BackendHttp=BackendHttp()) {
    fun current(settings: BackendSettings): List<JSONObject> {
        val response=read(settings,settings.ordersPath+"/current")
        check(response.opt("complete")==true) {"Backend не подтвердил полный список текущих заказов"}
        return rows(response,1000).also {list->check(list.all {it.getString("status")=="new" && it.getJSONArray("order_items").length()>0}) {"Backend вернул некорректный список новых заказов"}}
    }
    fun history(settings: BackendSettings,cursor: String?): OrderHistoryPage {
        require(cursor==null || cursor.matches(Regex("[A-Za-z0-9_-]{1,512}")))
        val response=read(settings,settings.ordersPath+"/history?limit=5"+(cursor?.let {"&cursor=$it"} ?: ""))
        val orders=rows(response,5)
        check(response.has("nextCursor")) {"Backend не вернул границу страницы истории"}
        check(response.isNull("nextCursor") || response.get("nextCursor") is String)
        val next=response.optString("nextCursor").takeUnless {response.isNull("nextCursor")}
        check(next==null || next.matches(Regex("[A-Za-z0-9_-]{1,512}")) && next!=cursor && orders.isNotEmpty()) {"Backend вернул некорректную страницу истории"}
        return OrderHistoryPage(orders,next)
    }
    private fun read(s: BackendSettings,path: String): JSONObject=try {http.json(s,path)} catch(e:BackendFailure) {
        if(e.status==404) throw BackendFailure("Для обновления событий и общей истории нужно обновить backend",404)
        if(e.status==409 && e.code=="CURRENT_QUEUE_TOO_LARGE") throw BackendFailure("Очередь слишком велика для полной загрузки. Рабочие данные сохранены",409)
        throw e
    }
    private fun decimal(raw: String,positive: Boolean=false) {
        check(raw.length<=40)
        check(raw.toBigDecimal().signum()>=if(positive) 1 else 0)
    }
    private fun rows(response: JSONObject,limit: Int): List<JSONObject> {
        val array=response.getJSONArray("orders");check(array.length()<=limit)
        return List(array.length()) {i->array.getJSONObject(i).also {row->
            check(row.getString("id").matches(Regex("[A-Za-z0-9_-]{1,100}")))
            check(row.getString("status").matches(Regex("[a-z_]{1,40}")))
            decimal(row.get("total").toString())
            check(row.getString("created_at").length in 1..64)
            val items=row.getJSONArray("order_items");check(items.length()<=500)
            repeat(items.length()) {index->val item=items.getJSONObject(index)
                decimal(item.get("quantity").toString(),true)
                decimal(item.get("price").toString())
            }
        }}.also {check(it.map {row->row.getString("id")}.distinct().size==it.size)}
    }
}
