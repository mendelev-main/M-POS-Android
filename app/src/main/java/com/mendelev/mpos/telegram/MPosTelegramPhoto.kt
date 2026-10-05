package com.mendelev.mpos.telegram

import java.io.OutputStream

object MPosTelegramPhoto {
    fun write(output: OutputStream, boundary: String, chatId: String, threadId: String, caption: String, png: ByteArray) {
        fun text(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
        fun field(name: String, value: String) = text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        field("chat_id", chatId)
        threadId.toLongOrNull()?.takeIf { it > 0 }?.let { field("message_thread_id", it.toString()) }
        field("caption", caption)
        field("parse_mode", "HTML")
        text("--$boundary\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"shift-report.png\"\r\nContent-Type: image/png\r\n\r\n")
        output.write(png)
        text("\r\n--$boundary--\r\n")
    }
}
