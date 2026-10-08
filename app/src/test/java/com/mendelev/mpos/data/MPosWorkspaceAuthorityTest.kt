package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosWorkspaceAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "workspace-authority-${UUID.randomUUID()}.db"
    private val layout = "{\"categoryOrder\":[\"Кофе\",\"Еда\"],\"categoryColors\":{\"Кофе\":\"#123456\"},\"categoryOnlineOrder\":{\"Кофе\":false},\"tiles\":[{\"type\":\"product\",\"id\":\"p1\"}],\"custom\":null}"
    private val navigation = "{\"folders\":[{\"id\":\"folder\",\"name\":\"Кофе ☕\"}],\"extension\":{\"zero\":0,\"enabled\":false}}"

    @Before fun open() {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mirror = MPosStorageMirror(database, scope) { replies.add(it) }
    }
    @After fun close() { mirror.close(); scope.cancel(); database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun call(action: String, key: String, payload: String? = null): JSONObject {
        mirror.handle(JSONObject().put("action", action).put("requestId", action).put("key", key).also { if (payload != null) it.put("payload", payload) })
        return requireNotNull(replies.poll(5, TimeUnit.SECONDS)).also { assertEquals(action, it.getString("requestId")) }
    }

    @Test fun ownedKeysMigrateOnceRetainExactJsonAndIgnoreObsoleteShadows() {
        for ((key, document) in mapOf("layout" to layout, "posNavigation" to navigation, "company" to "{\"legalName\":\"Cafe\",\"extension\":false}")) {
            call("put", key, "{\"old\":true}")
            assertTrue(call("workspaceInitialize", key, document).getBoolean("authoritative"))
            call("workspaceInitialize", key, "{\"stale\":true}")
            assertTrue(call("put", key, "{\"stale\":true}").getBoolean("ignored"))
            assertTrue(call("remove", key).getBoolean("ignored"))
            assertEquals(document, call("workspaceRead", key).getString("payload"))
        }
    }

    @Test fun independentAbsenceNullWriteAndRemovePreserveDefaultSemantics() {
        call("workspaceInitialize", "layout")
        assertFalse(call("workspaceRead", "layout").getBoolean("found"))
        assertFalse(call("workspaceStatus", "posNavigation").getBoolean("initialized"))
        call("workspaceWrite", "layout", "null")
        assertTrue(call("workspaceRead", "layout").getBoolean("found"))
        assertEquals("null", call("workspaceRead", "layout").getString("payload"))
        call("workspaceRemove", "layout")
        call("workspaceInitialize", "layout", layout)
        assertFalse(call("workspaceRead", "layout").getBoolean("found"))
    }

    @Test fun markerInsertFailureRollsBackDocumentAndAllowsRetry() = runBlocking {
        call("put", "layout", "{\"old\":true}")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_marker BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key LIKE 'mpos_workspace_authority_v1:%' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("workspaceInitialize", "layout", layout).getBoolean("ok"))
        assertFalse(call("workspaceStatus", "layout").getBoolean("initialized"))
        assertEquals("{\"old\":true}", database.legacyStorageShadowDao().get("layout")!!.payload)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_marker")
        assertTrue(call("workspaceInitialize", "layout", layout).getBoolean("ok"))
    }

    @Test fun failedWriteOrRemoveLeavesCommittedWorkspaceAndOtherKeyUntouched() {
        call("workspaceInitialize", "layout", layout)
        call("workspaceInitialize", "posNavigation", navigation)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_layout BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'layout' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("workspaceWrite", "layout", "{}").getBoolean("ok"))
        assertEquals(layout, call("workspaceRead", "layout").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_layout")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_remove BEFORE DELETE ON legacy_storage_shadow WHEN OLD.key = 'layout' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("workspaceRemove", "layout").getBoolean("ok"))
        assertEquals(layout, call("workspaceRead", "layout").getString("payload"))
        assertEquals(navigation, call("workspaceRead", "posNavigation").getString("payload"))
    }

    @Test fun invalidKeyAndMalformedOrWrongShapePayloadCannotOverwriteWorkspace() {
        call("workspaceInitialize", "layout", layout)
        for (invalid in listOf("{", "[]", "{} trailing")) assertFalse(call("workspaceWrite", "layout", invalid).getBoolean("ok"))
        assertFalse(call("workspaceInitialize", "orders", "{}").getBoolean("ok"))
        assertEquals(layout, call("workspaceRead", "layout").getString("payload"))
    }

    @Test fun fileBackedWorkspaceAndMarkersSurviveDatabaseReopen() {
        call("workspaceInitialize", "layout", layout)
        call("workspaceInitialize", "posNavigation", navigation)
        call("workspaceWrite", "posNavigation", "{\"folders\":[],\"custom\":\"new\"}")
        mirror.close(); scope.cancel(); database.close(); open()
        assertTrue(call("workspaceStatus", "layout").getBoolean("initialized"))
        call("workspaceInitialize", "layout", "{}")
        assertEquals(layout, call("workspaceRead", "layout").getString("payload"))
        assertEquals("{\"folders\":[],\"custom\":\"new\"}", call("workspaceRead", "posNavigation").getString("payload"))
    }
}
