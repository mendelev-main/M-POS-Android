package com.mendelev.mpos.payment

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

/** Initial equal parts only. Paid drafts and editing are separate migration boundaries. */
object MPosSplitPaymentPlans {
    fun calculate(total: Double, count: Int = 2): JSONObject? {
        require(count in 2..10) { "unsupported split count" }
        val scaled = total * 100
        if (!total.isFinite() || total < 0 || !scaled.isFinite() || scaled > 9_007_199_254_740_990.0) return null
        val lower = floor(scaled)
        val cents = (if (scaled - lower >= 0.5) lower + 1 else lower).toLong()
        val plans = JSONObject()
        val base = cents / count
        val remainder = cents % count
        val parts = JSONArray()
        for (index in 0 until count) {
            parts.put(JSONObject().put("method", "cash")
                .put("amount", (base + if (index < remainder) 1 else 0) / 100.0)
                .put("paid", false).put("cashGiven", JSONObject.NULL).put("change", JSONObject.NULL))
        }
        plans.put(count.toString(), parts)
        return plans
    }
}
