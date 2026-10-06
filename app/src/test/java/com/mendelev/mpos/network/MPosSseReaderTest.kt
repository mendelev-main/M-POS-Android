package com.mendelev.mpos.network

import java.io.IOException
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class MPosSseReaderTest {
    private fun reader(text: String, lineLimit: Int = 1024, eventLimit: Int = 1024) =
        MPosSseReader(Buffer().writeUtf8(text), lineLimit, eventLimit)

    @Test fun preservesAllButOneAsciiSpaceAndJoinsMultilineData() {
        val input = reader("data:  leading\ndata:\tkeep-tab\ndata: last  \n\n")
        assertEquals(" leading\n\tkeep-tab\nlast  ", input.nextMessage())
        assertNull(input.nextMessage())
    }

    @Test fun lfCrLfAndCrSeparatorsProduceIdenticalFrames() {
        for (separator in listOf("\n", "\r\n", "\r")) {
            val input = reader(listOf("data: first", "", "data: second", "", "").joinToString(separator))
            assertEquals("first", input.nextMessage())
            assertEquals("second", input.nextMessage())
            assertNull(input.nextMessage())
        }
    }

    @Test fun emptyDataAndColonlessDataDispatchButCommentsDoNot() {
        val input = reader(": heartbeat\n\ndata:\n\ndata\n\ndata:\ndata:\n\n")
        assertEquals("", input.nextMessage())
        assertEquals("", input.nextMessage())
        assertEquals("\n", input.nextMessage())
        assertNull(input.nextMessage())
    }

    @Test fun namedEventsAreExcludedFromLegacyMessageObserverAndTypeResets() {
        val input = reader("event: ready\ndata: ignored\n\nevent: message\ndata: visible\n\ndata: default\n\n")
        assertEquals("visible", input.nextMessage())
        assertEquals("default", input.nextMessage())
        assertNull(input.nextMessage())
    }

    @Test fun eventWithoutDataAndUnknownFieldsDoNotDispatchOrContaminateNextFrame() {
        val input = reader("event: named\nid: example\nretry: 100\nunknown: value\n\ndata: visible\n\n")
        assertEquals("visible", input.nextMessage())
        assertNull(input.nextMessage())
    }

    @Test fun eofDiscardsUnterminatedFrameWithOrWithoutTrailingLineBreak() {
        for (suffix in listOf("", "\n", "\r\n", "\r")) {
            assertNull(reader("data: incomplete$suffix").nextMessage())
        }
        val input = reader("data: complete\n\ndata: incomplete")
        assertEquals("complete", input.nextMessage())
        assertNull(input.nextMessage())
    }

    @Test fun fragmentedUtf8AndCrLfHandleInitialBomOnlyOnce() {
        val bytes = Buffer().writeUtf8("\uFEFFdata: Кофе ☕\r\n\r\ndata: \uFEFFsecond\n\n")
        val fragmented = object : Source {
            override fun read(sink: Buffer, byteCount: Long) = bytes.read(sink, minOf(1L, byteCount))
            override fun timeout() = Timeout.NONE
            override fun close() {}
        }.buffer()
        val input = MPosSseReader(fragmented)
        assertEquals("Кофе ☕", input.nextMessage())
        assertEquals("\uFEFFsecond", input.nextMessage())
        assertNull(input.nextMessage())
    }

    @Test fun byteLineBoundIsInclusiveAndChecksUnknownLinesToo() {
        assertEquals("a", reader("data:a\n\n", 6).nextMessage())
        assertThrows(IOException::class.java) { reader("data:aa\n\n", 6).nextMessage() }
        assertThrows(IOException::class.java) { reader(":1234567\n\n", 6).nextMessage() }
        assertThrows(IOException::class.java) { reader("data:☕\n\n", 7).nextMessage() }
    }

    @Test fun multilineFrameBoundIncludesInsertedNewlines() {
        assertEquals("a\nb", reader("data:a\ndata:b\n\n", eventLimit = 4).nextMessage())
        assertThrows(IOException::class.java) { reader("data:a\ndata:bb\n\n", eventLimit = 4).nextMessage() }
    }

    @Test fun sourceFailurePropagatesWithoutReturningPartialData() {
        val error = IOException("synthetic interrupted stream")
        val source = object : Source {
            var sent = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (sent) throw error
                sent = true
                return sink.writeUtf8("data: partial\n").size
            }
            override fun timeout() = Timeout.NONE
            override fun close() {}
        }.buffer()
        assertSame(error, assertThrows(IOException::class.java) { MPosSseReader(source).nextMessage() })
    }
}
