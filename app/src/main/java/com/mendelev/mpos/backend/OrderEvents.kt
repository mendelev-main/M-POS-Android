package com.mendelev.mpos.backend

import org.json.JSONObject
import java.io.Reader

data class ServerEvent(val type: String,val data: String,val id: String?,val retryMillis: Long?)
/** Bounded SSE parser. Event IDs are transport metadata, never proof of order acceptance. */
object OrderEvents {
    fun read(reader: Reader,receive: (ServerEvent)->Unit) {
        val source=reader.buffered()
        var type="message";var id: String?=null;var retry: Long?=null
        val data=StringBuilder();var size=0
        fun dispatch() {
            if(data.isNotEmpty() || type=="sleeping") receive(ServerEvent(type,data.toString().removeSuffix("\n"),id,retry))
            type="message";data.setLength(0);size=0
        }
        val line=StringBuilder()
        fun consume() {
            val value=line.toString();line.setLength(0)
            if(value.isEmpty()) {dispatch();return}
            if(value.startsWith(':')) return
            val field=value.substringBefore(':');val text=value.substringAfter(':',"").removePrefix(" ")
            when(field) {
                "event"->type=text
                "data"->{size+=text.length+1;require(size<=1_048_576) {"Событие backend слишком большое"};data.append(text).append('\n')}
                "id"->if('\u0000' !in text) id=text
                "retry"->text.takeIf {it.isNotEmpty() && it.all {c->c in '0'..'9'}}?.toLongOrNull()?.let {retry=it.coerceIn(1000,86_400_000)}
            }
        }
        var beginning=true;var cr=false
        while(true) {
            val n=source.read();if(n<0) break
            val c=n.toChar()
            if(beginning) {beginning=false;if(c=='\uFEFF') continue}
            if(c=='\n' && cr) {cr=false;continue}
            cr=c=='\r'
            if(c=='\n' || c=='\r') consume() else {require(line.length<65_536) {"Строка события backend слишком большая"};line.append(c)}
        }
        // An unterminated event may have been truncated by a network failure: reconnect, do not persist it.
    }
    fun orders(event: ServerEvent): List<JSONObject>? {
        if(event.type!="message") return null
        val root=BackendJson.obj(event.data)
        if(root.optString("type")!="orders") return null
        val a=root.getJSONArray("orders");require(a.length()<=1000)
        return List(a.length()) {a.getJSONObject(it)}.also {rows->
            require(rows.map {it.getString("id")}.distinct().size==rows.size)
            rows.forEach {r->
                require(Regex("[A-Za-z0-9_-]{1,100}").matches(r.getString("id")))
                require(r.getString("status") in setOf("new","accepted","preparing","ready","cancelled"))
                val items=r.getJSONArray("order_items");require(items.length() in 1..500)
                fun decimal(value: String,positive: Boolean=false) {
                    require(value.length<=40)
                    val n=value.toBigDecimal();require(n.signum()>=if(positive) 1 else 0)
                }
                decimal(r.get("total").toString());decimal(r.opt("delivery_fee")?.toString() ?: "0")
                repeat(items.length()) {i->val item=items.getJSONObject(i);decimal(item.get("quantity").toString(),true);decimal(item.get("price").toString())}
            }
        }
    }
}
