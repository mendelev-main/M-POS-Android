package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Read-only quantity decision and whole-cart stock preflight; the adapter saves the accepted cart. */
class MPosCartQuantityRepository(private val database: MPosDatabase) {
    companion object {
        fun quantity(current: Any?, delta: Any?): Any {
            // Retain JS addition for legacy numeric strings, rather than silently normalizing backups.
            if (current is String || delta is String) {
                fun text(value: Any?): String = when (value) {
                    null, JSONObject.NULL -> "null"
                    is Number -> {
                        val n = value.toDouble()
                        if (n == 0.0) "0" else if (kotlin.math.abs(n) >= 1e-6 && kotlin.math.abs(n) < 1e21)
                            java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
                        else n.toString().lowercase().replace(Regex("\\.0e"), "e").replace(Regex("e(?!-)"), "e+")
                    }
                    else -> value.toString()
                }
                return text(current) + text(delta)
            }
            return MPosJsonNumbers.number(current) + MPosJsonNumbers.number(delta)
        }
    }
    suspend fun read(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw); val input = parser.nextValue()
        require(input is JSONObject && parser.nextClean() == '\u0000' && input.getInt("version") == 1)
        val items = JSONArray(input.getJSONArray("items").toString())
        val target = input.getInt("targetIndex"); require(target in 0 until items.length())
        val item = items.getJSONObject(target); require(item.has("qty") && input.has("delta"))
        val next = quantity(item.opt("qty"), input.opt("delta"))
        val numeric = MPosJsonNumbers.number(next); require(numeric.isFinite()) { "invalid cart quantity" }
        val matches = input.getJSONArray("matchingIndices")
        val indices = (0 until matches.length()).map { matches.getInt(it) }
        require(target in indices && indices.toSet().size == indices.size && indices.all { it in 0 until items.length() })
        val result = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-cart-quantity")
            .put("quantity", next).put("remove", numeric <= 0)
        if (numeric <= 0) result.put("allowed", true)
        else {
            for (index in indices) items.getJSONObject(index).put("qty", next)
            val verdict = MPosStockPreflightRepository(database).read(JSONObject().put("version", 1).put("items", items).toString())
            result.put("allowed", verdict.getBoolean("allowed"))
            if (verdict.has("reason")) result.put("reason", verdict.getString("reason"))
        }
        result
    }
}
