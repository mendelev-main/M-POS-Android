package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Settlement arithmetic; configured prices already include manual prices/modifiers. */
object MPosPricingEngine {
    private fun rounded(value: Double) = MPosJsonNumbers.roundMoney(value)
    private fun numeric(value: Any?) = MPosJsonNumbers.number(value)
    private fun fallbackNumber(value: Any?) = numeric(MPosJsonNumbers.fallback(value, 0))
    private fun numberOrZero(value: Any?) = numeric(value).let { if (it.isNaN() || it == 0.0) 0.0 else it }
    fun calculate(items: JSONArray, discounts: JSONArray, orderType: String, deliveryFee: Any?, loyaltyDiscount: Double): JSONObject {
        var subtotal = 0.0; var gross = 0.0; var discountTotal = 0.0
        val lines = JSONArray()
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            // Missing fields in multiplication are JS undefined (NaN), unlike explicit JSON null.
            val price = if (item.has("price")) numeric(item.opt("price")) else Double.NaN
            val qty = if (item.has("qty")) numeric(item.opt("qty")) else Double.NaN
            val base = price * qty
            val definition = if (MPosJsonNumbers.truthy(item.opt("discountId")))
                (0 until discounts.length()).map { discounts.getJSONObject(it) }.firstOrNull { sameId(it.opt("id"), item.opt("discountId")) }
                else null
            val value = definition?.let { max(0.0, numberOrZero(it.opt("value"))) } ?: 0.0
            val discount = when {
                definition == null -> 0.0
                definition.optString("type") == "percent" -> base * min(100.0, value) / 100
                else -> min(base, value * qty)
            }
            val total = max(0.0, base - discount)
            require(total.isFinite() && discount.isFinite()) { "invalid pricing input" }
            subtotal += total; discountTotal += discount
            gross += fallbackNumber(item.opt("price")) * fallbackNumber(item.opt("qty"))
            lines.put(JSONObject().put("discount", discount).put("total", total))
        }
        val delivery = if (orderType == "Доставка") fallbackNumber(deliveryFee) else 0.0
        val total = max(0.0, subtotal + delivery - loyaltyDiscount)
        require(listOf(subtotal, gross, discountTotal, total, loyaltyDiscount).all { it.isFinite() }) { "invalid pricing input" }
        return JSONObject().put("lines", lines).put("subtotal", subtotal).put("total", total)
            .put("productDiscountTotal", rounded(discountTotal)).put("subtotalBeforeDiscounts", rounded(gross))
    }
    private fun sameId(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        a is String && b is String -> a == b
        else -> a == b
    }
    fun validate(order: JSONObject, input: JSONObject) {
        require(input.getInt("version") == 1) { "unsupported pricing input" }
        val result = calculate(order.getJSONArray("items"), input.getJSONArray("discounts"), order.optString("orderType"),
            order.opt("deliveryFee"), input.getDouble("loyaltyDiscount"))
        for (key in listOf("total", "productDiscountTotal", "subtotalBeforeDiscounts")) {
            require(order.getDouble(key) == result.getDouble(key)) { "pricing differs from displayed receipt" }
            order.put(key, result.getDouble(key))
        }
        require(order.getDouble("loyaltyDiscount") == input.getDouble("loyaltyDiscount")) { "loyalty input changed" }
    }
}
