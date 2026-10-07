package com.mendelev.mpos.network

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MPosWebAckHttpTest {
    @Test fun onlyReviewedAcknowledgementRoutesHaveBoundedNoRetryPost(){
        val http=MPosWebAckHttp();assertEquals(30000,http.client.callTimeoutMillis);assertFalse(http.client.retryOnConnectionFailure)
        for((key,route)in listOf("webOrderAcceptances" to "accept","webOrderReadyJournal" to "ready")){
            val request=http.request(JSONObject().put("backendUrl","https://example.invalid/prefix").put("deviceKey","synthetic-test-key").put("key",key).put("id","w/Б").put("body",JSONObject().put("readyEstimate","15m")))
            assertEquals("/prefix/api/orders/w%2F%D0%91/$route",request.url.encodedPath);assertEquals("POST",request.method);assertEquals("synthetic-test-key",request.header("X-Device-Key"))
        }
    }
}
