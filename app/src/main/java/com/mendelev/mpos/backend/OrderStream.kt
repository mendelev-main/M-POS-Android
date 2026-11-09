package com.mendelev.mpos.backend

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One connection; the runtime owns reconnection. Cancellation closes an active stream immediately. */
class OrderStream(private val client: OkHttpClient=OkHttpClient()) {
    suspend fun read(input: BackendSettings,receive: (ServerEvent)->Unit): Unit=suspendCancellableCoroutine {continuation->
        val s=input.validated()
        val call=client.newBuilder().callTimeout(0,TimeUnit.SECONDS).connectTimeout(10,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
            .newCall(Request.Builder().url(s.url+s.eventsPath).header("X-Device-Key",s.deviceKey).header("Accept","text/event-stream").header("Cache-Control","no-store").build())
        continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object: Callback {
            override fun onFailure(call: Call,e: IOException) {if(continuation.isActive) continuation.resumeWithException(BackendFailure("Канал заказов недоступен"))}
            override fun onResponse(call: Call,response: Response) {
                try {response.use {r->
                    if(!r.isSuccessful) throw BackendFailure("Канал заказов: HTTP ${r.code}",r.code)
                    require(r.header("Content-Type")?.substringBefore(';')?.trim()=="text/event-stream")
                    val body=r.body ?: error("empty")
                    OrderEvents.read(body.charStream()) {event->if(continuation.isActive) receive(event)}
                };if(continuation.isActive) continuation.resume(Unit)}
                catch(e:Exception) {if(continuation.isActive) continuation.resumeWithException(if(e is BackendFailure) e else BackendFailure("Канал заказов прерван или содержит некорректные данные"))}
            }
        })
    }
}
