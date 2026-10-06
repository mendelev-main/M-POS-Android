package com.mendelev.mpos.backup

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class MPosBackupInputTest {
    @Test fun preservesUtf8BackupBytesAndExactLimit() {
        val bytes = "{\"version\":13,\"products\":[{\"name\":\"Кофе ☕\"}]}".toByteArray(Charsets.UTF_8)
        assertArrayEquals(bytes, MPosBackupInput.read(ByteArrayInputStream(bytes), bytes.size))
        assertArrayEquals(byteArrayOf(), MPosBackupInput.read(ByteArrayInputStream(byteArrayOf()), 0))
    }

    @Test fun oversizedUnknownLengthStreamStopsAtLimitPlusOneWithoutClosingCallerStream() {
        var consumed = 0
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 42 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(42, off, off + len)
                consumed += len
                return len
            }
            override fun close() { closed = true }
        }
        val error = assertThrows(IllegalArgumentException::class.java) { MPosBackupInput.read(stream, 8193) }
        assertEquals("Файл резервной копии больше 500 МБ", error.message)
        assertEquals(8194, consumed)
        assertFalse(closed)
    }

    @Test fun shortAndZeroReadsDoNotTruncateOrSpin() {
        val bytes = ByteArray(20_000) { (it % 251).toByte() }
        val source = ByteArrayInputStream(bytes)
        var calls = 0
        val stream = object : InputStream() {
            override fun read() = source.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                if (++calls % 2 == 1) 0 else source.read(b, off, minOf(len, 7))
        }
        assertArrayEquals(bytes, MPosBackupInput.read(stream, bytes.size))
    }

    @Test fun interruptedReadPropagatesOriginalFailureWithoutReturningPartialBackup() {
        val failure = IOException("provider disconnected")
        val stream = object : InputStream() {
            var delivered = false
            override fun read(): Int = throw failure
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (delivered) throw failure
                delivered = true
                b[off] = 1
                return 1
            }
        }
        assertSame(failure, assertThrows(IOException::class.java) { MPosBackupInput.read(stream, 100) })
    }

    @Test fun zeroReadFallbackStillRejectsAnOversizedFile() {
        val stream = object : InputStream() {
            override fun read() = 1
            override fun read(b: ByteArray, off: Int, len: Int) = 0
        }
        assertThrows(IllegalArgumentException::class.java) { MPosBackupInput.read(stream, 0) }
    }
}
