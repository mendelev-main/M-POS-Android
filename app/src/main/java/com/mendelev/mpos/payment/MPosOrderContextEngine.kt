package com.mendelev.mpos.payment

import org.json.JSONObject

/** Local settings only. Customer identity, loyalty and comment are not reassigned. */
object MPosOrderContextEngine {
    private fun whitespace(c: Char) = c in '\u0009'..'\u000d' || c == '\u0020' || c == '\u00a0' || c == '\u1680' ||
        c in '\u2000'..'\u200a' || c == '\u2028' || c == '\u2029' || c == '\u202f' ||
        c == '\u205f' || c == '\u3000' || c == '\ufeff'
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        require(input.getString("operation") == "save")
        val fields = input.getJSONObject("fields")
        fun field(key: String): String {
            val value = fields.get(key)
            require(value is String)
            return value.trim(::whitespace)
        }
        return JSONObject().put("ok", true).put("authoritative", true).put("source", "native-order-context")
            .put("orderLabel", field("label")).put("name", field("name"))
            .put("phone", field("phone")).put("address", field("address"))
    }
}
