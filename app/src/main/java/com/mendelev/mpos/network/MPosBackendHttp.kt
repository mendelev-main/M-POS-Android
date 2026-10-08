package com.mendelev.mpos.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A closed list of POS backend routes; no browser CORS dependency or transport retries. */
class MPosBackendHttp(client: OkHttpClient = OkHttpClient()) {
    val client = client.newBuilder().callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    fun request(payload: JSONObject): Request {
        val base = payload.getString("backendUrl").trim().trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty())
        val kind = payload.getString("kind")
        val route = when (kind) {
            "health" -> "health"
            "eventsProbe" -> "api/orders/events"
            "testOrder" -> "api/orders/test"
            "telegramSettings" -> "api/device/telegram-settings"
            "liveReport" -> {
                val id = payload.getString("reportId")
                require(Regex("^[A-Za-z0-9_-]{20,100}$").matches(id))
                "api/device/live-report/$id"
            }
            else -> throw IllegalArgumentException("Unsupported backend route")
        }
        val builder = Request.Builder().url(base.newBuilder().addPathSegments(route).build())
            .header("Cache-Control", "no-cache, no-store")
        if (kind != "health") {
            val key = payload.getString("deviceKey")
            require(key.isNotBlank())
            builder.header("X-Device-Key", key)
        }
        if (kind == "eventsProbe") builder.header("Accept", "text/event-stream")
        when (kind) {
            "health", "eventsProbe" -> builder.get()
            "telegramSettings" -> builder.put(payload.getString("body").toRequestBody("application/json".toMediaType()))
            else -> builder.post(payload.getString("body").toRequestBody("application/json".toMediaType()))
        }
        return builder.build()
    }

    fun decode(response: Response, kind: String): JSONObject {
        // The stream is intentionally closed after headers; never wait for an infinite SSE body.
        val stream = kind == "eventsProbe"
        val validStream = response.header("Content-Type", "")!!.substringBefore(';').trim().equals("text/event-stream", true)
        return JSONObject().put("ok", true).put("authoritative", true)
            .put("httpOk", response.isSuccessful && (!stream || validStream))
            .put("httpStatus", response.code)
            .put("data", if (stream) JSONObject().put("streamAvailable", validStream) else MPosHttpJson.read(response))
    }
}
