package com.mendelev.mpos.network

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MPosNetworkTransport(
    private val scope: LifecycleCoroutineScope,
    private val onResult: (JSONObject) -> Unit,
    private val onEvent: (JSONObject) -> Unit,
    private val database: com.mendelev.mpos.data.MPosDatabase,
) {
    private val webSse = MPosWebSse(scope, onEvent)
    private val availability = MPosAvailabilityHttp()
    private val availabilityJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val catalog = MPosCatalogHttp()
    private val backend = MPosBackendHttp()
    private val webAcks = MPosWebAckHttp()
    private val loyaltyProfiles = MPosLoyaltyProfileHttp()
    private val profileCalls = java.util.concurrent.ConcurrentHashMap<String, Call>()
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var shadowJob: Job? = null
    @Volatile private var shadowCall: Call? = null
    @Volatile private var shadowConnected = false
    @Volatile private var shadowEvents = 0L
    @Volatile private var reconnects = 0L
    @Volatile private var lastEventHash = ""
    @Volatile private var shadowBackendUrl = ""
    @Volatile private var shadowDeviceKey = ""
    @Volatile private var shadowRequested = false

    fun handle(payload: JSONObject) {
        val requestId = payload.optString("requestId")
        when (payload.optString("action")) {
            "startWebSse" -> webSse.start(payload)
            "stopWebSse" -> webSse.stop(payload.optString("sessionId"))
            "webSseAck" -> webSse.acknowledge(payload.optString("sessionId"), payload.optLong("sequence"))
            "availabilityPublish" -> availabilityPublish(requestId,payload)
            "availabilityCancel" -> { availabilityJobs.remove(requestId)?.cancel(); profileCalls.remove(requestId)?.cancel() }
            "catalogExchange" -> catalogExchange(requestId,payload)
            "backendExchange" -> backendExchange(requestId,payload)
            "catalogCancel" -> profileCalls.remove(requestId)?.cancel()
            "webAck" -> webAck(requestId,payload)
            "loyaltyProfile" -> loyaltyProfile(requestId, payload)
            "loyaltyMutation" -> loyaltyProfile(requestId, payload, true)
            "loyaltyProfileCancel" -> profileCalls.remove(requestId)?.cancel()
            "describe" -> onResult(status(requestId))
            "probe" -> probe(requestId, payload)
            "startShadowSse" -> startShadowSse(requestId, payload)
            "stopShadowSse" -> { stopShadowSse(true); onResult(status(requestId).put("ok", true)) }
            "shadowStatus" -> onResult(status(requestId).put("ok", true))
            else -> result(requestId, false, "unsupported native network action")
        }
    }

    private fun status(requestId:String)=JSONObject().put("requestId",requestId).put("ok",true).put("transport","okhttp").put("authoritative",false).put("sseEnabled",shadowJob?.isActive==true).put("businessHandlers","legacy").put("connected",shadowConnected).put("events",shadowEvents).put("reconnects",reconnects).put("lastEventHash",lastEventHash)

    private fun availabilityPublish(requestId:String,payload:JSONObject) {
        val job=scope.launch(Dispatchers.IO,start=kotlinx.coroutines.CoroutineStart.LAZY) {
            var call:Call?=null
            try {
                val ticket=com.mendelev.mpos.data.MPosAvailabilityJournal(database).consume(payload.getString("token"),payload.getJSONObject("body"))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                call=availability.client.newCall(availability.request(ticket));profileCalls.put(requestId,call)?.cancel()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                call.execute().use{onResult(JSONObject().put("requestId",requestId).put("ok",true).put("authoritative",true).put("sent",it.isSuccessful))}
            } catch(error:kotlinx.coroutines.CancellationException){throw error}
            catch(_:Exception){onResult(JSONObject().put("requestId",requestId).put("ok",false).put("message","Не удалось отправить остатки"))}
            finally{call?.cancel();call?.let{profileCalls.remove(requestId,it)}}
        }
        availabilityJobs.put(requestId,job)?.cancel();job.invokeOnCompletion{availabilityJobs.remove(requestId,job)};job.start()
    }

    private fun catalogExchange(requestId: String, payload: JSONObject) {
        val call = try { (if (payload.optString("kind") == "media") catalog.mediaClient else catalog.menuClient).newCall(catalog.request(payload)) }
        catch (_: Exception) { onResult(JSONObject().put("requestId",requestId).put("ok",false).put("message","Некорректные настройки каталога")); return }
        profileCalls.put(requestId,call)?.cancel()
        scope.launch(Dispatchers.IO) {
            try { call.execute().use { onResult(catalog.decode(it).put("requestId",requestId)) } }
            catch (error: Exception) { onResult(JSONObject().put("requestId",requestId).put("ok",false)
                .put("timeout",error is java.io.InterruptedIOException).put("message","Не удалось связаться с сервером")) }
            finally { profileCalls.remove(requestId,call) }
        }
    }

    private fun backendExchange(requestId: String, payload: JSONObject) {
        val call = try { backend.client.newCall(backend.request(payload)) }
        catch (_: Exception) { result(requestId, false, "Проверьте HTTPS адрес backend и ключ устройства"); return }
        profileCalls.put(requestId, call)?.cancel()
        scope.launch(Dispatchers.IO) {
            try { call.execute().use { onResult(backend.decode(it, payload.getString("kind")).put("requestId", requestId)) } }
            catch (error: Exception) {
                val message = when (error) {
                    is java.net.UnknownHostException -> "Не найден адрес backend. Проверьте адрес и DNS сети"
                    is javax.net.ssl.SSLException -> "Не удалось проверить HTTPS сертификат backend"
                    is java.io.InterruptedIOException -> "Backend не ответил вовремя"
                    else -> "Нет связи с backend. Проверьте интернет на планшете"
                }
                result(requestId, false, message)
            } finally { profileCalls.remove(requestId, call) }
        }
    }

    private fun webAck(requestId:String,payload:JSONObject) {
        val call=try{webAcks.client.newCall(webAcks.request(payload))}catch(_:Exception){onResult(JSONObject().put("requestId",requestId).put("ok",false).put("message","Некорректные настройки WEB ACK"));return}
        profileCalls.put(requestId,call)?.cancel()
        scope.launch(Dispatchers.IO){
            try{call.execute().use{onResult(loyaltyProfiles.decode(it).put("source","native-web-ack").put("requestId",requestId))}}
            catch(error:Exception){onResult(JSONObject().put("requestId",requestId).put("ok",false).put("message",if(error is IllegalStateException)error.message else "Не удалось подтвердить WEB заказ"))}
            finally{profileCalls.remove(requestId,call)}
        }
    }

    private fun loyaltyProfile(requestId: String, payload: JSONObject, mutation: Boolean = false) {
        val call = try { loyaltyProfiles.client.newCall(if(mutation)loyaltyProfiles.mutation(payload) else loyaltyProfiles.request(payload)) }
        catch (_: Exception) { onResult(JSONObject().put("requestId",requestId).put("ok",false).put("message","Некорректные настройки сервера")); return }
        profileCalls.put(requestId,call)?.cancel()
        scope.launch(Dispatchers.IO) {
            try { call.execute().use { response -> onResult(loyaltyProfiles.decode(response).put("requestId",requestId)) } }
            catch (error: Exception) {
                val message = when(error) {
                    is java.io.InterruptedIOException -> "Сервер не ответил вовремя"
                    is IllegalStateException -> error.message ?: "Ошибка программы лояльности"
                    else -> "Нет связи с сервером"
                }
                onResult(JSONObject().put("requestId",requestId).put("ok",false).put("message",message))
            } finally { profileCalls.remove(requestId,call) }
        }
    }

    private fun probe(requestId: String, payload: JSONObject) {
        val backendUrl = payload.optString("backendUrl").trim().trimEnd('/'); val deviceKey = payload.optString("deviceKey")
        if (!validBackend(backendUrl, deviceKey)) { result(requestId, false, "HTTPS backend URL and device key are required"); return }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val request=Request.Builder().url(eventsUrl(backendUrl,deviceKey)).header("Accept","text/event-stream").get().build()
                client.newCall(request).execute().use { response -> JSONObject().put("requestId",requestId).put("ok",response.isSuccessful).put("httpStatus",response.code).put("contentType",response.header("Content-Type","")).put("authoritative",false) }
            }.onSuccess(onResult).onFailure { result(requestId,false,it.localizedMessage?:"native network probe failed") }
        }
    }

    private fun startShadowSse(requestId:String,payload:JSONObject,resetDiagnostics:Boolean=true){
        val backendUrl=payload.optString("backendUrl").trim().trimEnd('/'); val deviceKey=payload.optString("deviceKey")
        if(!validBackend(backendUrl,deviceKey)){result(requestId,false,"HTTPS backend URL and device key are required");return}
        stopShadowSse(false); shadowBackendUrl=backendUrl; shadowDeviceKey=deviceKey; shadowRequested=true; if(resetDiagnostics){shadowEvents=0; reconnects=0; lastEventHash=""}
        shadowJob=scope.launch(Dispatchers.IO){
            var attempt=0
            while(isActive){
                val request=Request.Builder().url(eventsUrl(backendUrl,deviceKey)).header("Accept","text/event-stream").get().build()
                try{
                    val call=client.newCall(request); shadowCall=call
                    call.execute().use { response ->
                        if(!response.isSuccessful) throw IllegalStateException("SSE HTTP "+response.code)
                        shadowConnected=true; attempt=0; emitState("connected")
                        val source=response.body?.source() ?: throw IllegalStateException("SSE body missing")
                        val frames=MPosSseReader(source)
                        while(isActive){
                            val data=frames.nextMessage() ?: break
                            observeEvent(data)
                        }
                    }
                }catch(_:Throwable){ if(!isActive) break } finally { shadowConnected=false; shadowCall=null }
                if(!isActive) break
                reconnects++; attempt=(attempt+1).coerceAtMost(6); emitState("reconnecting")
                delay((1000L shl (attempt-1)).coerceAtMost(30000L))
            }
        }
        onResult(status(requestId).put("ok",true))
    }

    private fun observeEvent(data:String){
        shadowEvents++; lastEventHash=sha256(data)
        onEvent(JSONObject().put("type","shadow-observed").put("authoritative",false).put("events",shadowEvents).put("hash",lastEventHash))
    }
    private fun emitState(state:String)=onEvent(JSONObject().put("type","shadow-state").put("state",state).put("authoritative",false).put("reconnects",reconnects))
    private fun stopShadowSse(clearRequest:Boolean){ shadowJob?.cancel(); shadowJob=null; shadowCall?.cancel(); shadowCall=null; shadowConnected=false; if(clearRequest){shadowRequested=false;shadowBackendUrl="";shadowDeviceKey=""} }
    fun onBackground(){ availabilityJobs.values.forEach { it.cancel() }; webSse.background(); if(shadowRequested){ stopShadowSse(false); emitState("background-paused") } }
    fun onForeground(){ webSse.foreground(); if(shadowRequested && shadowJob?.isActive!=true && validBackend(shadowBackendUrl,shadowDeviceKey)){ startShadowSse("",JSONObject().put("backendUrl",shadowBackendUrl).put("deviceKey",shadowDeviceKey),false); emitState("foreground-resumed") } }
    fun close(){ availabilityJobs.values.forEach { it.cancel() }; availabilityJobs.clear(); webSse.stop(); stopShadowSse(true); profileCalls.values.forEach { it.cancel() }; profileCalls.clear() }
    private fun validBackend(url:String,key:String)=url.startsWith("https://")&&key.isNotBlank()
    private fun eventsUrl(url:String,key:String)=url+"/api/orders/events?deviceKey="+URLEncoder.encode(key,"UTF-8")
    private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun result(requestId:String,ok:Boolean,message:String)=onResult(JSONObject().put("requestId",requestId).put("ok",ok).put("message",message).put("authoritative",false))
}