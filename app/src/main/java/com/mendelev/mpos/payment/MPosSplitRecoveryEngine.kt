package com.mendelev.mpos.payment

import com.mendelev.mpos.data.MPosCartTotalsEngine
import com.mendelev.mpos.data.MPosJsonNumbers
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.abs

/** Pure normalization and compatible restart draft validation. Never rewrites paid rows. */
object MPosSplitRecoveryEngine {
    private fun round(value: Double): Double {
        val lower = floor(value)
        return if (value - lower >= 0.5) lower + 1 else lower
    }
    private fun money(value: Double) = round(value * 100) / 100
    private fun number(value: Any?): Double = when (value) {
        is JSONArray -> when (value.length()) {
            0 -> 0.0
            1 -> when (val item = value.opt(0)) {
                is Boolean, is JSONObject -> Double.NaN
                else -> number(item)
            }
            else -> Double.NaN
        }
        else -> MPosJsonNumbers.number(value)
    }
    private fun string(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONArray -> (0 until value.length()).joinToString(",") { string(value.opt(it)) }
        else -> value.toString()
    }
    private fun normalize(input: JSONObject): JSONObject {
        val parts = input.getJSONArray("parts")
        val result = JSONObject().put("changed", parts.length() > 0)
        if (parts.length() == 0) return result
        val total = input.getDouble("total"); require(total.isFinite())
        val paid = (0 until parts.length()).filter { MPosJsonNumbers.truthy(parts.getJSONObject(it).opt("paid")) }
        val unpaid = (0 until parts.length()).filter { it !in paid }
        val paidSum = paid.sumOf { number(MPosJsonNumbers.fallback(parts.getJSONObject(it).opt("amount"), 0)) }
        val remaining = (round(total * 100) - round(paidSum * 100)).coerceAtLeast(0.0)
        require(remaining.isFinite() && remaining <= 9_007_199_254_740_990.0)
        val out = JSONArray(parts.toString())
        if (unpaid.isNotEmpty()) {
            val base = floor(remaining / unpaid.size); val remainder = remaining - base * unpaid.size
            unpaid.forEachIndexed { index, row -> out.getJSONObject(row).put("amount", (base + if (index < remainder) 1 else 0) / 100) }
        }
        return result.put("parts", out).put("count", parts.length())
    }
    private fun normalizedParts(parts: JSONArray): JSONArray {
        require(parts.length() > 0)
        val out = JSONArray()
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            val method = string(MPosJsonNumbers.fallback(part.opt("method")))
            require(method == "cash" || method == "card")
            require(part.has("amount"))
            val raw = number(part.opt("amount")); require(raw.isFinite() && raw >= 0)
            val amount = money(raw)
            var given = if (part.isNull("cashGiven") || !part.has("cashGiven")) null else number(part.opt("cashGiven"))
            var change = if (part.isNull("change") || !part.has("change")) null else number(part.opt("change"))
            require(given == null || given.isFinite() && given >= 0)
            require(change == null || change.isFinite() && change >= 0)
            require(method != "cash" || given == null || given + 0.0001 >= amount)
            require(method != "card" || given == null && change == null)
            given = given?.let(::money); change = change?.let(::money)
            require(method != "cash" || given == null || change == null || abs(change - (given - amount)) <= 0.001)
            out.put(JSONObject().put("method", method).put("amount", amount)
                .put("paid", part.opt("paid") == true).put("cashGiven", given ?: JSONObject.NULL).put("change", change ?: JSONObject.NULL))
        }
        return out
    }
    private fun restore(input: JSONObject): JSONObject {
        val expectedTotal = if (input.has("quote")) MPosCartTotalsEngine.calculate(input.getJSONObject("quote"))
            .getJSONObject("pricing").getDouble("total") else number(input.opt("expectedTotal"))
        val result = JSONObject().put("valid", false).put("draft", JSONObject.NULL).put("expectedTotal", expectedTotal)
        val draft = input.optJSONObject("draft") ?: return result
        try {
            val version = draft.opt("version"); require(version is Number && version.toDouble() == 1.0)
            val rawCents = draft.opt("totalCents"); require(rawCents is Number)
            val totalCents = rawCents.toDouble(); require(totalCents.isFinite() && totalCents >= 0 && floor(totalCents) == totalCents)
            val parts = normalizedParts(draft.getJSONArray("parts"))
            require(parts.length() in 2..10 && (0 until parts.length()).any { parts.getJSONObject(it).getBoolean("paid") })
            val partsCents = (0 until parts.length()).sumOf { round(parts.getJSONObject(it).getDouble("amount") * 100) }
            require(partsCents == totalCents && totalCents == round(expectedTotal * 100))
            val updatedAt = number(draft.opt("updatedAt")).let { if (it == 0.0 || it.isNaN()) input.getDouble("now") else it }
            check(updatedAt.isFinite()) { "unencodable draft timestamp" } // Bridge failure retains the reviewed compatibility path.
            val valid = JSONObject().put("version", 1).put("totalCents", totalCents).put("parts", parts).put("updatedAt", updatedAt)
            return result.put("valid", true).put("draft", valid)
        } catch (_error: IllegalArgumentException) { return result }
          catch (_error: org.json.JSONException) { return result }
    }
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val result = when (input.getString("operation")) {
            "normalize" -> normalize(input)
            "restore" -> restore(input)
            else -> throw IllegalArgumentException("unknown split recovery operation")
        }
        return result.put("ok", true).put("authoritative", true).put("source", "native-split-recovery")
    }
}
