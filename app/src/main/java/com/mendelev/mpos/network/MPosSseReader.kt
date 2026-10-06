package com.mendelev.mpos.network

import okio.Buffer
import okio.BufferedSource
import java.io.IOException

/** Diagnostic message frames with EventSource data semantics; no ACK or business dispatch. */
class MPosSseReader(
    private val source: BufferedSource,
    private val maxLineBytes: Int = 1_048_576,
    private val maxEventChars: Int = 1_048_576,
) {
    private var skipLf = false
    private var firstLine = true

    init { require(maxLineBytes > 0 && maxEventChars > 0) }

    fun nextMessage(): String? {
        val data = StringBuilder()
        var hasData = false
        var eventType = ""
        while (true) {
            var line = readLine() ?: return null // EOF never dispatches an unfinished frame.
            if (firstLine) { line = line.removePrefix("\uFEFF"); firstLine = false }
            if (line.isEmpty()) {
                if (hasData && (eventType.isEmpty() || eventType == "message")) {
                    return data.substring(0, data.length - 1)
                }
                data.setLength(0); hasData = false; eventType = ""
                continue
            }
            if (line.startsWith(':')) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            var value = if (colon < 0) "" else line.substring(colon + 1)
            // The protocol removes exactly one ASCII space, not arbitrary whitespace.
            if (value.startsWith(' ')) value = value.substring(1)
            when (field) {
                "data" -> {
                    if (data.length.toLong() + value.length + 1 > maxEventChars.toLong()) {
                        throw IOException("SSE diagnostic frame exceeds size limit")
                    }
                    data.append(value).append('\n'); hasData = true
                }
                "event" -> eventType = value
                // id/retry do not affect diagnostic-only message.data observation.
            }
        }
    }

    private fun readLine(): String? {
        val line = Buffer()
        while (!source.exhausted()) {
            val byte = source.readByte().toInt() and 255
            if (skipLf) { skipLf = false; if (byte == 10) continue }
            if (byte == 13) { skipLf = true; return line.readUtf8() }
            if (byte == 10) return line.readUtf8()
            if (line.size >= maxLineBytes) throw IOException("SSE diagnostic line exceeds size limit")
            line.writeByte(byte)
        }
        return if (line.size == 0L) null else line.readUtf8()
    }
}
