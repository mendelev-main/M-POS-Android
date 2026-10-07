package com.mendelev.mpos.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Read-only central loyalty profile: no retries, no offline eligibility cache. */
class MPosLoyaltyProfileHttp(client: OkHttpClient = OkHttpClient()) {
    val client = client.newBuilder().callTimeout(5000, TimeUnit.MILLISECONDS)
        .connectTimeout(5000, TimeUnit.MILLISECONDS).readTimeout(5000, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false).build()

    fun request(payload: JSONObject): Request {
        val base = payload.getString("backendUrl").trim().trimEnd('/').toHttpUrl()
        require(base.isHttps) { "HTTPS backend URL required" }
        val key = payload.getString("deviceKey")
        require(key.isNotBlank()) { "device key required" }
        val url = base.newBuilder().addPathSegments("api/customers")
            .addPathSegment(payload.getString("customerId")).addPathSegment("loyalty").build()
        return Request.Builder().url(url).header("Content-Type", "application/json")
            .header("X-Device-Key", key).header("Cache-Control", "no-cache, no-store").get().build()
    }

    fun mutation(payload: JSONObject): Request {
        val base = payload.getString("backendUrl").trim().trimEnd('/').toHttpUrl()
        require(base.isHttps)
        val key = payload.getString("deviceKey"); require(key.isNotBlank())
        val route = when(payload.getString("kind")) { "sale" -> "sales"; "reversal" -> "reversal"; else -> throw IllegalArgumentException("unsupported loyalty mutation") }
        val url = base.newBuilder().addPathSegments("api/loyalty").addPathSegment(route).build()
        return Request.Builder().url(url).header("X-Device-Key",key).header("Cache-Control","no-cache, no-store")
            .post(payload.getJSONObject("body").toString().toRequestBody("application/json".toMediaType())).build()
    }

    fun decode(response: Response): JSONObject {
        val data = MPosHttpJson.read(response)
        if (!response.isSuccessful) {
            val error = (data as? JSONObject)?.opt("error")
            throw IllegalStateException(if (com.mendelev.mpos.data.MPosJsonNumbers.truthy(error)) error.toString() else "HTTP ${response.code}")
        }
        return JSONObject().put("ok", true).put("authoritative", true).put("source", "native-loyalty-profile")
            .put("data", data).put("httpStatus", response.code)
    }
}
