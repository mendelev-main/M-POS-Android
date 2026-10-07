package com.mendelev.mpos.network
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class MPosAvailabilityHttpTest {
    @Test fun nativePublicationUsesOnlySnapshotRouteAndThirtySecondNoRetry(){
        val http=MPosAvailabilityHttp();val ticket=JSONObject().put("network",JSONObject().put("backendUrl","https://example.invalid/prefix").put("deviceKey","synthetic-test-key")).put("body",JSONObject().put("version",1).put("items",org.json.JSONArray()))
        val request=http.request(ticket);assertEquals("/prefix/api/availability/snapshot",request.url.encodedPath);assertEquals("POST",request.method);assertEquals("synthetic-test-key",request.header("X-Device-Key"));assertEquals(30000,http.client.callTimeoutMillis);assertFalse(http.client.retryOnConnectionFailure)
    }
}
