package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Availability is advisory: invalid recipes produce zero, unlimited stock produces null. */
object MPosAvailabilityEngine {
    private data class Key(val type:String,val value:Any?)
    private fun key(value:Any?)=when(value){null->Key("missing",null);JSONObject.NULL->Key("null",null);is Number->Key("number",value.toDouble());is String->Key("string",value);else->Key("other",value)}
    fun text(value:Any?):String=when(value){
        null->"undefined";JSONObject.NULL->"null";is JSONObject->"[object Object]"
        is JSONArray->(0 until value.length()).joinToString(","){if(value.isNull(it))"" else text(value.opt(it))}
        is Number->value.toDouble().let{if(it==0.0)"0" else if(abs(it)>=1e-6&&abs(it)<1e21)java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString() else it.toString().lowercase().replace(Regex("\\.0e"),"e").replace(Regex("e(?!-)"),"e+")}
        else->value.toString()
    }
    private fun number(value:Any?)=MPosJsonNumbers.number(if(value is JSONArray)text(value) else value)
    private fun stock(p:JSONObject)=number(p.opt("stock")).let{if(it.isNaN()||it==0.0)0.0 else it}
    private data class Step(val product:JSONObject,val qty:Double,val exit:Boolean=false)
    private fun expand(root:JSONObject,byId:Map<Key,JSONObject>):Map<Key,Double>{
        val totals=linkedMapOf<Key,Double>();val trail=mutableSetOf<Key>();val stack=ArrayDeque<Step>();stack.addLast(Step(root,1.0))
        while(stack.isNotEmpty()){
            val step=stack.removeLast();val p=step.product;val id=key(p.opt("id"))
            if(step.exit){trail.remove(id);continue}
            require(step.qty.isFinite()&&step.qty>0&&id !in trail)
            if(p.opt("type")=="simple"){
                val total=(totals[id]?:0.0)+step.qty;require(total.isFinite());totals[id]=total
            }else{
                require(p.opt("type")=="composite");val components=p.getJSONArray("components");require(components.length()>0)
                trail.add(id);stack.addLast(Step(p,step.qty,true))
                for(i in components.length()-1 downTo 0){val c=components.getJSONObject(i);val child=byId[key(c.opt("productId"))]?:error("missing ingredient");stack.addLast(Step(child,step.qty*number(c.opt("qty"))))}
            }
        };return totals
    }
    /** Build the ingredient lookup once; workspace callers calculate only visible products. */
    fun reader(products:JSONArray):(JSONObject)->Any {
        val byId=linkedMapOf<Key,JSONObject>()
        for(i in 0 until products.length()){val p=products.getJSONObject(i);byId[key(p.opt("id"))]=p}
        return {p->
            val quantity=if(p.opt("type")=="simple"){
                if(MPosJsonNumbers.truthy(p.opt("noStockTracking")))Double.POSITIVE_INFINITY else stock(p)
            }else try{
                val ingredients=expand(p,byId)
                var min=Double.POSITIVE_INFINITY
                for((id,qty) in ingredients){
                    val ingredient=byId[id] ?: error("missing ingredient")
                    if(ingredient.opt("type")!="simple"||MPosJsonNumbers.truthy(ingredient.opt("noStockTracking")))continue
                    val ratio=stock(ingredient)/qty
                    if(!ratio.isFinite()){min=0.0;break}
                    min=kotlin.math.min(min,max(0.0,floor(ratio+Math.ulp(1.0)*8*max(1.0,abs(ratio)))))
                };min
            }catch(_:Exception){0.0}
            if(quantity==Double.POSITIVE_INFINITY)JSONObject.NULL else if(quantity.isFinite())max(0.0,quantity) else 0.0
        }
    }
    fun items(products:JSONArray):JSONArray {
        val quantity=reader(products);val out=JSONArray()
        for(i in 0 until products.length()){
            val p=products.getJSONObject(i)
            out.put(JSONObject().put("externalId",text(p.opt("id"))).put("quantity",quantity(p)))
        }
        return out // Adapter retains reviewed localeCompare ordering; quantities are native.
    }
}
