package com.mendelev.mpos.network

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosCatalogHttpTest {
    private fun payload(kind:String)=JSONObject().put("backendUrl","https://example.invalid/prefix/")
        .put("deviceKey","synthetic-test-key").put("kind",kind).put("body","{\"text\":\"Б\",\"n\":-0,\"extension\":true}")
    @Test fun exactRoutesPreserveRawBodyHeadersAndNoAutomaticRetry() {
        val http=MPosCatalogHttp()
        for((kind,route)in listOf("menu" to "menu/sync","media" to "media/upload")){
            val request=http.request(payload(kind));assertEquals("/prefix/api/$route",request.url.encodedPath)
            assertEquals("POST",request.method);assertEquals("synthetic-test-key",request.header("X-Device-Key"))
            val buffer=Buffer();request.body!!.writeTo(buffer);assertEquals(payload(kind).getString("body"),buffer.readUtf8())
        }
        assertEquals(60000,http.menuClient.callTimeoutMillis);assertEquals(10000,http.mediaClient.callTimeoutMillis)
        assertFalse(http.menuClient.retryOnConnectionFailure);assertFalse(http.mediaClient.retryOnConnectionFailure)
        for(p in listOf(payload("arbitrary"),payload("menu").put("backendUrl","http://example.invalid"),payload("menu").put("deviceKey",""))){try{http.request(p);fail("invalid route/config accepted")}catch(_:IllegalArgumentException){}}
    }
    @Test fun responseRetainsHttpFailureForReviewed413HandlingAndStrictJson() {
        val http=MPosCatalogHttp()
        fun decode(body:String,code:Int)=http.decode(Response.Builder().request(http.request(payload("media"))).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build())
        val refused=decode("{\"error\":\"too big\"}",413);assertTrue(refused.getBoolean("ok"));assertFalse(refused.getBoolean("httpOk"));assertEquals(413,refused.getInt("httpStatus"));assertEquals("too big",refused.getJSONObject("data").getString("error"))
        for(body in listOf("not json","{unquoted:1}","{} trailing"))assertTrue(decode(body,200).isNull("data"))
        assertEquals(12,decode("12",200).getInt("data"))
    }
    @Test fun actualOkHttpClientDeliversOnePostWithoutTransportReplay() {
        var calls=0
        val client=OkHttpClient.Builder().addInterceptor { chain ->calls++;assertEquals("POST",chain.request().method);Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(500).message("fixture").body("{\"error\":\"offline\"}".toResponseBody()).build()}.build()
        val http=MPosCatalogHttp(client);http.menuClient.newCall(http.request(payload("menu"))).execute().use{assertFalse(http.decode(it).getBoolean("httpOk"))};assertEquals(1,calls)
    }
}
