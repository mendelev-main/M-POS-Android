package com.mendelev.mpos.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosBackendHttpTest {
    private fun payload(kind: String) = JSONObject().put("kind",kind).put("backendUrl","https://example.invalid/prefix/")
        .put("deviceKey","synthetic-key").put("body","{}").put("reportId","abcdefghijklmnopqrst")
    @Test fun onlyExactHttpsRoutesAndMethodsWithFiniteTimeouts() {
        val http=MPosBackendHttp()
        for((kind,method) in listOf("health" to "GET","eventsProbe" to "GET","telegramSettings" to "PUT","testOrder" to "POST","liveReport" to "POST")) {
            val request=http.request(payload(kind));assertEquals(method,request.method)
            assertTrue(request.url.encodedPath.startsWith("/prefix/"))
            assertEquals(if(kind=="health")null else "synthetic-key",request.header("X-Device-Key"))
        }
        assertEquals(12000,http.client.callTimeoutMillis);assertFalse(http.client.retryOnConnectionFailure);assertFalse(http.client.followRedirects)
        for(input in listOf(payload("arbitrary"),payload("liveReport").put("reportId","../owner"),payload("health").put("backendUrl","http://example.invalid"),payload("health").put("backendUrl","https://user:password@example.invalid"))) {
            try { http.request(input);fail("invalid config accepted") } catch(_:IllegalArgumentException) { }
        }
    }
    @Test fun probeRequiresEventStreamContentTypeAndNeverReadsBody() {
        val http=MPosBackendHttp()
        for(type in listOf("text/event-stream; charset=utf-8","text/html")) {
            val response=Response.Builder().request(http.request(payload("eventsProbe"))).protocol(Protocol.HTTP_1_1).code(200).message("fixture").header("Content-Type",type).body("never parse stream".toResponseBody()).build()
            response.use { assertEquals(type.startsWith("text/event-stream"),http.decode(it,"eventsProbe").getBoolean("httpOk")) }
        }
    }
}
