package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackendTest {
    @Test fun onlineAcceptanceSendsWebReadyEstimateWithoutQueryCredentials() {
        val calls=mutableListOf<String>()
        val transport=OnlineOrderTransport(BackendHttp(client {r->
            assertEquals("POST",r.method);assertEquals("/pos/api/orders/order-123/accept",r.url.encodedPath)
            assertNull(r.url.query);assertEquals(settings.deviceKey,r.header("X-Device-Key"))
            val sink=okio.Buffer();r.body!!.writeTo(sink)
            calls+=JSONObject(sink.readUtf8()).getString("readyEstimate")
            200 to "{\"ok\":true,\"order\":{\"id\":\"order-123\",\"status\":\"accepted\"}}"
        }))
        listOf("5m","15m","30m","40m","60plus","at:18:30").forEach {transport.accept(settings,"order-123",it)}
        assertEquals(listOf("5m","15m","30m","40m","60plus","at:18:30"),calls)
        try {transport.accept(settings,"order-123","at:25:90");fail()} catch(_:IllegalArgumentException) {}
        assertEquals(6,calls.size)
    }
    @Test fun readyUsesWebNotificationCommandAndNeverFallsBackToSilentStatusChange() {
        var calls=0
        val transport=OnlineOrderTransport(BackendHttp(client {r->
            calls++;assertEquals("POST",r.method);assertEquals("/pos/api/orders/order-123/ready",r.url.encodedPath)
            val sink=okio.Buffer();r.body!!.writeTo(sink);assertEquals("{}",sink.readUtf8())
            404 to "{}"
        }))
        try {transport.ready(settings,"order-123");fail()} catch(e:BackendFailure) {assertEquals(404,e.status)}
        assertEquals(1,calls)
        OnlineOrderTransport(BackendHttp(client {200 to "{\"ok\":true}"})).ready(settings,"order-123")
        try {OnlineOrderTransport(BackendHttp(client {200 to "{\"ok\":true,\"order\":{\"id\":\"other\",\"status\":\"ready\"}}"})).ready(settings,"order-123");fail()}
        catch(_:IllegalStateException) {}
    }
    private val settings=BackendSettings(url="https://shop.example.org/pos/",deviceKey="fixture-device-key")
    private fun client(handler: (Request)->Pair<Int,String>)=OkHttpClient.Builder().addInterceptor {chain->
        val (code,body)=handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .header("Content-Type",if(chain.request().url.encodedPath.endsWith("events")) "text/event-stream; charset=utf-8" else "application/json")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    @Test fun editableRoutesRoundTripAndSecretsStayOutOfDiagnostics() {
        val custom=settings.copy(healthPath="/v2/health",eventsPath="/events",timeoutSeconds=30).validated()
        assertEquals("https://shop.example.org/pos",custom.url)
        assertEquals(custom,BackendSettingsCodec.decode(BackendSettingsCodec.encode(custom)))
        assertFalse(custom.toString().contains(custom.deviceKey))
        assertFalse(custom.toString().contains(custom.url))
        assertEquals(settings.validated().target(),settings.copy(url="https://SHOP.EXAMPLE.ORG:443/pos").validated().target())
    }
    @Test fun rejectUnsafeAddressesPathsAndCredentials() {
        val invalid=listOf(settings.copy(url="http://example.org"),settings.copy(url="https://u:p@example.org"),settings.copy(url="https://example.org?a=b"),settings.copy(url="https://example.org/#fragment"),settings.copy(eventsPath="//foreign.example.org/events"),settings.copy(menuPath="/../menu"),settings.copy(menuPath="/%2e%2e/menu"),settings.copy(deviceKey="bad\r\nkey"),settings.copy(timeoutSeconds=0))
        invalid.forEach {try {it.validated();fail("Accepted invalid configuration")} catch(_:IllegalArgumentException) {}}
    }
    @Test fun connectionMatchesWebHealthOnlyWithConfiguredBasePrefix() {
        val calls=mutableListOf<String>()
        val http=BackendHttp(client {request->
            assertEquals("fixture-device-key",request.header("X-Device-Key"))
            assertNull(request.url.query)
            calls+=request.url.encodedPath
            200 to if(request.url.encodedPath.endsWith("health")) "{\"ok\":true}" else "{\"available\":false,\"fresh\":false,\"state\":null,\"reason\":\"missing\"}"
        })
        http.check(settings)
        assertEquals(listOf("/pos/health"),calls)
    }
    @Test fun rejectedKeyCannotBecomeSuccessfulOrLeakServerBody() {
        val http=BackendHttp(client {r->if(r.url.encodedPath.endsWith("health")) 200 to "{\"ok\":true}" else 401 to "secret from server: fixture-device-key"})
        http.check(settings)
        try {http.checkEvents(settings);fail()} catch(e:BackendFailure) {assertEquals(401,e.status);assertFalse(e.message!!.contains(settings.deviceKey))}
    }
    @Test fun rejectsMalformedAndOversizedResponses() {
        listOf("[]","bad-json","x".repeat(1_048_577)).forEach {raw->
            try {BackendHttp(client {200 to raw}).json(settings,"/health");fail()} catch(_:BackendFailure) {}
        }
    }
    @Test fun noRedirectAndNoMutationDuringConnectionAndEventsProbe() {
        var count=0
        val http=BackendHttp(client {r->assertEquals("GET",r.method);assertNull(r.body);assertEquals(settings.deviceKey,r.header("X-Device-Key"));count++;200 to "event: ping\ndata: {}\n\n"})
        http.checkEvents(settings)
        assertEquals(1,count)
        try {BackendHttp(client {302 to ""}).json(settings,"/health");fail()} catch(e:BackendFailure) {assertEquals(302,e.status)}
    }
    @Test fun streamingTransportUsesHeaderAndDispatchesRealFrames()=kotlinx.coroutines.runBlocking {
        val events=mutableListOf<ServerEvent>()
        val transport=OrderStream(client {request->
            assertEquals(settings.deviceKey,request.header("X-Device-Key"));assertNull(request.url.query)
            200 to "event: ready\ndata: {\"ok\":true}\n\ndata: {\"type\":\"orders\",\"orders\":[]}\n\n"
        })
        transport.read(settings,events::add)
        assertEquals(listOf("ready","message"),events.map {it.type})
        assertTrue(OrderEvents.orders(events.last())!!.isEmpty())
    }
    @Test fun streamingUnauthorizedResponseCannotBecomeConnected()=kotlinx.coroutines.runBlocking {
        try {OrderStream(client {401 to "{}"}).read(settings) {fail("Must not dispatch")};fail()}
        catch(e:BackendFailure) {assertEquals(401,e.status)}
    }
}
