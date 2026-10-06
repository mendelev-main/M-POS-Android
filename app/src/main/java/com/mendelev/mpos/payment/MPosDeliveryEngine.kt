package com.mendelev.mpos.payment

import com.mendelev.mpos.data.MPosJsonNumbers
import org.json.JSONArray
import org.json.JSONObject

/** Existing delivery selection policy, with no rounding or catalogue synchronization. */
object MPosDeliveryEngine {
    private fun number(value: Any?): Double = when (value) {
        is JSONArray -> when (value.length()) {
            0 -> 0.0
            1 -> when (val item = value.opt(0)) { is Boolean, is JSONObject -> Double.NaN; else -> number(item) }
            else -> Double.NaN
        }
        else -> MPosJsonNumbers.number(value)
    }
    private fun fee(state: JSONObject) = if (state.has("fee")) number(state.opt("fee")) else Double.NaN
    private fun matches(state: JSONObject, amount: Double): Boolean {
        val rates = state.getJSONArray("rates")
        return (0 until rates.length()).any { i ->
            val row = rates.getJSONObject(i)
            row.has("amount") && number(row.opt("amount")) == amount
        }
    }
    fun allowed(state: JSONObject): Boolean {
        if (state.opt("orderType") != "Доставка") return true
        val amount = fee(state)
        return state.opt("selected") == true && amount.isFinite() && amount >= 0 && matches(state, amount)
    }
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val state = input.getJSONObject("state")
        val result = JSONObject().put("ok", true).put("authoritative", true).put("source", "native-delivery")
        return when (input.getString("operation")) {
            "check" -> result.put("allowed", allowed(state))
            "select" -> {
                val amount = if (input.has("amount")) number(input.opt("amount")) else Double.NaN
                if (!matches(state, amount)) result.put("changed", false)
                else result.put("changed", true).put("fee", if (amount == 0.0) 0 else amount).put("selected", true)
            }
            "type" -> {
                val type = input.getString("orderType")
                val selected = if (state.opt("orderType") != type) false else state.opt("selected") ?: JSONObject.NULL
                result.put("changed", true).put("orderType", type).put("selected", selected)
                    .put("fee", if (type == "Доставка") state.opt("fee") ?: JSONObject.NULL else 0)
            }
            else -> throw IllegalArgumentException("unknown delivery operation")
        }
    }
}
