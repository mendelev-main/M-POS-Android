package com.mendelev.mpos.network

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class NativeNetworkTransport(
    private val scope: LifecycleCoroutineScope,
    private val onResult: (JSONObject) -> Unit,
    private val onEvent: (JSONObject) -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
.readTimeout(15, TimeUnit.SECONDS)
.build()

    fun handle(payload: JSONObject) {
        val requestId = payload.optString("requestId")
        when (payload.optString("action")) {
            "describe" -> onResult(JSONObject().put("requestId", requestId).put("ok", true).put("transport", "okhttp").put("authoritative", false).put("sseEnabled", false).put("businessHandlers", "legacy"))
            "probe" -> probe(requestId, payload)
            else -> result(requestId, false, "unsupported native network action")
        }
    }

    private fun probe(requestId: String, payload: JSONObject) {
        val backendUrl = payload.optString("backendUrl").trim().trimEnd('/')
        val deviceKey = payload.optString("deviceKey")
        if (!backendUrl.startsWith("https://")) { result(requestId, false, "HTTPS backend URL is required"); return }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val encodedKey = URLEncoder.encode(deviceKey, "UTF-8")
                val request = Request.Builder().url(backendUrl + "/api/orders/events?deviceKey=" + encodedKey).header("Accept", "text/event-stream").get().build()
                client.newCall(request).execute().use { response -> JSONObject().put("requestId", requestId).put("ok", response.isSuccessful).put("httpStatus", response.code).put("contentType", response.header("Content-Type", "")).put("authoritative", false) }
            }.onSuccess(onResult).onFailure { result(requestId, false, it.localizedMessage ?: "native network probe failed") }
        }
    }

    private fun result(requestId: String, ok: Boolean, message: String) = onResult(JSONObject().put("requestId", requestId).put("ok", ok).put("message", message).put("authoritative", false))
}