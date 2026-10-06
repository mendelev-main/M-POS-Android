package com.mendelev.mpos.payment

import com.mendelev.mpos.data.MPosJsonNumbers
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

/** Read-only redistribution; paid rows and existing tender fields retain reviewed semantics. */
object MPosSplitCountEngine {
    private fun cents(value: Double): Long {
        val scaled = value * 100
        require(value.isFinite() && scaled.isFinite() && kotlin.math.abs(scaled) <= 9_007_199_254_740_990.0) { "unsafe split amount" }
        val lower = floor(scaled)
        return (if (scaled - lower >= 0.5) lower + 1 else lower).toLong()
    }
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val total = input.getDouble("total")
        val delta = input.getInt("delta")
        require(delta == -1 || delta == 1)
        val parts = input.getJSONArray("parts")
        require(parts.length() in 2..10)
        val next = (parts.length() + delta).coerceIn(2, 10)
        val result = JSONObject().put("ok", true).put("authoritative", true).put("source", "native-split-count")
        if (next == parts.length()) return result.put("changed", false).put("allowed", true)
        val paid = (0 until parts.length()).filter { MPosJsonNumbers.truthy(parts.getJSONObject(it).opt("paid")) }
        if (next < paid.size) return result.put("changed", false).put("allowed", false)
            .put("reason", "Нельзя уменьшить количество платежей: уже есть оплаченные части")
        val unpaid = (0 until parts.length()).filter { it !in paid }.toMutableList()
        if (next > parts.length()) repeat(next - parts.length()) { unpaid.add(-1) }
        else while (unpaid.size > next - paid.size) unpaid.removeAt(unpaid.lastIndex)
        // Source rounds the sum of paid amounts, rather than rounding each part first.
        val paidSum = paid.sumOf { index ->
            val value = parts.getJSONObject(index).opt("amount")
            MPosJsonNumbers.number(if (MPosJsonNumbers.truthy(value)) value else 0)
        }
        val remaining = (cents(total) - cents(paidSum)).coerceAtLeast(0)
        val out = JSONArray(); val indices = JSONArray()
        for (index in paid) { out.put(JSONObject(parts.getJSONObject(index).toString())); indices.put(index) }
        for ((position, index) in unpaid.withIndex()) {
            val part = if (index >= 0) JSONObject(parts.getJSONObject(index).toString()) else JSONObject()
                .put("method", "cash").put("amount", 0).put("paid", false).put("cashGiven", JSONObject.NULL).put("change", JSONObject.NULL)
            val base = remaining / unpaid.size; val remainder = remaining % unpaid.size
            part.put("amount", (base + if (position < remainder) 1 else 0) / 100.0)
            out.put(part); indices.put(index)
        }
        return result.put("allowed", true).put("changed", true).put("parts", out).put("indices", indices).put("count", next)
    }
}
