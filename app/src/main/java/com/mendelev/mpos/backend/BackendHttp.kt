package com.mendelev.mpos.backend

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BackendFailure(message: String,val status: Int?=null,val orderId: String?=null,val orderStatus: String?=null,val code: String?=null): Exception(message)
/** No redirects or automatic retries: credentials and writes stay bound to the configured endpoint. */
class BackendHttp(private val client: OkHttpClient=OkHttpClient()) {
    private fun configured(s: BackendSettings)=client.newBuilder().callTimeout(s.timeoutSeconds.toLong(),TimeUnit.SECONDS)
        .connectTimeout(minOf(s.timeoutSeconds,10).toLong(),TimeUnit.SECONDS).readTimeout(s.timeoutSeconds.toLong(),TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private fun request(s: BackendSettings,path: String)=Request.Builder().url(s.url+path).header("X-Device-Key",s.deviceKey).header("Cache-Control","no-store")
    fun json(input: BackendSettings,path: String,method: String="GET",body: JSONObject?=null): JSONObject {
        val s=input.validated()
        try {
            val request=request(s,path).method(method,body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            return configured(s).newCall(request).execute().use {r->
                val source=r.body?.source() ?: throw BackendFailure("Backend вернул пустой ответ")
                source.request(1_048_577)
                if(source.buffer.size>1_048_576) throw BackendFailure("Ответ backend слишком большой")
                val raw=source.readUtf8()
                if(!r.isSuccessful) {
                    val data=runCatching {BackendJson.obj(raw)}.getOrNull()
                    val order=data?.optJSONObject("order")
                    val code=when {
                        r.code==403 && data?.optString("error")=="Administrator authorization required"->"ADMIN_AUTH_REQUIRED"
                        data?.optString("code") in setOf("LOYALTY_BALANCE_CHANGED","LOYALTY_CONSENT_REQUIRED")->data?.optString("code")
                        else->null
                    }
                    val message=when(code) {
                        "ADMIN_AUTH_REQUIRED"->"Backend отклонил пароль администратора. Введите пароль администратора действующей Web-версии."
                        "LOYALTY_BALANCE_CHANGED"->"Накопления клиента изменились. Обновите данные перед корректировкой."
                        "LOYALTY_CONSENT_REQUIRED"->"Участие клиента изменилось. Обновите данные и проверьте согласие."
                        else->responseFailure(r.code).message!!
                    }
                    throw BackendFailure(message,r.code,order?.optString("id"),order?.optString("status"),code)
                }
                try {BackendJson.obj(raw)} catch(_:Exception) {throw BackendFailure("Backend вернул некорректный JSON")}
            }
        } catch(e:BackendFailure) {throw e} catch(_:Exception) {throw BackendFailure("Нет ответа от backend. Проверьте адрес, интернет и HTTPS-сертификат")}
    }
    fun check(s: BackendSettings) {
        if(json(s,s.healthPath).opt("ok")!=true) throw BackendFailure("Backend не подтвердил готовность")
    }
    /** Read-only probe: never accepts or creates an online order. Header auth is supported by the server. */
    fun checkEvents(input: BackendSettings) {
        val s=input.validated()
        try {configured(s).newCall(request(s,s.eventsPath).header("Accept","text/event-stream").build()).execute().use {r->
            if(!r.isSuccessful) throw responseFailure(r.code)
            if(r.header("Content-Type")?.substringBefore(';')?.trim()!="text/event-stream") throw BackendFailure("Backend не открыл канал событий SSE")
        }} catch(e:BackendFailure) {throw e} catch(_:Exception) {throw BackendFailure("Канал заказов не ответил. Проверьте адрес и ключ устройства")}
    }
    private fun responseFailure(code: Int)=BackendFailure(when(code) {
        401,403->"HTTP $code: backend не разрешил доступ. Проверьте ключ устройства и адрес сервера"
        404->"Маршрут отсутствует на backend. Проверьте пути API"
        409->"Данные на сервере изменились. Проверьте состояние перед повторной публикацией"
        429->"Backend ограничил запросы. Повторите позже"
        in 300..399->"Backend перенаправляет запрос. Укажите конечный HTTPS адрес"
        else->"Backend вернул HTTP $code"
    },code)
}
