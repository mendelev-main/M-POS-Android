package com.mendelev.mpos.payment

import com.mendelev.mpos.data.MPosJsonNumbers
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor

/** Reviewed split amount editing; no persistence, stock reservation or terminal effects. */
object MPosSplitAmountEngine {
    private val whitespace = Regex("[\\u0009-\\u000d\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]")
    fun parse(raw: String): Double? {
        val text = whitespace.replace(raw, "").replaceFirst(",", ".")
        if (!Regex("[0-9]+(?:\\.[0-9]{0,2})?").matches(text)) return null
        return text.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    private fun cents(value: Double): Double {
        val scaled = value * 100
        val lower = floor(scaled)
        return if (scaled - lower >= 0.5) lower + 1 else lower
    }
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val parts = input.getJSONArray("parts")
        require(parts.length() in 2..10)
        val index = input.getInt("index")
        val out = JSONObject().put("ok", true).put("authoritative", true).put("source", "native-split-amount").put("changed", false)
        if (index !in 0 until parts.length() || MPosJsonNumbers.truthy(parts.getJSONObject(index).opt("paid"))) return out
        val parsed = parse(input.getString("raw")) ?: return out
        val total = input.getDouble("total")
        require(total.isFinite())
        val totalCents = cents(total).coerceAtLeast(0.0)
        val paid = (0 until parts.length()).filter { MPosJsonNumbers.truthy(parts.getJSONObject(it).opt("paid")) }
        val previous = (0 until index).filter { it !in paid }
        fun sum(indices: List<Int>): Double = indices.sumOf { i ->
            val value = parts.getJSONObject(i).opt("amount")
            cents(MPosJsonNumbers.number(if (MPosJsonNumbers.truthy(value)) value else 0))
        }
        val paidCents = sum(paid); val previousCents = sum(previous)
        require(listOf(totalCents, paidCents, previousCents).all { it.isFinite() && kotlin.math.abs(it) <= 9_007_199_254_740_990.0 })
        val maxRequested = (totalCents - paidCents - previousCents).coerceAtLeast(0.0)
        val requested = minOf(cents(parsed), maxRequested)
        val result = JSONArray(parts.toString())
        result.getJSONObject(index).put("amount", requested / 100).put("cashGiven", JSONObject.NULL).put("change", JSONObject.NULL)
        val following = (index + 1 until parts.length()).filter { it !in paid }
        val targets: List<Int>
        val remaining: Double
        if (following.isNotEmpty()) {
            targets = following; remaining = (totalCents - paidCents - previousCents - requested).coerceAtLeast(0.0)
        } else {
            targets = (0 until parts.length()).filter { it != index && it !in paid }
            remaining = (totalCents - paidCents - requested).coerceAtLeast(0.0)
        }
        if (targets.isNotEmpty()) {
            val base = floor(remaining / targets.size); val remainder = remaining - base * targets.size
            targets.forEachIndexed { position, target -> result.getJSONObject(target).put("amount", (base + if (position < remainder) 1 else 0) / 100) }
        }
        return out.put("changed", true).put("parts", result).put("index", index)
    }
}
