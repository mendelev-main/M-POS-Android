package com.mendelev.mpos.telegram

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class MPosTelegramConnectionResultTest {
    @Test fun invalidTestCredentialsReturnCorrelatedResultWithoutLegacyNotification() {
        val replies = mutableListOf<JSONObject>()
        var legacyCalls = 0
        val client = TelegramClient({ error("No report requested") }, { _, _ -> legacyCalls++ },
            { error("No monthly report requested") }, { _, _ -> error("No shift requested") }, replies::add)
        client.handle(JSONObject().put("action", "test").put("requestId", "synthetic-request")
            .put("botToken", "synthetic-sensitive-token"))
        assertEquals(0, legacyCalls)
        assertEquals(1, replies.size)
        assertEquals("synthetic-request", replies.single().getString("requestId"))
        assertFalse(replies.single().getBoolean("ok"))
        assertFalse(replies.single().toString().contains("synthetic-sensitive-token"))
    }

    @Test fun legacyTestWithoutRequestIdStillReturnsThroughLegacyCallback() {
        val results = mutableListOf<Boolean>()
        val client = TelegramClient({ error("No report requested") }, { ok, _ -> results.add(ok) },
            { error("No monthly report requested") }, { _, _ -> error("No shift requested") },
            { error("No correlated test requested") })
        client.handle(JSONObject().put("action", "test"))
        assertEquals(listOf(false), results)
    }

    @Test fun correlatedTestHasCompatibleFallbackWhenNewCallbackIsAbsent() {
        val results = mutableListOf<Boolean>()
        val client = TelegramClient({ error("No report requested") }, { ok, _ -> results.add(ok) },
            { error("No monthly report requested") }, { _, _ -> error("No shift requested") })
        client.handle(JSONObject().put("action", "test").put("requestId", "synthetic-request"))
        assertEquals(listOf(false), results)
    }
}
