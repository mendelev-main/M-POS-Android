package com.mendelev.mpos.backend

import org.json.JSONArray
import org.json.JSONObject

/** Pure encrypted-inbox payload; tombstones reject stale SSE after a confirmed deletion. */
internal data class InboxSnapshot(val orders: List<JSONObject> = emptyList(),val removed: Set<String> = emptySet()) {
    fun merge(incoming: List<JSONObject>,accepted: Set<String>): InboxSnapshot=copy(orders=(incoming+orders)
        .distinctBy {it.getString("id")}.filter {it.getString("id") !in accepted && it.getString("id") !in removed && it.getString("status")=="new"}
        .sortedByDescending {it.optString("created_at")}).also {require(it.orders.size<=1000) {"Накопилось слишком много заказов сайта"}}
    fun reconcileCurrent(incoming: List<JSONObject>,accepted: Set<String>): InboxSnapshot {
        require(incoming.size<=1000 && incoming.map {it.getString("id")}.distinct().size==incoming.size)
        require(incoming.all {it.getString("status")=="new"})
        val live=incoming.map {it.getString("id")}.toSet()
        val gone=orders.map {it.getString("id")}.toSet()-live
        return copy(orders=incoming.filter {it.getString("id") !in accepted}.sortedByDescending {it.optString("created_at")},
            removed=(removed-live)+gone+accepted).also {require(it.removed.size<=100000)}
    }
    fun remove(id: String): InboxSnapshot {
        require(Regex("[A-Za-z0-9_-]{1,100}").matches(id))
        return copy(orders=orders.filterNot {it.getString("id")==id},removed=removed+id)
    }
    fun encode(): String=JSONObject().put("version",2).put("orders",JSONArray(orders)).put("removed",JSONArray(removed.toList())).toString()
    companion object {
        fun decode(raw: String): InboxSnapshot {
            val j=JSONObject(raw);val version=j.getInt("version");require(version in 1..2)
            val a=j.getJSONArray("orders");require(a.length()<=1000)
            val removed=if(version==2) j.getJSONArray("removed") else JSONArray()
            require(removed.length()<=100000)
            val ids=List(removed.length()) {removed.getString(it).also {id->require(Regex("[A-Za-z0-9_-]{1,100}").matches(id))}}.toSet()
            return InboxSnapshot(List(a.length()) {a.getJSONObject(it)}.filterNot {it.getString("id") in ids},ids)
        }
    }
}
