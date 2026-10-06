package com.mendelev.mpos.data

import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Reviewed editor validation. No unit conversion, stock decrement or writes. */
object MPosRecipeEditEngine {
    private fun same(a: Any?, b: Any?) = if (a is Number && b is Number) a.toDouble() == b.toDouble() else a == b
    private fun number(v: Any?): Double {
        fun text(value: Any?): String = when(value) {
            null, JSONObject.NULL -> ""
            is JSONArray -> (0 until value.length()).joinToString(",") { text(value.opt(it)) }
            is JSONObject -> "[object Object]"
            else -> value.toString()
        }
        val value=if(v is JSONArray)text(v) else v
        if(value is String) {
            val trimmed=MPosOrderContextEngine.trim(value)
            if(trimmed != trimmed.trim { it.isWhitespace() || it == '\uFEFF' })return Double.NaN
            return MPosJsonNumbers.number(trimmed)
        }
        return MPosJsonNumbers.number(value)
    }
    private fun numericOr(v: Any?, fallback: Double): Double = number(v).let { if (it.isNaN() || it == 0.0) fallback else it }
    private fun string(v: Any?): String = when(v) { null -> "undefined"; JSONObject.NULL -> "null"; is JSONObject -> "[object Object]"; is JSONArray -> (0 until v.length()).joinToString(",") { if(v.isNull(it)) "" else string(v.opt(it)) }; else -> v.toString() }
    private fun reply() = JSONObject().put("ok",true).put("authoritative",true).put("source","native-recipe-edit")
    private class Policy(message: String): IllegalArgumentException(message)
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val products = input.getJSONArray("products")
        fun resolve(id: Any?): JSONObject? = (0 until products.length()).map { products.getJSONObject(it) }.firstOrNull { same(it.opt("id"),id) }
        return try {
            when(input.getString("operation")) {
                "recipe" -> {
                    val totals = mutableListOf<Pair<Any?,Double>>()
                    val trail = mutableListOf<Any?>()
                    data class Step(val product: JSONObject?,val qty: Double,val exit: Boolean=false,val error: String?=null)
                    val stack=ArrayDeque<Step>();stack.addLast(Step(input.getJSONObject("draft"),1.0))
                    while(stack.isNotEmpty()) {
                        val step=stack.removeLast();if(step.error!=null)throw Policy(step.error);val p=step.product ?: throw Policy("Товар или ингредиент не найден")
                        val id=p.opt("id");val name=string(p.opt("name"))
                        if(step.exit){trail.removeAt(trail.lastIndex);continue}
                        if(!step.qty.isFinite() || step.qty<=0)throw Policy("Количество ингредиента должно быть больше нуля")
                        if(trail.any { same(it,id) })throw Policy("Циклический состав: $name")
                        if(p.opt("type")=="simple") {
                            val index=totals.indexOfFirst { same(it.first,id) };val total=(if(index<0)0.0 else totals[index].second)+step.qty
                            if(!total.isFinite())throw Policy("Слишком большое количество: $name")
                            if(index<0)totals.add(id to total) else totals[index]=id to total
                        }else {
                            val components=p.optJSONArray("components")
                            if(p.opt("type")!="composite" || components==null || components.length()==0)throw Policy("Проверьте состав товара: $name")
                            trail.add(id);stack.addLast(Step(p,step.qty,true))
                            for(i in components.length()-1 downTo 0) {
                                val c=components.opt(i)
                                if(!MPosJsonNumbers.truthy(c))stack.addLast(Step(null,step.qty,error="Некорректный ингредиент: $name"))
                                else if(c is JSONObject)stack.addLast(Step(resolve(c.opt("productId")),step.qty*number(c.opt("qty"))))
                                else stack.addLast(Step(null,step.qty))
                            }
                        }
                    }
                    val ingredients=JSONArray();for((id,qty)in totals)ingredients.put(JSONObject().put("productId",id?:JSONObject.NULL).put("qty",qty))
                    reply().put("allowed",true).put("ingredients",ingredients)
                }
                "modifiers" -> {
                    if(input.opt("type")=="composite") {
                        val yield=number(input.opt("recipeYield"));if(!yield.isFinite()||yield<=0)throw Policy("Выход состава должен быть больше нуля")
                    }
                    val raw=input.getJSONArray("groups");val generated=input.getJSONArray("generatedIds");val normalized=JSONArray()
                    for(i in 0 until raw.length()) {
                        val g=raw.optJSONObject(i)?:JSONObject();val ids=generated.getJSONObject(i)
                        val maximum=max(1.0,numericOr(g.opt("max"),1.0));val minimum=max(0.0,min(maximum,numericOr(g.opt("min"),0.0)))
                        val name=string(MPosJsonNumbers.fallback(g.opt("name")))
                        val group=JSONObject().put("id",MPosJsonNumbers.fallback(g.opt("id"),ids.get("id"))).put("name",name).put("min",minimum).put("max",maximum)
                        val options=g.optJSONArray("options")?:JSONArray();val out=JSONArray()
                        for(j in 0 until options.length()) {
                            val o=options.getJSONObject(j)
                            out.put(JSONObject().put("id",MPosJsonNumbers.fallback(o.opt("id"),ids.getJSONArray("options").get(j)))
                                .put("productId",MPosJsonNumbers.fallback(o.opt("productId")))
                                .put("posName",string(MPosJsonNumbers.fallback(o.opt("posName"))))
                                .put("qty",numericOr(o.opt("qty"),1.0)).put("priceDelta",numericOr(o.opt("priceDelta"),0.0)))
                        }
                        group.put("options",out)
                        if(MPosOrderContextEngine.trim(name).isEmpty())throw Policy("Укажите название каждой группы модификаторов")
                        if(minimum>maximum || minimum>out.length())throw Policy("Проверьте минимум и максимум группы: $name")
                        if(out.length()==0)throw Policy("Добавьте варианты в группу: $name")
                        val seen=mutableListOf<Any?>()
                        for(j in 0 until out.length()) {
                            val o=out.getJSONObject(j);val id=o.opt("productId")
                            if(!MPosJsonNumbers.truthy(id)||same(id,input.opt("editingId"))||resolve(id)==null)throw Policy("Выберите существующий товар для каждого модификатора")
                            if(seen.any{same(it,id)})throw Policy("Товар повторяется в группе: $name")
                            seen.add(id)
                            val qty=o.getDouble("qty");if(!qty.isFinite()||qty<=0)throw Policy("Количество модификатора должно быть больше нуля")
                        }
                        normalized.put(group)
                    }
                    reply().put("allowed",true).put("groups",normalized)
                }
                else -> throw IllegalArgumentException("unknown recipe editor operation")
            }
        } catch(error: Policy) { reply().put("allowed",false).put("message",error.message) }
    }
}
