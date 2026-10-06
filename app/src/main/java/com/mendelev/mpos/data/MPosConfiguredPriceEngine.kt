package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

/** Unit-price formation; modifier quantity affects stock, not the reviewed price delta. */
object MPosConfiguredPriceEngine {
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1) { "unsupported configured price input" }
        val manualInput = input.has("manualInput")
        val manual = manualInput || (input.has("manualBasePrice") && !input.isNull("manualBasePrice"))
        val base = when {
            manualInput -> {
                val raw = input.getString("manualInput").replaceFirst(',', '.')
                val value = MPosJsonNumbers.number(raw)
                require(value.isFinite() && value > 0) { "invalid manual price" }
                // The positive guard is before rounding, as in the reviewed form.
                MPosJsonNumbers.roundMoney(value)
            }
            manual -> MPosJsonNumbers.number(input.opt("manualBasePrice"))
            else -> MPosJsonNumbers.number(input.opt("catalogPrice")).let { if (it.isNaN() || it == 0.0) 0.0 else it }
        }
        val modifiers = input.getJSONArray("modifiers")
        var extra = 0.0
        for (index in 0 until modifiers.length()) {
            val modifier = modifiers.getJSONObject(index)
            extra += MPosJsonNumbers.number(MPosJsonNumbers.fallback(modifier.opt("priceDelta"), 0))
        }
        val price = base + extra
        require(base.isFinite() && extra.isFinite() && price.isFinite()) { "invalid configured price" }
        return JSONObject().put("ok", true).put("authoritative", true).put("source", "kotlin-configured-price")
            .put("basePrice", base).put("extra", extra).put("price", price).put("manualPrice", manual)
    }
    fun validate(order: JSONObject, input: JSONObject) {
        require(input.getInt("version") == 1) { "unsupported configured price input" }
        val items = order.getJSONArray("items")
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            // Imported/pre-migration lines without a base snapshot retain their historical price.
            if (!item.has("basePrice")) continue
            val modifiers = if (!MPosJsonNumbers.truthy(item.opt("selectedModifiers"))) JSONArray() else item.getJSONArray("selectedModifiers")
            val request = JSONObject().put("version", 1).put("modifiers", modifiers)
                .put("manualBasePrice", item.opt("basePrice") ?: JSONObject.NULL)
                .put("catalogPrice", item.opt("basePrice") ?: JSONObject.NULL)
            val result = calculate(request)
            require(item.getDouble("price") == result.getDouble("price")) { "configured price differs from receipt" }
        }
    }
}
