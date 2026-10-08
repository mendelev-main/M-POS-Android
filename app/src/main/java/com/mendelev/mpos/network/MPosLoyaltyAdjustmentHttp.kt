package com.mendelev.mpos.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Exactly one remote adjustment. Credentials exist only in the native HTTPS request, with no automatic retry. */
class MPosLoyaltyAdjustmentHttp(client: OkHttpClient = OkHttpClient()) {
    class Rejected(val status:Int,message:String):IllegalStateException(message)
    val client = client.newBuilder().callTimeout(5000,TimeUnit.MILLISECONDS).connectTimeout(5000,TimeUnit.MILLISECONDS)
        .readTimeout(5000,TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
    fun request(payload:JSONObject, credential:String):Request {
        val base=payload.getString("backendUrl").trim().trimEnd('/').toHttpUrl();require(base.isHttps)
        val key=payload.getString("deviceKey");require(key.isNotBlank())
        val url=base.newBuilder().addPathSegments("api/customers").addPathSegment(payload.getString("customerId")).addPathSegment("loyalty-adjustment").build()
        val body=JSONObject(payload.getJSONObject("body").toString()).put("adminPassword",credential)
        return Request.Builder().url(url).header("Content-Type","application/json").header("X-Device-Key",key)
            .header("Cache-Control","no-cache, no-store").post(body.toString().toRequestBody("application/json".toMediaType())).build()
    }
    fun decode(response:Response, credential:String) {
        val data=MPosHttpJson.read(response)
        if(!response.isSuccessful){
            val error=(data as? JSONObject)?.opt("error")
            val message=if(com.mendelev.mpos.data.MPosJsonNumbers.truthy(error))error.toString() else "HTTP ${response.code}"
            throw Rejected(response.code,if(credential.isEmpty())message else message.replace(credential,"•••"))
        }
    }
    suspend fun execute(payload:JSONObject,credential:String):Unit=suspendCancellableCoroutine { continuation ->
        val call=client.newCall(request(payload,credential));continuation.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback{
            override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response){
                response.use {
                    try {decode(it,credential);if(continuation.isActive)continuation.resume(Unit)}
                    catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(error)}
                }
            }
        })
    }
}
