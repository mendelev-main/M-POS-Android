package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import android.icu.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Read-only parity model; historical balances are estimates, never stock mutations. */
object MPosWarehouseEngine {
    private fun number(row:JSONObject,key:String)=if(row.has(key))MPosPurchaseEngine.number(row.opt(key)) else Double.NaN
    private fun JSONObject.numeric(key:String,value:Double?):JSONObject=put(key,if(value!=null&&value.isFinite())value else JSONObject.NULL)
    private class Row(val id:Any?,val name:Any,val unit:Any,val cost:Double?,val current:Double?,val min:Any?,val hasMin:Boolean) {
        val suppliers=mutableListOf<Any>();var incoming=0.0;var incomingValue=0.0;var outgoing=0.0;var returned=0.0;var afterStart=0.0;var afterEnd=0.0
        fun json():JSONObject {
            val end=current?.minus(afterEnd)
            return JSONObject().apply{if(id!=null)put("id",id)}.put("name",name).put("unit",unit).numeric("cost",cost).numeric("current",current)
                .apply{if(hasMin)put("minStock",min?:JSONObject.NULL)}.put("supplierNames",JSONArray(suppliers))
                .numeric("incoming",incoming).numeric("incomingValue",incomingValue).numeric("outgoing",outgoing).numeric("returned",returned).numeric("afterStart",afterStart).numeric("afterEnd",afterEnd)
                .numeric("start",current?.minus(afterStart)).numeric("end",end).numeric("outgoingValue",cost?.let{outgoing*it}).numeric("returnedValue",cost?.let{returned*it})
                .numeric("currentValue",if(current!=null&&cost!=null)current*cost else null).numeric("endValue",if(end!=null&&cost!=null)end*cost else null)
        }
    }
    fun range(from:String,to:String,zone:ZoneId,validationNow:Long):Pair<Long,Long> {
        fun parse(value:String):LocalDate {
            check(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)){"Укажите даты периода"}
            return try{LocalDate.parse(value).also{check(it.year>=100){"Некорректная дата"}}}catch(_:java.time.DateTimeException){throw IllegalStateException("Некорректная дата")}
        }
        val start=parse(from);val last=parse(to);check(start<=last){"Начало периода позже окончания"}
        check(last<=Instant.ofEpochMilli(validationNow).atZone(zone).toLocalDate()){"Дата окончания не может быть в будущем"}
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    fun calculate(input:JSONObject,products:JSONArray,receivings:JSONArray,orders:JSONArray):JSONObject {
        require(input.getInt("version")==1)
        val from=input.getString("from");val to=input.getString("to");val now=input.getLong("now");val zone=ZoneId.of(input.getString("zone"));val (start,end)=range(from,to,zone,input.getLong("validationNow"))
        val map=linkedMapOf<Any?,Row>();val documents=mutableListOf<JSONObject>();val warnings=JSONObject().put("legacySales",0).put("legacyReturns",0).put("invalid",0).put("unknownCosts",0)
        fun warn(key:String){warnings.put(key,warnings.getInt(key)+1)}
        fun key(id:Any?):Any?=if(id is Number)id.toDouble() else id
        fun row(id:Any?,name:Any?,unit:Any?):Row=map.getOrPut(key(id)){
            val p=MPosReceivingEngine.find(products,id);val simple=p?.opt("type")=="simple";val cost=if(simple)number(requireNotNull(p),"cost").takeIf{it.isFinite()} else null
            Row(id,MPosJsonNumbers.fallback(p?.opt("name"),MPosJsonNumbers.fallback(name,"Удалённый товар · ${MPosAvailabilityEngine.text(id)}")),if(simple)MPosPurchaseEngine.stockUnit(requireNotNull(p)) else MPosJsonNumbers.fallback(unit,""),cost,
                if(simple&&!MPosJsonNumbers.truthy(p?.opt("noStockTracking")))number(requireNotNull(p),"stock").takeIf{it.isFinite()} else null,
                if(simple)p?.opt("minStock") else JSONObject.NULL,!simple||p?.has("minStock")==true)
        }
        for(i in 0 until products.length()){val p=products.getJSONObject(i);if(p.opt("type")=="simple")row(p.opt("id"),p.opt("name"),MPosPurchaseEngine.stockUnit(p))}
        fun event(id:Any?,name:Any?,unit:Any?,qty:Double,time:Double,kind:String,value:Double=0.0):Row? {
            if(!MPosJsonNumbers.truthy(id)||!qty.isFinite()||qty<0||!time.isFinite()||time>now||!value.isFinite()){warn("invalid");return null}
            val r=row(id,name,unit);val amount=try{if(unit!=null&&!MPosSupplyParity.same(unit,r.unit))MPosPurchaseEngine.convert(qty,unit,r.unit) else qty}catch(_:IllegalStateException){warn("invalid");return null}
            val delta=if(kind=="outgoing")-amount else amount
            if(time>=start)r.afterStart+=delta;if(time>=end)r.afterEnd+=delta
            if(time>=start&&time<end){when(kind){"incoming"->{r.incoming+=amount;r.incomingValue+=value};"outgoing"->r.outgoing+=amount;"returned"->r.returned+=amount};return r};return null
        }
        fun itemCost(item:JSONObject):Double=if(item.has("totalCost")&&!item.isNull("totalCost"))number(item,"totalCost") else number(item,"qty")*number(item,"unitCost")
        for(i in 0 until receivings.length()){
            val receipt=receivings.getJSONObject(i);if(MPosJsonNumbers.truthy(receipt.opt("adminDeleted")))continue
            val time=number(receipt,"timestamp");val items=receipt.optJSONArray("items")?:JSONArray().put(receipt)
            if(time>=start&&time<end&&time<=now){var sum=0.0;for(j in 0 until items.length())sum+=itemCost(items.getJSONObject(j));val total=if(receipt.has("totalCost")&&!receipt.isNull("totalCost"))number(receipt,"totalCost") else sum
                documents.add(JSONObject().apply{if(receipt.has("id"))put("id",receipt.get("id"))}.put("supplier",MPosJsonNumbers.fallback(receipt.opt("supplierName"),"Не указан")).put("number",MPosJsonNumbers.fallback(receipt.opt("invoiceNumber"),"—")).put("date",MPosJsonNumbers.fallback(receipt.opt("invoiceDate"),"—")).numeric("timestamp",time).numeric("total",total).put("shortage",MPosJsonNumbers.truthy(receipt.opt("shortage"))))
            }
            for(j in 0 until items.length()){val item=items.getJSONObject(j);val received=event(item.opt("productId"),item.opt("productName"),item.opt("stockUnit"),number(item,"qty"),time,"incoming",itemCost(item));val supplier=MPosJsonNumbers.fallback(receipt.opt("supplierName"),"Не указан")
                if(received!=null&&number(item,"qty")>0&&supplier !in received.suppliers)received.suppliers.add(supplier)
            }
        }
        var recordedSalesCost=0.0;var missingSalesCosts=0
        for(i in 0 until orders.length()){
            val order=orders.getJSONObject(i);val saleTime=number(order,"timestamp");val returnTime=MPosPurchaseEngine.number(MPosJsonNumbers.fallback(order.opt("returnedAt"),0))
            if(saleTime>=start&&saleTime<end&&saleTime<=now){val items=order.optJSONArray("items")?:JSONArray();for(j in 0 until items.length()){val item=items.getJSONObject(j);val cost=number(item,"cost");if(!item.has("cost")||item.isNull("cost")||!cost.isFinite())missingSalesCosts++ else recordedSalesCost+=cost*MPosPurchaseEngine.number(MPosJsonNumbers.fallback(item.opt("qty"),0))}}
            val snap=order.optJSONObject("stockConsumption");val lines=snap?.optJSONArray("items");val version=snap?.opt("version")
            if(version !is Number||version.toDouble()!=1.0||lines==null){if(saleTime>=start&&saleTime<=now)warn("legacySales");if(returnTime>=start&&returnTime<=now)warn("legacyReturns");continue}
            val ids=mutableSetOf<Any?>();var invalid=false
            for(j in 0 until lines.length()){val item=lines.optJSONObject(j);if(item==null||!MPosJsonNumbers.truthy(item.opt("productId"))||!number(item,"qty").isFinite()||number(item,"qty")<0||!ids.add(key(item.opt("productId"))))invalid=true}
            if(invalid){warn("invalid");continue}
            for(j in 0 until lines.length()){val item=lines.getJSONObject(j);event(item.opt("productId"),null,null,number(item,"qty"),saleTime,"outgoing");if(MPosJsonNumbers.truthy(returnTime))event(item.opt("productId"),null,null,number(item,"qty"),returnTime,"returned")}
        }
        val collator=Collator.getInstance(Locale.forLanguageTag("ru"));val rows=map.values.sortedWith{a,b->collator.compare(a.name as String,b.name as String)}
        for(r in rows)if(r.cost==null&&(r.outgoing!=0.0||r.returned!=0.0))warn("unknownCosts")
        val suppliers=linkedMapOf<Any,JSONObject>()
        for(doc in documents){val supplier=doc.get("supplier");val entry=suppliers.getOrPut(supplier){JSONObject().put("name",supplier).put("count",0).put("total",0).put("shortages",0)};entry.put("count",entry.getInt("count")+1);val total=doc.optDouble("total");entry.numeric("total",entry.getDouble("total")+(if(total.isFinite())total else 0.0));if(doc.getBoolean("shortage"))entry.put("shortages",entry.getInt("shortages")+1)}
        val model=JSONObject().put("from",from).put("to",to).put("generatedTimestamp",now).put("rows",JSONArray(rows.map{it.json()})).put("documents",JSONArray(documents.sortedByDescending{it.getDouble("timestamp")})).put("suppliers",JSONArray(suppliers.values.toList())).put("warnings",warnings).numeric("recordedSalesCost",recordedSalesCost).put("missingSalesCosts",missingSalesCosts)
        model.numeric("incomingValue",rows.sumOf{it.incomingValue}).numeric("outgoingValue",rows.sumOf{it.cost?.let{cost->it.outgoing*cost}?:0.0}).numeric("returnedValue",rows.sumOf{it.cost?.let{cost->it.returned*cost}?:0.0})
            .numeric("currentValue",rows.sumOf{if(it.current!=null&&it.cost!=null)it.current*it.cost else 0.0}).numeric("endValue",rows.sumOf{if(it.current!=null&&it.cost!=null)(it.current-it.afterEnd)*it.cost else 0.0})
            .put("low",rows.count{it.current!=null&&it.min!=null&&it.min!==JSONObject.NULL&&it.current<=MPosPurchaseEngine.number(it.min)}).put("idle",rows.count{it.current!=null&&it.current>0&&it.outgoing==0.0})
        return model
    }
}
