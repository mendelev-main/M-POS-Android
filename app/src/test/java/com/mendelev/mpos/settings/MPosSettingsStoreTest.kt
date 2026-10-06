package com.mendelev.mpos.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MPosSettingsStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stores = mutableListOf<MPosSettingsStore>()
    private val replies = LinkedBlockingQueue<JSONObject>()
    private lateinit var preferences: SharedPreferences

    @Before fun setup() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("settings-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
    }
    @After fun cleanup() { stores.forEach { it.close() }; scope.cancel(); preferences.edit().clear().commit() }
    private fun store(prefs: SharedPreferences = preferences) = MPosSettingsStore(prefs, scope) { replies.add(it) }.also { stores += it }
    private fun settings(name: String) = JSONObject().put("printers", JSONArray().put(JSONObject().put("name", name)))
        .put("posNotifications", JSONObject().put("enabled", true)).put("extension", "preserved")
    private fun command(action: String, id: String, settings: JSONObject? = null) = JSONObject()
        .put("action", action).put("requestId", id).also { if (settings != null) it.put("settings", settings) }
    private fun reply() = requireNotNull(replies.poll(5, TimeUnit.SECONDS)) { "missing native settings reply" }
    private fun interceptedCommit(block: (SharedPreferences.Editor) -> Boolean): SharedPreferences =
        object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                    override fun commit(): Boolean = block(editor)
                }
            }
        }

    @Test fun orderedReplaceClearReplaceReadPreservesExistingEnvelopeAndFields() {
        val store = store()
        store.handle(command("replacePlatformSettings", "first", settings("first")))
        store.handle(command("clearPlatformSettings", "clear"))
        store.handle(command("replacePlatformSettings", "last", settings("last")))
        store.handle(command("getPlatformSettings", "read"))
        for (id in listOf("first", "clear", "last", "read")) {
            val value = reply()
            assertEquals(id, value.getString("requestId")); assertTrue(value.getBoolean("ok")); assertFalse(value.getBoolean("authoritative"))
            if (id == "clear") assertTrue(value.isNull("snapshot"))
            if (id == "read") {
                val snapshot = value.getJSONObject("snapshot")
                assertEquals(1, snapshot.getInt("schemaVersion"))
                assertTrue(snapshot.getLong("updatedAt") > 0)
                assertEquals(settings("last").toString(), snapshot.getJSONObject("settings").toString())
                assertEquals(snapshot.toString(), preferences.getString("platform_settings_snapshot", null))
            }
        }
        val reopened = store()
        reopened.handle(command("getPlatformSettings", "reopen"))
        assertEquals(settings("last").toString(), reply().getJSONObject("snapshot").getJSONObject("settings").toString())
    }

    @Test fun diskCommitRunsOffCallerAndAcknowledgementWaitsForIt() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val store = store(interceptedCommit { editor -> started.countDown(); check(release.await(5, TimeUnit.SECONDS)); editor.commit() })
        try {
            val source = settings("captured")
            store.handle(command("replacePlatformSettings", "write", source))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            source.put("extension", "mutated after submission")
            assertTrue(replies.isEmpty())
            store.handle(command("getPlatformSettings", "read"))
            release.countDown()
            assertEquals("preserved", reply().getJSONObject("snapshot").getJSONObject("settings").getString("extension"))
            assertEquals("read", reply().getString("requestId"))
        } finally { release.countDown() }
    }

    @Test fun failedDiskCommitNeverReportsSuccessAndWorkerContinues() {
        val store = store(interceptedCommit { false })
        store.handle(command("replacePlatformSettings", "failed", settings("private fixture value")))
        store.handle(command("getPlatformSettings", "next"))
        val failed = reply()
        assertFalse(failed.getBoolean("ok")); assertFalse(failed.toString().contains("private fixture value"))
        val next = reply(); assertEquals("next", next.getString("requestId")); assertTrue(next.getBoolean("ok")); assertTrue(next.isNull("snapshot"))
        store.handle(command("clearPlatformSettings", "clear-failed"))
        assertFalse(reply().getBoolean("ok"))
    }

    @Test fun invalidCommandsDoNotKillWorkerOrChangeSnapshot() {
        val store = store()
        store.handle(command("replacePlatformSettings", "missing"))
        store.handle(command("unsupported", "unknown"))
        store.handle(command("replacePlatformSettings", "valid", settings("valid")))
        assertFalse(reply().getBoolean("ok")); assertFalse(reply().getBoolean("ok")); assertTrue(reply().getBoolean("ok"))
    }

    @Test fun corruptStoredJsonIsReportedAndLaterReplacementRepairsIt() {
        preferences.edit().putString("platform_settings_snapshot", "not json").commit()
        val store = store()
        store.handle(command("getPlatformSettings", "corrupt"))
        store.handle(command("replacePlatformSettings", "repair", settings("repair")))
        store.handle(command("getPlatformSettings", "read"))
        assertFalse(reply().getBoolean("ok")); assertTrue(reply().getBoolean("ok"))
        assertEquals(settings("repair").toString(), reply().getJSONObject("snapshot").getJSONObject("settings").toString())
    }

    @Test fun fullAndClosedQueueRejectExplicitlyWithoutBlockingCaller() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val store = store(interceptedCommit { editor -> started.countDown(); check(release.await(5, TimeUnit.SECONDS)); editor.commit() })
        try {
            store.handle(command("replacePlatformSettings", "write", settings("held")))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(64) { store.handle(command("getPlatformSettings", "queued-$it")) }
            store.handle(command("getPlatformSettings", "overflow"))
            val rejected = reply(); assertEquals("overflow", rejected.getString("requestId")); assertFalse(rejected.getBoolean("ok"))
            release.countDown()
            assertEquals("write", reply().getString("requestId"))
            repeat(64) { assertEquals("queued-$it", reply().getString("requestId")) }
            store.close()
            store.handle(command("getPlatformSettings", "closed"))
            assertFalse(reply().getBoolean("ok"))
        } finally { release.countDown() }
    }

    @Test fun closingDuringCommitSuppressesLateActivityCallback() {
        for (succeeds in listOf(true, false)) {
            val started = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
            val store = store(interceptedCommit { editor ->
                started.countDown(); check(release.await(5, TimeUnit.SECONDS))
                (if (succeeds) editor.commit() else false).also { finished.countDown() }
            })
            try {
                store.handle(command("replacePlatformSettings", "write", settings("held")))
                assertTrue(started.await(5, TimeUnit.SECONDS))
                store.close(); release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                assertNull(replies.poll(200, TimeUnit.MILLISECONDS))
            } finally { release.countDown() }
        }
    }
}
