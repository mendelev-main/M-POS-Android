package com.mendelev.mpos.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MPosLoyaltyProfileHttpTest {
    private fun payload()=JSONObject().put("backendUrl","https://example.invalid/prefix").put("deviceKey","synthetic-test-key").put("customerId","A /Б")
    private fun response(http:MPosLoyaltyProfileHttp,body:String,code:Int=200)=Response.Builder().request(http.request(payload())).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(body.toResponseBody()).build()
    @Test fun readRequestEncodesCustomerPathAndRetainsDeadlineAndNoRetry(){
        val http=MPosLoyaltyProfileHttp();val request=http.request(payload())
        assertEquals("GET",request.method);assertEquals("/prefix/api/customers/A%20%2F%D0%91/loyalty",request.url.encodedPath)
        assertEquals("synthetic-test-key",request.header("X-Device-Key"));assertEquals(5000,http.client.callTimeoutMillis);assertFalse(http.client.retryOnConnectionFailure)
        assertTrue(request.header("Cache-Control")!!.contains("no-store"))
    }
    @Test fun successfulJsonMalformedBodyAndHttpErrorsMatchReadContract(){
        val http=MPosLoyaltyProfileHttp()
        response(http,"""{"customer":{"id":"c"},"programs":[]}""").use{assertEquals("c",http.decode(it).getJSONObject("data").getJSONObject("customer").getString("id"))}
        for(raw in listOf("not JSON","{unquoted:1}","{'single':1}","{} trailing","{\"a\":1,}","// comment\n{}"))response(http,raw).use{assertTrue("$raw accepted",http.decode(it).isNull("data"))}
        response(http,"""{"error":"Gift unavailable"}""",409).use{try{http.decode(it);fail("HTTP refusal accepted")}catch(e:IllegalStateException){assertEquals("Gift unavailable",e.message)}}
        response(http,"invalid",503).use{try{http.decode(it);fail("HTTP error accepted")}catch(e:IllegalStateException){assertEquals("HTTP 503",e.message)}}
    }
    @Test(expected=IllegalArgumentException::class) fun insecureConfigurationIsRejected(){MPosLoyaltyProfileHttp().request(payload().put("backendUrl","http://example.invalid"))}
    @Test fun actualClientGetsHeadersAndClosesResponse(){
        var observed=false
        val client=OkHttpClient.Builder().addInterceptor { chain -> observed=chain.request().header("X-Device-Key")=="synthetic-test-key";Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body("{\"programs\":[]}".toResponseBody()).build() }.build()
        val http=MPosLoyaltyProfileHttp(client);http.client.newCall(http.request(payload())).execute().use{assertTrue(http.decode(it).getBoolean("ok"))};assertTrue(observed)
    }
}
