package com.mendelev.mpos.data

import org.json.JSONObject

/** One read-only quote shared by payment presentation and settlement engines. */
object MPosCartTotalsEngine {
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1) { "unsupported cart totals input" }
        val items = input.getJSONArray("items")
        val loyalty = MPosLoyaltyRewardEngine.calculate(items, input.getJSONArray("programs"), input.getJSONObject("redemptions"))
        val pricing = MPosPricingEngine.calculate(items, input.getJSONArray("discounts"), input.optString("orderType"),
            input.opt("deliveryFee"), loyalty.getDouble("discount"))
        return JSONObject().put("ok", true).put("authoritative", true).put("source", "native-cart-totals")
            .put("pricing", pricing).put("loyalty", loyalty)
    }
}
