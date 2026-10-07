package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Room-backed sales aggregates. Refund exclusion deliberately follows the reviewed policy. */
object MPosAnalyticsEngine {
    private fun number(value:Any?):Double=MPosPurchaseEngine.number(value).let{if(it.isNaN()||it==0.0)0.0 else it}
    private fun amount(row:JSONObject,key:String)=MPosPurchaseEngine.number(MPosJsonNumbers.fallback(row.opt(key),0))
    private fun JSONObject.numeric(key:String,value:Double)=put(key,if(value.isFinite())value else JSONObject.NULL)
    private fun objectKeys(keys:Set<String>):List<String> {
        fun index(key:String):Long?=key.toLongOrNull()?.takeIf{it in 0..4294967294L&&it.toString()==key}
        return keys.filter{index(it)!=null}.sortedBy{index(it)}+keys.filter{index(it)==null}
    }
    private fun descending(a:Double,b:Double):Int {val difference=b-a;return if(difference.isNaN()||difference==0.0)0 else if(difference>0)1 else -1}
    fun range(input:JSONObject):Pair<Long?,Long?> {
        val zone=ZoneId.of(input.getString("zone"));val today=Instant.ofEpochMilli(input.getLong("now")).atZone(zone).toLocalDate()
        fun parse(key:String):LocalDate? {
            val value=input.opt(key);if(!MPosJsonNumbers.truthy(value))return today
            val text=MPosAvailabilityEngine.text(value);if(!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text))return null
            val fields=text.split('-').map{it.toInt()};if(fields[1] !in 1..12||fields[2] !in 1..31)return null
            // Date('YYYY-MM-DDT...') normalizes days 29–31 in shorter months.
            return LocalDate.of(fields[0],fields[1],1).plusDays((fields[2]-1).toLong())
        }
        return parse("from")?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() to parse("to")?.atTime(LocalTime.of(23,59,59,999000000))?.atZone(zone)?.toInstant()?.toEpochMilli()
    }
    fun calculate(input:JSONObject,orders:JSONArray,products:JSONArray,shifts:JSONArray):JSONObject {
        require(input.getInt("version")==1);val (from,to)=range(input)
        val chosen=mutableListOf<JSONObject>();for(i in 0 until orders.length()){val row=orders.getJSONObject(i);val at=amount(row,"timestamp");if(from!=null&&to!=null&&at>=from&&at<=to&&!MPosJsonNumbers.truthy(row.opt("returnedAt")))chosen.add(row)}
        var revenue=0.0;var cash=0.0;var card=0.0;var cost=0.0
        val employees=linkedMapOf<String,Double>();val categories=linkedMapOf<String,Pair<Double,Double>>();val items=linkedMapOf<String,Pair<Double,Double>>()
        fun add(map:MutableMap<String,Pair<Double,Double>>,name:String,line:Double,qty:Double){val old=map[name]?: (0.0 to 0.0);map[name]=(old.first+line) to (old.second+qty)}
        for(order in chosen){
            val total=amount(order,"total");revenue+=total
            val payments=order.optJSONArray("payments")
            if(payments!=null){var cashPart=0.0;var cardPart=0.0;for(i in 0 until payments.length()){val payment=payments.getJSONObject(i);when(payment.opt("method")){"cash"->cashPart+=amount(payment,"amount");"card"->cardPart+=amount(payment,"amount")}};cash+=cashPart;card+=cardPart}
            else{if(order.opt("method")=="cash")cash+=total;if(order.opt("method")=="card")card+=total}
            val shift=MPosReceivingEngine.find(shifts,order.opt("shiftId"));val employee=MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(shift?.opt("employeeName"),"Без сотрудника"));employees[employee]=number(employees[employee])+total
            val lines=order.optJSONArray("items")?:JSONArray()
            for(i in 0 until lines.length()){
                val line=lines.getJSONObject(i);val qty=number(line.opt("qty"));val price=number(line.opt("price"));cost+=number(line.opt("cost"))*qty
                val p=MPosReceivingEngine.find(products,line.opt("productId"));val category=MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(p?.opt("category"),MPosJsonNumbers.fallback(line.opt("category"),"Без категории")))
                val name=MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(line.opt("name"),MPosJsonNumbers.fallback(line.opt("productName"),MPosJsonNumbers.fallback(p?.opt("name"),"Товар"))))
                add(categories,category,price*qty,qty);add(items,name,price*qty,qty)
            }
        }
        var inventoryValue=0.0
        for(i in 0 until products.length()){val p=products.getJSONObject(i);if(p.opt("type")=="simple"&&!MPosJsonNumbers.truthy(p.opt("noStockTracking")))inventoryValue+=kotlin.math.max(0.0,number(p.opt("stock")))*kotlin.math.max(0.0,number(p.opt("cost")))}
        fun groups(map:Map<String,Pair<Double,Double>>):JSONArray=JSONArray(objectKeys(map.keys).map{key->val row=requireNotNull(map[key]);JSONObject().put("name",key).numeric("revenue",row.first).numeric("qty",row.second)}.sortedWith{a,b->descending(a.optDouble("qty"),b.optDouble("qty"))})
        return JSONObject().put("range",JSONObject().put("from",from?:JSONObject.NULL).put("to",to?:JSONObject.NULL)).put("orderCount",chosen.size)
            .numeric("revenue",revenue).numeric("cash",cash).numeric("card",card).numeric("cost",cost).numeric("profit",revenue-cost).numeric("avg",if(chosen.isEmpty())0.0 else revenue/chosen.size)
            .put("employees",JSONArray(objectKeys(employees.keys).map{JSONObject().put("name",it).numeric("value",requireNotNull(employees[it]))}.sortedWith{a,b->descending(a.optDouble("value"),b.optDouble("value"))}))
            .put("categories",groups(categories)).put("products",groups(items)).numeric("inventoryValue",inventoryValue)
    }
}
