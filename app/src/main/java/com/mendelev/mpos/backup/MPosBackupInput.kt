package com.mendelev.mpos.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Enforces the existing backup limit before accumulating bytes beyond it. Caller owns the stream. */
object MPosBackupInput {
    const val MAX_BYTES = 500_000_000

    fun read(input: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        require(maxBytes >= 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            // Only one extra byte is needed to distinguish an exact-limit file from an oversized one.
            val requested = minOf(buffer.size.toLong(), maxBytes.toLong() - total + 1).toInt()
            val count = input.read(buffer, 0, requested)
            if (count < 0) break
            if (count == 0) {
                // Some document providers return zero: make progress without a busy loop.
                val next = input.read()
                if (next < 0) break
                require(total < maxBytes) { "Файл резервной копии больше 500 МБ" }
                output.write(next)
                total++
                continue
            }
            require(count.toLong() <= maxBytes.toLong() - total) { "Файл резервной копии больше 500 МБ" }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }
}
