package com.mendelev.mpos.backend

import com.mendelev.mpos.telegram.TelegramSettings
import org.json.JSONObject

/** Configure the existing server bot; never send a bot token or a notification from POS. */
internal class DeviceTelegramSettings(private val http: BackendHttp=BackendHttp()) {
    companion object {
        // An untouched legacy/default switch must not disable the Web device on a shared key.
        fun shouldSync(s: TelegramSettings)=s.onlineOrders || s.onlineOrdersConfigured
    }
    fun sync(backend: BackendSettings,telegram: TelegramSettings) {
        val s=telegram.validated()
        val result=http.json(backend,backend.telegramSettingsPath,"PUT",JSONObject()
            .put("chatId",s.deviceChatId).put("enabled",s.enabled && s.onlineOrders))
        check(result.optBoolean("ok")) {"Backend не подтвердил настройки уведомлений рабочего устройства"}
    }
}
