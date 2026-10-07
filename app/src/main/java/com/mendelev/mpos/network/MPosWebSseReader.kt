package com.mendelev.mpos.network

import okio.BufferedSource

/** EventSource framing; the diagnostic reader deliberately remains independent. */
class MPosWebSseReader(private val source: BufferedSource, initialId: String = "", private val onRetry: (Long) -> Unit = {}) {
    data class Event(val type: String, val data: String, val id: String)
    var lastEventId = initialId
        private set
    var retryMillis: Long? = null
        private set
    private var first = true
    private var skipLf = false

    fun next(): Event? {
        val data = StringBuilder()
        var hasData = false
        var type = ""
        var pendingId = lastEventId
        while (true) {
            var line = line() ?: return null // EOF never dispatches an unfinished event.
            if (first) { line = line.removePrefix("\uFEFF"); first = false }
            if (line.isEmpty()) {
                lastEventId = pendingId
                if (hasData) return Event(type.ifEmpty { "message" }, data.dropLast(1).toString(), lastEventId)
                type = ""
                continue
            }
            if (line.startsWith(':')) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            var value = if (colon < 0) "" else line.substring(colon + 1)
            if (value.startsWith(' ')) value = value.substring(1)
            when (field) {
                "data" -> { data.append(value).append('\n'); hasData = true }
                "event" -> type = value
                "id" -> if (!value.contains('\u0000')) pendingId = value
                "retry" -> if (value.isNotEmpty() && value.all { it in '0'..'9' }) value.toLongOrNull()?.let { retryMillis = it; onRetry(it) }
            }
        }
    }

    private fun line(): String? {
        val bytes = okio.Buffer()
        while (!source.exhausted()) {
            val byte = source.readByte().toInt() and 255
            if (skipLf) { skipLf = false; if (byte == 10) continue }
            if (byte == 13) { skipLf = true; return bytes.readUtf8() }
            if (byte == 10) return bytes.readUtf8()
            bytes.writeByte(byte)
        }
        return if (bytes.size > 0) bytes.readUtf8() else null
    }
}
