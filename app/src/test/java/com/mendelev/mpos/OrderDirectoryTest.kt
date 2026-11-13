package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrderDirectoryTest {
    private val settings=BackendSettings(url="https://example.org/pos",deviceKey="fixture-device")
    private fun row(id: String="one",status: String="new")=JSONObject().put("id",id).put("status",status).put("total","10").put("created_at","2026-11-13T09:00:00Z")
        .put("order_items",JSONArray().put(JSONObject().put("quantity","1").put("price","10")))
    private fun directory(handler: (Request)->Pair<Int,String>)=OrderDirectory(BackendHttp(OkHttpClient.Builder().addInterceptor {chain->
        val request=chain.request();assertEquals("GET",request.method);assertEquals(settings.deviceKey,request.header("X-Device-Key"));assertNull(request.url.queryParameter("deviceKey"))
        val (code,raw)=handler(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture").body(raw.toResponseBody("application/json".toMediaType())).build()
    }.build()))
    private fun reject(block: ()->Unit) {assertTrue(runCatching(block).isFailure)}
    @Test fun currentRequiresCompleteSnapshotBeforeReturningAnything() {
        val valid=JSONObject().put("orders",JSONArray().put(row())).put("complete",true)
        assertEquals(1,directory {assertEquals("/pos/api/orders/current",it.url.encodedPath);200 to valid.toString()}.current(settings).size)
        reject {directory {200 to valid.put("complete",false).toString()}.current(settings)}
        reject {directory {200 to valid.put("complete",true).put("orders",JSONArray().put(row(status="accepted"))).toString()}.current(settings)}
    }
    @Test fun historyUsesFiveAndOpaqueCursorAndRefusesMalformedOrRepeatedPages() {
        val response=JSONObject().put("orders",JSONArray().put(row(status="ready"))).put("nextCursor","cursor-two")
        val page=directory {assertEquals("5",it.url.queryParameter("limit"));assertEquals("cursor-one",it.url.queryParameter("cursor"));200 to response.toString()}.history(settings,"cursor-one")
        assertEquals("cursor-two",page.nextCursor);assertEquals("ready",page.orders.single().getString("status"))
        reject {directory {200 to response.put("nextCursor","cursor-one").toString()}.history(settings,"cursor-one")}
        reject {directory {error("must not request")}.history(settings,"../secret?key=x")}
        reject {directory {200 to response.put("nextCursor",JSONObject.NULL).put("orders",JSONArray(List(6) {row(it.toString())})).toString()}.history(settings,null)}
        reject {directory {200 to response.put("orders",JSONArray().put(row()).put(row())).toString()}.history(settings,null)}
    }
    @Test fun legacyBackendAndAuthFailuresAreExplicitAndDoNotBecomeEmptyPages() {
        try {directory {404 to "{}"}.history(settings,null);fail()} catch(e:BackendFailure) {assertEquals(404,e.status);assertTrue(e.message!!.contains("backend"))}
        try {directory {403 to "{}"}.current(settings);fail()} catch(e:BackendFailure) {assertEquals(403,e.status)}
    }
}
