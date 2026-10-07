package com.mendelev.mpos.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Manual catalogue and product-save media requests only; never schedules work. */
class MPosCatalogHttp(client: OkHttpClient = OkHttpClient()) {
    val menuClient = client.newBuilder().callTimeout(60,TimeUnit.SECONDS).connectTimeout(10,TimeUnit.SECONDS)
        .readTimeout(60,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    val mediaClient = client.newBuilder().callTimeout(10,TimeUnit.SECONDS).connectTimeout(10,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    fun request(payload: JSONObject): Request {
        val base = payload.getString("backendUrl").trim().trimEnd('/').toHttpUrl()
        require(base.isHttps)
        val key = payload.getString("deviceKey");require(key.isNotBlank())
        val route = when(payload.getString("kind")) {
            "menu" -> "api/menu/sync"
            "media" -> "api/media/upload"
            else -> throw IllegalArgumentException("unsupported catalogue request")
        }
        return Request.Builder().url(base.newBuilder().addPathSegments(route).build())
            .header("X-Device-Key",key).header("Cache-Control","no-cache, no-store")
            .post(payload.getString("body").toRequestBody("application/json".toMediaType())).build()
    }
    fun decode(response: Response) = JSONObject().put("ok",true).put("authoritative",true)
        .put("httpOk",response.isSuccessful).put("httpStatus",response.code).put("data",MPosHttpJson.read(response))
}
