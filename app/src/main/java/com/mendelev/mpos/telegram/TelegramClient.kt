package com.mendelev.mpos.telegram

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class TelegramClient(private val onResult: (Boolean, String) -> Unit) {
    private val executor = Executors.newSingleThreadExecutor()

    fun handle(payload: JSONObject) {
        val token = payload.optString("botToken").trim()
        val chatId = payload.optString("chatId").trim()
        if (token.isBlank() || chatId.isBlank()) return onResult(false, "Укажите токен бота и ID рабочей группы")
        val action = payload.optString("action")
        val text = when (action) {
            "test" -> "🟢 <b>Telegram подключён</b>\nM POS Android успешно связался с рабочей группой."
            "send" -> payload.optString("text")
            "sendShiftCloseReport" -> shiftText(payload.optJSONObject("report") ?: JSONObject())
            else -> return onResult(false, "Эта Telegram-команда ещё не перенесена на Android")
        }
        executor.execute {
            runCatching { send(token, chatId, payload.optString("threadId"), text) }
                .onSuccess { onResult(true, if (action == "test") "Telegram подключён" else "Отчёт отправлен") }
                .onFailure { onResult(false, it.message ?: "Ошибка Telegram") }
        }
    }

    private fun send(token: String, chatId: String, threadId: String, text: String) {
        val fields = linkedMapOf("chat_id" to chatId, "text" to text, "parse_mode" to "HTML")
        if (threadId.isNotBlank()) fields["message_thread_id"] = threadId
        val body = fields.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }.toByteArray()
        val connection = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.outputStream.use { it.write(body) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (connection.responseCode !in 200..299 || !JSONObject(response.ifBlank { "{}" }).optBoolean("ok")) error("Telegram HTTP ${connection.responseCode}")
    }

    private fun shiftText(report: JSONObject): String = buildString {
        append("<b>Смена закрыта · M POS</b>\n")
        append("Сотрудник: ${escape(report.optString("employeeName", "Сотрудник"))}\n")
        append("Заказов: ${report.optInt("count")}\n")
        append("Выручка: ${money(report.optDouble("total"))}\n")
        append("Наличные: ${money(report.optDouble("cash"))}\n")
        append("Карта: ${money(report.optDouble("card"))}\n")
        append("Расхождение: ${money(report.optDouble("difference"))}")
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun money(value: Double) = "%.2f BYN".format(value)
}

