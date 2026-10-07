package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.truncate

/** Mirrors requested > Number(rewards || 0), including JS NaN comparison semantics. */
object MPosLoyaltyEligibilityEngine {
    private fun text(value: Any?, present: Boolean = true): String = when {
        !present -> "undefined"
        value == null || value === JSONObject.NULL -> "null"
        value is JSONArray -> (0 until value.length()).joinToString(",") { if(value.isNull(it)) "" else text(value.opt(it)) }
        value is JSONObject -> "[object Object]"
        value is Number -> value.toDouble().let { if(it == 0.0) "0" else if(kotlin.math.abs(it)>=1e-6 && kotlin.math.abs(it)<1e21)java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString() else it.toString().lowercase().replace(Regex("\\.0e"),"e").replace(Regex("e(?!-)"),"e+") }
        else -> value.toString()
    }
    private fun number(value: Any?): Double {
        val scalar = if(value is JSONArray)text(value) else value
        if(scalar is String) {
            val trimmed = com.mendelev.mpos.payment.MPosOrderContextEngine.trim(scalar)
            if(trimmed.isNotEmpty() && (trimmed.first().isWhitespace() || trimmed.last().isWhitespace()))return Double.NaN
            return MPosJsonNumbers.number(trimmed)
        }
        return MPosJsonNumbers.number(scalar)
    }
    private fun requested(value: Any?): Double = max(0.0, truncate(number(value).let { if(it.isNaN() || it == 0.0)0.0 else it }))
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val redemptions = input.getJSONObject("redemptions")
        val selected = redemptions.keys().asSequence().any { requested(redemptions.opt(it)) > 0 }
        val programs = input.getJSONArray("programs")
        val byId = linkedMapOf<String, JSONObject>()
        for(i in 0 until programs.length()) {val p=programs.getJSONObject(i);byId[text(p.opt("id"),p.has("id"))]=p}
        val valid = redemptions.keys().asSequence().none { id ->
            requested(redemptions.opt(id)) > number(MPosJsonNumbers.fallback(byId[id]?.opt("rewards"),0))
        }
        return JSONObject().put("ok",true).put("authoritative",true).put("source","native-loyalty-eligibility")
            .put("selected",selected).put("valid",valid)
    }
}
