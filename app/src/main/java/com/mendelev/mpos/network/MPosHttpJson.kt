package com.mendelev.mpos.network

import android.util.JsonReader
import android.util.JsonToken
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener
import java.io.StringReader

/** Matches response.json().catch(() => null), including strict scalar JSON. */
object MPosHttpJson {
    fun read(response: Response): Any {
        val raw = response.body?.string() ?: return JSONObject.NULL
        return try {
            JsonReader(StringReader("[$raw]")).use { reader ->
                reader.isLenient = false
                reader.beginArray(); reader.skipValue(); reader.endArray()
                require(reader.peek() == JsonToken.END_DOCUMENT)
            }
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') }
        } catch (_: Exception) { JSONObject.NULL }
    }
}
