package com.mendelev.mpos.network

import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One foreground stream. Acknowledgements bound bridge delivery to one event at a time. */
class MPosWebSse(
    private val scope: CoroutineScope,
    private val emit: (JSONObject) -> Unit,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build(),
) {
    private data class Session(val id: String, val url: String, var lastId: String = "", var retry: Long = 3000,
        var sequence: Long = 0, var ack: CompletableDeferred<Unit>? = null)
    private var session: Session? = null
    private var job: Job? = null
    private var call: Call? = null
    private var foreground = true

    @Synchronized fun start(payload: JSONObject) {
        stop()
        val id = payload.optString("sessionId")
        val url = try {
            payload.optString("backendUrl").trim().trimEnd('/').toHttpUrl().newBuilder()
                .addPathSegments("api/orders/events").addQueryParameter("deviceKey", payload.optString("deviceKey")).build()
        } catch (_: Exception) { null }
        if (id.isBlank() || url?.isHttps != true || payload.optString("deviceKey").isBlank()) {
            emit(JSONObject().put("type", "web-sse").put("sessionId", id).put("state", "closed")); return
        }
        session = Session(id, url.toString())
        if (foreground) connect(session!!)
    }

    @Synchronized fun acknowledge(id: String, sequence: Long) {
        session?.takeIf { it.id == id && it.sequence == sequence }?.ack?.complete(Unit)
    }
    @Synchronized fun stop(id: String? = null) {
        if (id != null && session?.id != id) return
        pause(); session = null
    }
    @Synchronized fun background() { foreground = false; pause(); session?.let { state(it, "reconnecting") } }
    @Synchronized fun foreground() { foreground = true; session?.let { if (job?.isActive != true) connect(it) } }
    private fun pause() { job?.cancel(); job = null; call?.cancel(); call = null }
    private fun state(s: Session, value: String) = emit(JSONObject().put("type", "web-sse").put("sessionId", s.id).put("state", value))

    private fun connect(s: Session) {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var activeCall: Call? = null
                try {
                    val builder = Request.Builder().url(s.url).header("Accept", "text/event-stream").header("Cache-Control", "no-cache")
                    if (s.lastId.isNotEmpty()) builder.headers(builder.build().headers.newBuilder().addUnsafeNonAscii("Last-Event-ID", s.lastId).build())
                    val current = client.newCall(builder.build())
                    activeCall = current
                    synchronized(this@MPosWebSse) { if (session !== s || !isActive) return@launch; call = current }
                    current.execute().use { response ->
                        ensureActive()
                        if (response.code != 200 || response.header("Content-Type", "")!!.substringBefore(';').trim().lowercase() != "text/event-stream") {
                            state(s, "closed")
                            synchronized(this@MPosWebSse) { if (session === s) session = null }
                            return@launch
                        }
                        state(s, "open")
                        val reader = MPosWebSseReader(response.body!!.source(), s.lastId) { s.retry = it }
                        while (isActive) {
                            val event = reader.next()
                            ensureActive()
                            reader.retryMillis?.let { s.retry = it }
                            if (event == null) { s.lastId = reader.lastEventId; break }
                            val ack = CompletableDeferred<Unit>()
                            val seq = synchronized(this@MPosWebSse) { s.ack = ack; ++s.sequence }
                            emit(JSONObject().put("type", "web-sse").put("sessionId", s.id).put("state", "event")
                                .put("eventType", event.type).put("data", event.data).put("lastEventId", event.id).put("sequence", seq))
                            ack.await()
                            s.lastId = event.id
                        }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* No payload, URL or device key in diagnostics. */ }
                finally { synchronized(this@MPosWebSse) { if (call === activeCall) call = null } }
                if (!isActive) break
                state(s, "reconnecting")
                delay(s.retry)
            }
        }
    }
}
