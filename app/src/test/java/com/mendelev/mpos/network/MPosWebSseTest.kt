package com.mendelev.mpos.network

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class MPosWebSseTest {
    @Test fun framingPreservesNamedEventsIdsRetryAndEmptyData() {
        val reader=MPosWebSseReader(Buffer().writeUtf8("\uFEFF: heartbeat\rid: Б\r\nretry: 12\nevent: custom\ndata: first\ndata:  second\n\ndata\n\nid: bad\u0000id\nretry: -1\ndata: last\n\ndata: unfinished"))
        assertEquals(MPosWebSseReader.Event("custom","first\n second","Б"),reader.next());assertEquals(12L,reader.retryMillis)
        assertEquals(MPosWebSseReader.Event("message","","Б"),reader.next())
        assertEquals(MPosWebSseReader.Event("message","last","Б"),reader.next());assertNull(reader.next())
    }
    @Test fun incompleteIdDoesNotAdvanceReconnectCursor() {
        val reader=MPosWebSseReader(Buffer().writeUtf8("id: uncommitted\ndata: incomplete"),"previous")
        assertNull(reader.next());assertEquals("previous",reader.lastEventId)
    }
    @Test fun idsResetAndRetryMustContainOnlyAsciiDigits() {
        val reader=MPosWebSseReader(Buffer().writeUtf8("id:\nretry: １２\ndata: x\n\nretry: 999999999999999999999\ndata: y\n\n"),"previous")
        assertEquals("",reader.next()!!.id);assertNull(reader.retryMillis);assertEquals("y",reader.next()!!.data);assertNull(reader.retryMillis)
    }
    @Test fun bridgeBackpressureAndReconnectCarryIdWithoutConcurrentDelivery() = runBlocking {
        val requests=CopyOnWriteArrayList<Request>();val events=CopyOnWriteArrayList<JSONObject>()
        val first=CountDownLatch(1);val second=CountDownLatch(1);val reconnected=CountDownLatch(1)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            requests.add(chain.request())
            val body=if(requests.size==1) "retry: 0\nid: first\ndata: one\n\nid: second\ndata: two\n\n" else {reconnected.countDown();"data: three\n\n"}
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("text/event-stream; charset=utf-8".toMediaType())).header("Content-Type","text/event-stream; charset=utf-8").build()
        }.build()
        val transport=MPosWebSse(scope,{ event ->events.add(event);if(event.optString("state")=="event"){if(event.optString("data")=="one")first.countDown() else if(event.optString("data")=="two")second.countDown()}},client)
        try {
            transport.start(config("a"));assertTrue(first.await(3,TimeUnit.SECONDS));assertFalse(second.await(100,TimeUnit.MILLISECONDS))
            transport.acknowledge("old",1);assertFalse(second.await(100,TimeUnit.MILLISECONDS))
            transport.acknowledge("a",1);assertTrue(second.await(3,TimeUnit.SECONDS));transport.acknowledge("a",2)
            assertTrue(reconnected.await(3,TimeUnit.SECONDS));assertEquals("second",requests[1].header("Last-Event-ID"))
            assertEquals("/prefix/api/orders/events",requests[0].url.encodedPath);assertEquals("synthetic key/+",requests[0].url.queryParameter("deviceKey"))
        } finally {transport.stop();scope.cancel()}
    }
    @Test fun permanentHttpAndWrongMimeCloseRatherThanRetry() = runBlocking {
        for((code,mime)in listOf(204 to "text/event-stream",200 to "application/json",403 to "text/event-stream")){
            val closed=CountDownLatch(1);val requests=CopyOnWriteArrayList<Request>();val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val client=OkHttpClient.Builder().addInterceptor { chain ->requests.add(chain.request());Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test").header("Content-Type",mime).body("".toResponseBody()).build()}.build()
            val transport=MPosWebSse(scope,{if(it.optString("state")=="closed")closed.countDown()},client)
            try{transport.start(config("a"));assertTrue(closed.await(3,TimeUnit.SECONDS));transport.foreground();delay(20);assertEquals(1,requests.size)}finally{transport.stop();scope.cancel()}
        }
    }
    @Test fun backgroundDoesNotConnectAndOldCloseCannotStopReplacement() = runBlocking {
        val requests=CopyOnWriteArrayList<Request>();val event=CountDownLatch(1);val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val client=OkHttpClient.Builder().addInterceptor { chain ->requests.add(chain.request());Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test").header("Content-Type","text/event-stream").body("data: keep\n\n".toResponseBody()).build()}.build()
        val transport=MPosWebSse(scope,{if(it.optString("state")=="event")event.countDown()},client)
        try{transport.background();transport.start(config("old"));transport.start(config("new"));transport.stop("old");delay(30);assertTrue(requests.isEmpty());transport.foreground();assertTrue(event.await(3,TimeUnit.SECONDS));assertEquals(1,requests.size)}finally{transport.stop();scope.cancel()}
    }
    private fun config(id:String)=JSONObject().put("sessionId",id).put("backendUrl","https://example.invalid/prefix").put("deviceKey","synthetic key/+")
}
