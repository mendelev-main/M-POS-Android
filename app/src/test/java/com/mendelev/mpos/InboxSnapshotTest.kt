package com.mendelev.mpos

import com.mendelev.mpos.backend.InboxSnapshot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InboxSnapshotTest {
    private fun order(id: String)=JSONObject().put("id",id).put("status","new")
    @Test fun confirmedRemovalSurvivesRestartAndLateNewSnapshotWithoutDroppingOtherOrders() {
        val first=InboxSnapshot().merge(listOf(order("one"),order("two")),emptySet()).remove("one")
        val restored=InboxSnapshot.decode(first.encode())
        val merged=restored.merge(listOf(order("one"),order("three")),emptySet())
        assertEquals(setOf("two","three"),merged.orders.map {it.getString("id")}.toSet())
        assertEquals(setOf("one"),merged.removed)
        assertEquals(merged.encode(),merged.remove("one").encode())
    }
    @Test fun legacyInboxReadsAndAcceptedOrdersStillDisappear() {
        val legacy=InboxSnapshot.decode("""{"version":1,"orders":[{"id":"one","status":"new"},{"id":"two","status":"new"}]}""")
        assertTrue(legacy.removed.isEmpty())
        assertEquals(listOf("two"),legacy.merge(emptyList(),setOf("one")).orders.map {it.getString("id")})
        assertTrue(InboxSnapshot().merge(listOf(order("one")),emptySet()).orders.isNotEmpty())
    }
}
