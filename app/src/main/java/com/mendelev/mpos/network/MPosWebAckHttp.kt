package com.mendelev.mpos.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** ACK transport only; journal eligibility is established before this call. */
class MPosWebAckHttp(client:OkHttpClient=OkHttpClient()) {
    val client=client.newBuilder().callTimeout(30,TimeUnit.SECONDS).connectTimeout(30,TimeUnit.SECONDS)
        .readTimeout(30,TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
    fun request(p:JSONObject):Request {
        val base=p.getString("backendUrl").trim().trimEnd('/').toHttpUrl();require(base.isHttps)
        val key=p.getString("deviceKey");require(key.isNotBlank())
        val route=when(p.getString("key")){"webOrderAcceptances"->"accept";"webOrderReadyJournal"->"ready";else->throw IllegalArgumentException("unsupported WEB ACK")}
        val url=base.newBuilder().addPathSegments("api/orders").addPathSegment(p.getString("id")).addPathSegment(route).build()
        return Request.Builder().url(url).header("X-Device-Key",key).header("Cache-Control","no-cache, no-store")
            .post(p.getJSONObject("body").toString().toRequestBody("application/json".toMediaType())).build()
    }
}
