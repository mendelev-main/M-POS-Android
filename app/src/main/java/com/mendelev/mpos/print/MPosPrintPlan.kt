package com.mendelev.mpos.print

import com.mendelev.mpos.data.MPosJsonNumbers
import com.mendelev.mpos.data.MPosPurchaseEngine
import com.mendelev.mpos.data.MPosSupplyParity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max

/** Routing parity; platform settings authority is migrated separately. */
object MPosPrintPlan {
    fun validEndpoint(job:JSONObject):Boolean {
        val ip=job.optString("__networkPrinterIp").trim();val port=job.optInt("__networkPrinterPort",9100)
        return port in 1..65535&&ip.split('.').let{parts->parts.size==4&&parts.all{p->p.toIntOrNull()?.let{it in 0..255}==true}}
    }
    fun calculate(input:JSONObject):JSONArray {
        require(input.getInt("version")==1)
        val trigger=input.getString("trigger");val order=input.optJSONObject("order")?:JSONObject();val jobs=JSONArray()
        if(trigger=="direct"){jobs.put(JSONObject(order.toString()));return jobs}
        val printers=input.getJSONArray("printers")
        fun selected(kind:String)=(0 until printers.length()).map{printers.getJSONObject(it)}.filter{it.opt("enabled")!=false&&MPosJsonNumbers.truthy(it.opt("ip"))&&if(kind=="orders")it.opt("printOrders")==true else it.opt("printReceipts")!=false}
        fun append(p:JSONObject,kind:String){
            val job=JSONObject(order.toString());val items=order.optJSONArray("items");val categories=p.optJSONArray("orderCategories")
            if(kind=="orders"&&items!=null&&categories!=null&&categories.length()>0){job.put("items",JSONArray((0 until items.length()).map{items.getJSONObject(it)}.filter{i->
                val category=i.opt("category")
                // includes() compares primitive categories strictly; null is not an absent property.
                i.has("category")&&category !is JSONObject&&category !is JSONArray&&(0 until categories.length()).any{index->val selected=categories.opt(index);selected !is JSONObject&&selected !is JSONArray&&MPosSupplyParity.same(selected,category)}
            }))}
            if(kind=="orders"&&(job.optJSONArray("items")?.length()?:0)==0)return
            if(kind!="shift-close")job.put("currency",MPosJsonNumbers.fallback(order.opt("currency"),"BYN"))
            else job.put("timestamp",MPosJsonNumbers.fallback(order.opt("closedAt"),input.getLong("now")))
            job.put("__printDocumentType",when(kind){"orders"->"kitchen";"shift-close"->kind;else->"receipt"}).put("__networkPrinterIp",p.getString("ip")).put("__networkPrinterPort",9100).put("__printerConfig",JSONObject(p.toString()))
            val raw=MPosPurchaseEngine.number(p.opt("copies"));val copies=ceil(max(1.0,if(raw.isNaN()||raw==0.0)1.0 else raw));require(copies.isFinite()&&copies<=128){"Некорректное количество копий"}
            repeat(copies.toInt()){require(jobs.length()<128){"Слишком много заданий печати"};jobs.put(JSONObject(job.toString()))}
        }
        when(trigger){
            "manual-receipt"->selected("receipts").forEach{append(it,"receipts")}
            "manual-kitchen"->selected("orders").forEach{append(it,"orders")}
            "kitchen-now"->selected("orders").filter{it.opt("autoPrintKitchen")!=false}.forEach{append(it,"orders")}
            "completed"->{
                if(!MPosJsonNumbers.truthy(order.opt("kitchenPrinted")))selected("orders").filter{it.opt("autoPrintKitchen")!=false}.forEach{append(it,"orders")}
                selected("receipts").filter{it.opt("autoPrintReceipt")!=false}.forEach{append(it,"receipts")}
            }
            "shift-close"->selected("receipts").forEach{append(it,"shift-close")}
            else->throw IllegalArgumentException("unsupported print trigger")
        }
        return jobs
    }
}
