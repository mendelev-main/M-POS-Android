package com.mendelev.mpos.data

import org.json.JSONObject
import java.math.BigInteger

/** JSON scalar numeric coercion used by the reviewed runtime's Number(...) calculations. */
object MPosJsonNumbers {
    private val decimal = Regex("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
    fun number(value: Any?): Double = when (value) {
        null, JSONObject.NULL -> 0.0
        is Number -> value.toDouble()
        is Boolean -> if (value) 1.0 else 0.0
        is String -> {
            val text = value.trim { it.isWhitespace() || it == '\uFEFF' }
            when {
                text.isEmpty() -> 0.0
                text in setOf("Infinity", "+Infinity", "-Infinity") -> text.toDouble()
                decimal.matches(text) -> text.toDoubleOrNull() ?: Double.NaN
                text.matches(Regex("0[xX][0-9a-fA-F]+")) -> BigInteger(text.substring(2), 16).toDouble()
                text.matches(Regex("0[bB][01]+")) -> BigInteger(text.substring(2), 2).toDouble()
                text.matches(Regex("0[oO][0-7]+")) -> BigInteger(text.substring(2), 8).toDouble()
                else -> Double.NaN
            }
        }
        else -> Double.NaN
    }
    fun truthy(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
        is String -> value.isNotEmpty()
        else -> true
    }
    fun fallback(value: Any?, default: Any = ""): Any = value.takeIf(::truthy) ?: default
    fun amount(record: JSONObject, key: String): Double = number(fallback(record.opt(key), 0))
    fun reportAmount(record: JSONObject, key: String): Double = number(record.opt(key)).let { if (it.isNaN() || it == 0.0) 0.0 else it }
}
