package com.mendelev.mpos.telegram

import org.json.JSONObject

/** Fixed user-facing messages: never echo Telegram payloads, token URLs or exceptions. */
class MPosTelegramFailure(val safeMessage: String) : Exception(safeMessage) {
    companion object {
        fun fromResponse(status: Int, raw: String): MPosTelegramFailure {
            val description = runCatching { JSONObject(raw).optString("description").lowercase() }.getOrDefault("")
            val message = when {
                status == 401 || status == 404 -> "Telegram отклонил токен бота (HTTP $status). Проверьте актуальный токен"
                status == 403 -> "Боту запрещено писать в этот чат (HTTP 403). Добавьте бота в группу или нажмите Start в личном чате"
                status == 400 && "thread" in description -> "Не найдена тема Telegram (HTTP 400). Проверьте ID темы или оставьте его пустым"
                status == 400 && "chat" in description -> "Не найден чат Telegram (HTTP 400). Проверьте ID группы и участие бота"
                status == 429 -> "Telegram временно ограничил отправку (HTTP 429). Повторите проверку позже"
                else -> "Telegram отклонил сообщение (HTTP $status). Проверьте группу и права бота"
            }
            return MPosTelegramFailure(message)
        }
        fun message(error: Throwable): String = when (error) {
            is MPosTelegramFailure -> error.safeMessage
            is java.net.UnknownHostException -> "Не найден адрес Telegram. Проверьте интернет и DNS на планшете"
            is javax.net.ssl.SSLException -> "Не удалось проверить HTTPS сертификат Telegram"
            is java.net.SocketTimeoutException -> "Telegram не ответил вовремя. Проверьте доступ к Telegram с планшета"
            else -> "Не удалось подключиться к Telegram. Проверьте интернет на планшете"
        }
    }
}
