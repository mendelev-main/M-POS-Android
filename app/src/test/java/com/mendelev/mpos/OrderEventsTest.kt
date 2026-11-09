package com.mendelev.mpos

import com.mendelev.mpos.backend.*
import org.junit.Assert.*
import org.junit.Test

class OrderEventsTest {
    @Test fun commentsCrLfMultiLineIdAndRetryAreParsedWithoutIncompleteEvents() {
        val result=mutableListOf<ServerEvent>()
        OrderEvents.read(("\uFEFF: ping\r\nid: 42\r\nretry: 2000\r\nevent: orders\r\ndata: first\r\ndata: second\r\n\r\ndata: truncated").reader(),result::add)
        assertEquals(listOf(ServerEvent("orders","first\nsecond","42",2000)),result)
    }
    @Test fun sleepingEventHonorsServerRetryAndDoesNotBecomeOrder() {
        val result=mutableListOf<ServerEvent>()
        OrderEvents.read("retry: 7200000\nevent: sleeping\ndata: {}\n\n".reader(),result::add)
        assertEquals(7200000L,result.single().retryMillis)
        assertNull(OrderEvents.orders(result.single()))
    }
    @Test fun ordersValidateUniqueIdsAndAmountsWithoutRoundingQuantities() {
        val raw="""{"type":"orders","orders":[{"id":"order-1","status":"new","total":"0.01","delivery_fee":"0","order_items":[{"price":"0.01","quantity":"0.0004"}]}]}"""
        val rows=OrderEvents.orders(ServerEvent("message",raw,null,null))!!
        assertEquals("0.0004",rows.single().getJSONArray("order_items").getJSONObject(0).getString("quantity"))
        try {OrderEvents.orders(ServerEvent("message",raw.replace("0.0004","-1"),null,null));fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun lineAndEventLimitsPreventUnboundedBuffers() {
        listOf("data: "+"x".repeat(65537)+"\n\n",List(32) {"data: "+"x".repeat(40000)+"\n"}.joinToString("")+"\n").forEach {raw->
            try {OrderEvents.read(raw.reader()) {};fail()} catch(_:IllegalArgumentException) {}
        }
    }
}
