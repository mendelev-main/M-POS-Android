package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import com.mendelev.mpos.telegram.TelegramSettings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceTelegramSettingsTest {
    private val backend=BackendSettings(url="https://fixture.example.org",deviceKey="fixture-key",telegramSettingsPath="/custom/telegram")
    private val telegram=TelegramSettings(enabled=true,onlineOrders=true,deviceChatId="1234567890123456789",botToken="123456:"+"A".repeat(32),chatId="-100123")
    @Test fun usesDeviceKeyAndWorkDeviceRecipientWithoutSendingBotTokenOrOwnerData() {
        val requests=mutableListOf<JSONObject>()
        val http=BackendHttp(OkHttpClient.Builder().addInterceptor {c->
            val r=c.request();assertEquals("PUT",r.method);assertEquals("/custom/telegram",r.url.encodedPath)
            assertEquals("fixture-key",r.header("X-Device-Key"))
            val b=Buffer();r.body!!.writeTo(b);requests+=JSONObject(b.readUtf8())
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("ok").body("{\"ok\":true}".toResponseBody("application/json".toMediaType())).build()
        }.build())
        DeviceTelegramSettings(http).sync(backend,telegram)
        DeviceTelegramSettings(http).sync(backend,telegram.copy(onlineOrders=false))
        assertEquals(setOf("chatId","enabled"),requests.first().keys().asSequence().toSet())
        assertEquals(telegram.deviceChatId,requests.first().getString("chatId"));assertTrue(requests.first().getBoolean("enabled"))
        assertFalse(requests.last().getBoolean("enabled"));assertFalse(requests.first().toString().contains(telegram.botToken))
        assertEquals(backend,BackendSettingsCodec.decode(BackendSettingsCodec.encode(backend)))
        val legacy=JSONObject(BackendSettingsCodec.encode(backend));legacy.remove("telegramSettingsPath")
        assertEquals("/api/device/telegram-settings",BackendSettingsCodec.decode(legacy.toString()).telegramSettingsPath)
    }
    @Test fun untouchedLegacySettingsDoNotDisableWebAndExplicitDisableSurvivesRestart() {
        assertFalse(DeviceTelegramSettings.shouldSync(TelegramSettings(revision=1)))
        assertTrue(DeviceTelegramSettings.shouldSync(telegram))
        val disabled=telegram.copy(onlineOrders=false,onlineOrdersConfigured=true)
        val restored=com.mendelev.mpos.telegram.TelegramSettingsCodec.decode(com.mendelev.mpos.telegram.TelegramSettingsCodec.encode(disabled))
        assertTrue(DeviceTelegramSettings.shouldSync(restored));assertFalse(restored.onlineOrders)
    }
    @Test fun rejectionDoesNotLookLikeSuccess() {
        val http=BackendHttp(OkHttpClient.Builder().addInterceptor {c->Response.Builder().request(c.request()).protocol(Protocol.HTTP_1_1).code(403).message("forbidden").body("{}".toResponseBody("application/json".toMediaType())).build()}.build())
        assertThrows(BackendFailure::class.java) {DeviceTelegramSettings(http).sync(backend,telegram)}
    }
}
