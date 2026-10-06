package com.mendelev.mpos.data

import org.json.JSONObject

/** Keep UTF-16 string contents intact across JavaScript transport, including legacy lone surrogates. */
object MPosBridgeJson {
    fun serialize(value: JSONObject): String {
        val raw=value.toString()
        return buildString(raw.length) {
            for(c in raw)if(c.code in 0xD800..0xDFFF)append("\\u").append(c.code.toString(16).padStart(4,'0')) else append(c)
        }
    }
}
