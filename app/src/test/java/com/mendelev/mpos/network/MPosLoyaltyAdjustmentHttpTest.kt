package com.mendelev.mpos.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
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
class MPosLoyaltyAdjustmentHttpTest {
    private fun payload()=JSONObject().put("backendUrl","https://example.invalid/prefix").put("deviceKey","synthetic-key")
        .put("customerId","A /Б").put("body",JSONObject().put("programId","program").put("progressDelta",1).put("rewardDelta",0)
            .put("reason","Correction").put("adminEmployeeId","a").put("adminEmployeeName","Admin"))
    @Test fun requestUsesOnlyAdjustmentRouteAndNativeTransientCredential() {
        val http=MPosLoyaltyAdjustmentHttp();val p=payload();val request=http.request(p,"synthetic-password")
        assertEquals("POST",request.method);assertEquals("/prefix/api/customers/A%20%2F%D0%91/loyalty-adjustment",request.url.encodedPath)
        assertEquals("synthetic-key",request.header("X-Device-Key"));assertEquals(5000,http.client.callTimeoutMillis);assertFalse(http.client.retryOnConnectionFailure)
        val buffer=Buffer();request.body!!.writeTo(buffer);val body=JSONObject(buffer.readUtf8())
        assertEquals("synthetic-password",body.getString("adminPassword"));assertFalse(p.getJSONObject("body").has("adminPassword"))
        assertEquals("a",body.getString("adminEmployeeId"))
    }
    @Test fun decodeRetainsBackendErrorButCannotReflectSubmittedCredential() {
        val http=MPosLoyaltyAdjustmentHttp()
        val response=Response.Builder().request(http.request(payload(),"synthetic-password")).protocol(Protocol.HTTP_1_1).code(409).message("fixture")
            .body("{\"error\":\"Rejected synthetic-password\"}".toResponseBody()).build()
        response.use {try{http.decode(it,"synthetic-password");fail("rejection ignored")}catch(e:IllegalStateException){assertEquals("Rejected •••",e.message)}}
    }
    @Test fun actualAsyncClientExecutesExactlyOneRequestWithNoRealBackend()=runBlocking {
        var count=0
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            count++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body("{}".toResponseBody()).build()
        }.build()
        MPosLoyaltyAdjustmentHttp(client).execute(payload(),"synthetic-password");assertEquals(1,count)
    }
    @Test(expected=IllegalArgumentException::class) fun insecureUrlIsRejected(){MPosLoyaltyAdjustmentHttp().request(payload().put("backendUrl","http://example.invalid"),"synthetic-password")}
}
