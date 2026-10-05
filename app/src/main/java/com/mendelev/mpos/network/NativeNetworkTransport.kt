package com.mendelev.mpos.network

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class NativeNetworkTransport(
    private val scope: LifecycleCoroutineScope,
    private val onResult: (JSONObject) -> Unit,
    private val onEvent: (JSONObject) -> Unit,
) {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()
    private var shadowJob: Job? = null
    @Volatile private var shadowCall: Call? = null
    @Volatile private var shadowConnected = false
    @Volatile private var shadowEvents = 0L
    @Volatile private var reconnects = 0L
    @Volatile private var lastEventHash = ""

    fun handle(payload: JSONObject) {
        val requestId = payload.optString("requestId")
        when (payload.optString("action")) {
            "describe" -> onResult(status(requestId))
            "probe" -> probe(requestId, payload)
            "startShadowSse" -> startShadowSse(requestId, payload)
            "stopShadowSse" -> { stopShadowSse(); onResult(status(requestId).put("ok", true)) }
            "shadowStatus" -> onResult(status(requestId).put("ok", true))
            else -> result(requestId, false, "unsupported native network action")
        }
    }

    private fun status(requestId:String)=JSONObject().put("requestId",requestId).put("ok",true).put("transport","okhttp").put("authoritative",false).put("sseEnabled",shadowJob?.isActive==true).put("businessHandlers","legacy").put("connected",shadowConnected).put("events",shadowEvents).put("reconnects",reconnects).put("lastEventHash",lastEventHash)

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

    private fun startShadowSse(requestId:String,payload:JSONObject){
        val backendUrl=payload.optString("backendUrl").trim().trimEnd('/'); val deviceKey=payload.optString("deviceKey")
        if(!validBackend(backendUrl,deviceKey)){result(requestId,false,"HTTPS backend URL and device key are required");return}
        stopShadowSse(); shadowEvents=0; reconnects=0; lastEventHash=""
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
                        val data=StringBuilder()
                        while(isActive&&!source.exhausted()){
                            val line=source.readUtf8Line() ?: break
                            if(line.isEmpty()){ if(data.isNotEmpty()){ observeEvent(data.toString()); data.setLength(0) } }
                            else if(line.startsWith("data:")){ if(data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").trimStart()) }
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
    private fun stopShadowSse(){ shadowJob?.cancel(); shadowJob=null; shadowCall?.cancel(); shadowCall=null; shadowConnected=false }
    private fun validBackend(url:String,key:String)=url.startsWith("https://")&&key.isNotBlank()
    private fun eventsUrl(url:String,key:String)=url+"/api/orders/events?deviceKey="+URLEncoder.encode(key,"UTF-8")
    private fun sha256(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun result(requestId:String,ok:Boolean,message:String)=onResult(JSONObject().put("requestId",requestId).put("ok",ok).put("message",message).put("authoritative",false))
}