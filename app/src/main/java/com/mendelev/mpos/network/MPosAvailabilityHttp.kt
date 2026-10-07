package com.mendelev.mpos.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MPosAvailabilityHttp {
    val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
        .callTimeout(30,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    fun request(ticket:JSONObject):Request {
        val network=ticket.getJSONObject("network");val base=network.getString("backendUrl").trim().trimEnd('/').toHttpUrl();require(base.isHttps)
        val body=ticket.getJSONObject("body")
        return Request.Builder().url(base.newBuilder().addPathSegments("api/availability/snapshot").build())
            .header("X-Device-Key",network.getString("deviceKey")).header("Cache-Control","no-cache, no-store")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
    }
}
