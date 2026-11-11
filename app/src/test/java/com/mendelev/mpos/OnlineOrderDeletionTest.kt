package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import com.mendelev.mpos.core.access.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnlineOrderDeletionTest {
    private val settings=BackendSettings(url="https://fixture.example.org/pos",deviceKey="fixture-key")
    private fun transport(handler: (Request)->Pair<Int,String>)=OnlineOrderTransport(BackendHttp(OkHttpClient.Builder().addInterceptor {chain->
        val (status,body)=handler(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()))
    @Test fun deletionUsesOnlySilentStatusRouteAndProjectsAfterAcknowledgement()=runBlocking {
        val steps=mutableListOf<String>()
        val service=OnlineOrderDeletion(transport {r->
            steps+="request"
            assertEquals("PATCH",r.method);assertEquals("/pos/api/orders/order-1/status",r.url.encodedPath)
            assertNull(r.url.query);assertEquals("fixture-key",r.header("X-Device-Key"))
            val sink=Buffer();r.body!!.writeTo(sink);val body=JSONObject(sink.readUtf8())
            assertEquals(setOf("status"),body.keys().asSequence().toSet());assertEquals("cancelled",body.getString("status"))
            200 to """{"ok":true,"order":{"id":"order-1","status":"cancelled"}}"""
        },AdminSession(PasswordVerifier {true}).also {it.login("fixture".toCharArray())})
        service.delete(settings,"order-1",1,before={steps+="before"},after={steps+="removed"})
        assertEquals(listOf("before","request","removed"),steps)
    }
    @Test fun lockedOrExpiredSessionNeverSendsRequest()=runBlocking {
        var calls=0
        val session=AdminSession(PasswordVerifier {true})
        val service=OnlineOrderDeletion(transport {calls++;200 to "{}"},session)
        try {service.delete(settings,"order-1",0);fail()} catch(_:Exception) {}
        session.login("fixture".toCharArray())
        try {service.delete(settings,"order-1",0);fail()} catch(_:Exception) {}
        session.logout()
        try {service.delete(settings,"order-1",1);fail()} catch(_:Exception) {}
        assertEquals(0,calls)
    }
    @Test fun networkFailureOrWrongAcknowledgementDoesNotHideOrRetryThroughAnotherRoute()=runBlocking {
        for((status,body) in listOf(403 to "{}",404 to "{}",503 to "{}",200 to """{"ok":true,"order":{"id":"other","status":"cancelled"}}""",200 to """{"ok":true,"order":{"id":"order-1","status":"accepted"}}""")) {
            var calls=0;var removed=false
            val session=AdminSession(PasswordVerifier {true});session.login("fixture".toCharArray())
            val service=OnlineOrderDeletion(transport {calls++;status to body},session)
            try {service.delete(settings,"order-1",session.state.value.generation,after={removed=true});fail()} catch(_:Exception) {}
            assertEquals(1,calls);assertFalse(removed)
        }
    }
    @Test fun changedTargetOrAlreadyAcceptedOrderGuardPreventsDeletion()=runBlocking {
        var calls=0
        val session=AdminSession(PasswordVerifier {true});session.login("fixture".toCharArray())
        val service=OnlineOrderDeletion(transport {calls++;200 to "{}"},session)
        try {service.delete(settings,"order-1",1,before={error("fixture target changed")});fail()} catch(_:IllegalStateException) {}
        assertEquals(0,calls)
    }
}
