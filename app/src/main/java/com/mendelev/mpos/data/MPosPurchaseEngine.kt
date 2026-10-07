package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object MPosPurchaseEngine {
    private data class UnitMeta(val kind:String,val scale:Double)
    private val units=mapOf("piece" to UnitMeta("count",1.0),"kg" to UnitMeta("mass",1000.0),"g" to UnitMeta("mass",1.0),"l" to UnitMeta("volume",1000.0),"ml" to UnitMeta("volume",1.0))
    fun stockUnit(p:JSONObject):Any=MPosJsonNumbers.fallback(p.opt(if(p.opt("type")=="simple")"stockUnit" else "recipeUnit"),if(p.opt("type")=="simple")"" else "piece")
    fun number(value:Any?)=MPosJsonNumbers.number(if(value is JSONArray)MPosAvailabilityEngine.text(value) else value)
    fun nullish(value:Any?,fallback:Any):Any=if(value==null||value===JSONObject.NULL)fallback else value
    fun convert(qty:Double,from:Any,to:Any):Double {
        if(MPosSupplyParity.same(from,to))return qty
        val a=if(from is String)units[from] else null;val b=if(to is String)units[to] else null;check(a!=null&&b!=null&&a.kind==b.kind){"Несовместимые единицы измерения"}
        val value=qty*a.scale/b.scale;check(value.isFinite()){"Некорректное количество"}
        return BigDecimal(value).round(MathContext(15,RoundingMode.HALF_UP)).toDouble()
    }
    fun line(p:JSONObject,qtyValue:Any?,unit:Any,packSize:Any?,contentUnit:Any?,at:Long):JSONObject {
        val qty=number(qtyValue);check(qty.isFinite()&&qty>0){"Количество заказа должно быть больше нуля"}
        val base=stockUnit(p);val pack=unit in setOf("bottle","pack","box");val inside=MPosJsonNumbers.fallback(contentUnit,base)
        val hasSize=packSize!=null&&packSize!==JSONObject.NULL&&packSize!=""
        var expected:Double?=null
        if(pack){if(hasSize){val size=number(packSize);check(size.isFinite()&&size>0){"Количество в единице закупки должно быть больше нуля"};expected=convert(qty*size,inside,base)}}else expected=convert(qty,unit,base)
        check(expected==null||(expected.isFinite()&&expected>0)){"Некорректное количество заказа"}
        return JSONObject().apply{if(p.has("id"))put("productId",p.get("id"));if(p.has("name"))put("productName",p.get("name"))}
            .put("qty",expected?:0).put("requestedQty",qty).put("requestedUnit",unit).put("stockUnit",base).put("contentUnit",inside)
            .put("expectedQty",expected?:JSONObject.NULL).put("packSize",if(pack&&hasSize)number(packSize) else JSONObject.NULL).put("timestamp",at)
    }
}
