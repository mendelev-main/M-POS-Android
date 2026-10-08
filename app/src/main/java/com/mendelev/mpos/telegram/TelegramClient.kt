package com.mendelev.mpos.telegram

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

class TelegramClient(
    private val createWarehousePdf: (JSONObject) -> File,
    private val onResult: (Boolean, String) -> Unit,
    private val onMonthlyResult: (JSONObject) -> Unit,
    private val onShiftResult: (Boolean, String) -> Unit,
    private val onTestResult: ((JSONObject) -> Unit)? = null,
) {
    private val executor = Executors.newSingleThreadExecutor()

    fun handle(payload: JSONObject) {
        val action = payload.optString("action")
        val requestId = payload.optString("requestId")
        val result: (Boolean, String) -> Unit = { ok, message ->
            if (action == "test" && requestId.isNotBlank() && onTestResult != null)
                onTestResult.invoke(JSONObject().put("requestId", requestId).put("ok", ok).put("message", message))
            else onResult(ok, message)
        }
        val token = payload.optString("botToken").trim()
        val chatId = payload.optString("chatId").trim()
        if (token.isBlank() || chatId.isBlank()) {
            if (action == "sendShiftCloseReport") onShiftResult(false, "Укажите токен бота и ID рабочей группы")
            else result(false, "Укажите токен бота и ID рабочей группы")
            return
        }
        if (action == "sendShiftCloseReport") {
            val report = payload.optJSONObject("report")
            if (report == null || report.length() == 0) return onShiftResult(false, "Не заполнен отчёт о закрытии смены")
            executor.execute {
                runCatching {
                    val png = MPosShiftReceiptImage.render(report)
                    sendPhoto(token, chatId, payload.optString("threadId"), png, MPosShiftReceipt.caption(report))
                }.onSuccess { onShiftResult(true, "Чек закрытия смены отправлен в Telegram") }
                    .onFailure { onShiftResult(false, "Не удалось отправить изображение закрытия смены в Telegram") }
            }
            return
        }
        if (action == "sendMonthlyWarehouseReport") {
            val periodKey = payload.optString("periodKey")
            val report = payload.optJSONObject("report")
            if (periodKey.isBlank() || report == null) return onMonthlyResult(JSONObject().put("ok", false).put("message", "Не заполнен складской отчёт").put("periodKey", periodKey))
            executor.execute {
                runCatching {
                    val file = createWarehousePdf(report)
                    sendDocument(token, chatId, payload.optString("threadId"), file,
                        "📊 <b>Ежемесячный складской отчёт</b>\nПериод: ${escape(report.optString("period", periodKey))}")
                }.onSuccess {
                    onMonthlyResult(JSONObject().put("ok", true).put("message", "Ежемесячный складской отчёт отправлен").put("periodKey", periodKey))
                }.onFailure {
                    onMonthlyResult(JSONObject().put("ok", false).put("message", it.message ?: "Ошибка Telegram").put("periodKey", periodKey))
                }
            }
            return
        }
        val text = when (action) {
            "test" -> "🟢 <b>Telegram подключён</b>\nM POS Android успешно связался с рабочей группой."
            "send" -> payload.optString("text")
            else -> return result(false, "Эта Telegram-команда ещё не перенесена на Android")
        }
        executor.execute {
            runCatching { send(token, chatId, payload.optString("threadId"), text) }
                .onSuccess { result(true, if (action == "test") "Telegram подключён" else "Отчёт отправлен") }
                .onFailure { result(false, MPosTelegramFailure.message(it)) }
        }
    }

    private fun send(token: String, chatId: String, threadId: String, text: String) {
        val fields = linkedMapOf("chat_id" to chatId, "text" to text, "parse_mode" to "HTML")
        if (threadId.isNotBlank()) fields["message_thread_id"] = threadId
        val body = fields.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }.toByteArray()
        val connection = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(body) }
            val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299 || !JSONObject(response.ifBlank { "{}" }).optBoolean("ok")) throw MPosTelegramFailure.fromResponse(connection.responseCode, response)
        } finally { connection.disconnect() }
    }

    private fun sendDocument(token: String, chatId: String, threadId: String, file: File, caption: String) {
        val boundary = "MPos-${UUID.randomUUID()}"
        val connection = URL("https://api.telegram.org/bot$token/sendDocument").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.outputStream.buffered().use { output ->
            fun field(name: String, value: String) {
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("chat_id", chatId)
            if (threadId.isNotBlank()) field("message_thread_id", threadId)
            field("caption", caption)
            field("parse_mode", "HTML")
            output.write("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"\r\nContent-Type: application/pdf\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(output) }
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299 || !JSONObject(response.ifBlank { "{}" }).optBoolean("ok")) error("Telegram HTTP $code")
    }

    private fun sendPhoto(token: String, chatId: String, threadId: String, png: ByteArray, caption: String) {
        val boundary = "MPos-${UUID.randomUUID()}"
        val connection = URL("https://api.telegram.org/bot$token/sendPhoto").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.buffered().use { MPosTelegramPhoto.write(it, boundary, chatId, threadId, caption, png) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || !JSONObject(response.ifBlank { "{}" }).optBoolean("ok")) error("Telegram photo rejected")
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
