package com.mendelev.mpos.workspace

import com.mendelev.mpos.data.*
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject

/** Modifier selection and merge rules of the reviewed cart, without DOM selection. */
object MPosCartAddModel {
    fun number(value:Any?)=MPosJsonNumbers.number(if(value is JSONArray)MPosAvailabilityEngine.text(value) else value)
    fun orZero(value:Any?)=number(value).let{if(it.isNaN()||it==0.0)0.0 else it}
    fun product(products:JSONArray,id:Any?):JSONObject? {
        if(id is JSONObject||id is JSONArray)return null
        return (0 until products.length()).map{products.getJSONObject(it)}.firstOrNull{MPosSupplyParity.same(it.opt("id"),id)}
    }
    fun groups(p:JSONObject,products:JSONArray):JSONArray {
        val raw=p.optJSONArray("modifierGroups")?:JSONArray();val result=JSONArray()
        for(i in 0 until raw.length()) {
            val g=raw.getJSONObject(i);require(MPosJsonNumbers.truthy(g.opt("id"))){"legacy modifier identity"}
            val max=kotlin.math.max(1.0,orZero(g.opt("max")));val min=kotlin.math.max(0.0,kotlin.math.min(max,orZero(g.opt("min"))))
            require(max.isFinite()&&min.isFinite())
            val options=JSONArray();val source=g.optJSONArray("options")?:JSONArray()
            for(j in 0 until source.length()) {
                val o=source.getJSONObject(j);require(MPosJsonNumbers.truthy(o.opt("id"))){"legacy modifier identity"}
                val id=MPosJsonNumbers.fallback(o.opt("productId"));val mp=product(products,id)
                val qty=orZero(o.opt("qty")).let{if(it==0.0)1.0 else it};val delta=orZero(o.opt("priceDelta"));require(qty.isFinite()&&delta.isFinite())
                val posName=MPosOrderContextEngine.trim(MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(o.opt("posName"))))
                options.put(JSONObject().put("id",o.get("id")).put("productId",id).put("qty",qty).put("priceDelta",delta)
                    .put("name",if(posName.isNotEmpty())posName else MPosAvailabilityEngine.text(mp?.opt("name")?:"Товар недоступен")).put("available",mp!=null))
            }
            result.put(JSONObject().put("id",g.get("id")).put("name",MPosAvailabilityEngine.text(MPosJsonNumbers.fallback(g.opt("name")))).put("min",min).put("max",max).put("options",options))
        };return result
    }
    fun selected(groups:JSONArray,selections:JSONArray,validate:Boolean=true):JSONArray {
        require(selections.length()==groups.length());val mods=JSONArray()
        for(i in 0 until groups.length()) {
            val g=groups.getJSONObject(i);val chosen=selections.getJSONArray(i);val options=g.getJSONArray("options")
            val indices=(0 until chosen.length()).map{chosen.getInt(it)}
            require(indices.toSet().size==indices.size&&indices.all{it in 0 until options.length()})
            if(validate)require(indices.size>=g.getDouble("min")&&indices.size<=g.getDouble("max")){"«${g.getString("name") }»: выберите от ${MPosAvailabilityEngine.text(g.get("min"))} до ${MPosAvailabilityEngine.text(g.get("max"))}"}
            for(index in indices.sorted()) {
                // HTML dataset strings resolve the first strictly matching option, even for legacy duplicates.
                val datasetId=MPosAvailabilityEngine.text(options.getJSONObject(index).get("id"))
                val o=(0 until options.length()).map{options.getJSONObject(it)}.firstOrNull{it.opt("id") is String&&it.getString("id")==datasetId}?:continue
                if(validate)require(o.getBoolean("available")){"Модификатор больше недоступен: ${g.getString("name")}"}
                mods.put(JSONObject().put("groupId",g.get("id")).put("groupName",g.getString("name")).put("optionId",o.get("id"))
                    .put("productId",o.get("productId")).put("name",o.getString("name")).put("qty",o.get("qty")).put("priceDelta",o.get("priceDelta")))
            }
        };return mods
    }
    fun signature(mods:JSONArray):String {
        val tuples=(0 until mods.length()).map{mods.getJSONObject(it).let{m->JSONArray().put(m.opt("groupId")?:JSONObject.NULL).put(m.opt("productId")?:JSONObject.NULL).put(orZero(m.opt("qty"))).put(orZero(m.opt("priceDelta")))}}
        return JSONArray(tuples.sortedBy{MPosAvailabilityEngine.text(it)}).toString()
    }
    fun existing(items:JSONArray,id:Any,mods:JSONArray,manual:Boolean):Int? {
        if(manual)return null;val expectedSignature=signature(mods)
        return (0 until items.length()).firstOrNull { i->val item=items.getJSONObject(i)
            MPosSupplyParity.same(item.opt("productId"),id)&&!MPosJsonNumbers.truthy(item.opt("comment"))&&!MPosJsonNumbers.truthy(item.opt("discountId"))&&signature(item.optJSONArray("selectedModifiers")?:JSONArray())==expectedSignature
        }
    }
}
