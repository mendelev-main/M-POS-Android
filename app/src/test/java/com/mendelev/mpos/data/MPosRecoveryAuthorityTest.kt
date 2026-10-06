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
class MPosRecoveryAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "recovery-authority-${UUID.randomUUID()}.db"
    private val layout = "{\"items\":[{\"productId\":\"p1\",\"qty\":1,\"modifier\":true}],\"customer\":{\"address\":\"fixture\"},\"kitchenPrinted\":true,\"extension\":null}"
    private val navigation = "{\"version\":1,\"id\":\"j1\",\"type\":\"payment\",\"writes\":[{\"key\":\"orders\",\"value\":[{\"id\":\"o1\"}]}],\"custom\":true}"

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

    @Test fun bothKeysMigrateOnceRetainExactJsonAndIgnoreObsoleteShadows() {
        for ((key, document) in mapOf("currentOrderSession" to layout, "criticalStorageJournal" to navigation)) {
            call("put", key, "{\"old\":true}")
            assertTrue(call("recoveryInitialize", key, document).getBoolean("authoritative"))
            call("recoveryInitialize", key, "{\"stale\":true}")
            assertTrue(call("put", key, "{\"stale\":true}").getBoolean("ignored"))
            assertTrue(call("remove", key).getBoolean("ignored"))
            assertEquals(document, call("recoveryRead", key).getString("payload"))
        }
    }

    @Test fun independentAbsenceNullWriteAndRemovePreserveDefaultSemantics() {
        call("recoveryInitialize", "currentOrderSession")
        assertFalse(call("recoveryRead", "currentOrderSession").getBoolean("found"))
        assertFalse(call("recoveryStatus", "criticalStorageJournal").getBoolean("initialized"))
        call("recoveryWrite", "currentOrderSession", "null")
        assertTrue(call("recoveryRead", "currentOrderSession").getBoolean("found"))
        assertEquals("null", call("recoveryRead", "currentOrderSession").getString("payload"))
        call("recoveryRemove", "currentOrderSession")
        call("recoveryInitialize", "currentOrderSession", layout)
        assertFalse(call("recoveryRead", "currentOrderSession").getBoolean("found"))
    }

    @Test fun markerInsertFailureRollsBackDocumentAndAllowsRetry() = runBlocking {
        call("put", "currentOrderSession", "{\"old\":true}")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_marker BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key LIKE 'mpos_recovery_authority_v1:%' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("recoveryInitialize", "currentOrderSession", layout).getBoolean("ok"))
        assertFalse(call("recoveryStatus", "currentOrderSession").getBoolean("initialized"))
        assertEquals("{\"old\":true}", database.legacyStorageShadowDao().get("currentOrderSession")!!.payload)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_marker")
        assertTrue(call("recoveryInitialize", "currentOrderSession", layout).getBoolean("ok"))
    }

    @Test fun failedWriteOrRemoveLeavesCommittedWorkspaceAndOtherKeyUntouched() {
        call("recoveryInitialize", "currentOrderSession", layout)
        call("recoveryInitialize", "criticalStorageJournal", navigation)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_layout BEFORE INSERT ON legacy_storage_shadow WHEN NEW.key = 'currentOrderSession' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("recoveryWrite", "currentOrderSession", "{}").getBoolean("ok"))
        assertEquals(layout, call("recoveryRead", "currentOrderSession").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_layout")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_remove BEFORE DELETE ON legacy_storage_shadow WHEN OLD.key = 'currentOrderSession' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("recoveryRemove", "currentOrderSession").getBoolean("ok"))
        assertEquals(layout, call("recoveryRead", "currentOrderSession").getString("payload"))
        assertEquals(navigation, call("recoveryRead", "criticalStorageJournal").getString("payload"))
    }

    @Test fun invalidKeyAndMalformedOrWrongShapePayloadCannotOverwriteWorkspace() {
        call("recoveryInitialize", "currentOrderSession", layout)
        for (invalid in listOf("{", "[]", "{} trailing")) assertFalse(call("recoveryWrite", "currentOrderSession", invalid).getBoolean("ok"))
        assertFalse(call("recoveryInitialize", "orders", "{}").getBoolean("ok"))
        assertEquals(layout, call("recoveryRead", "currentOrderSession").getString("payload"))
    }

    @Test fun fileBackedWorkspaceAndMarkersSurviveDatabaseReopen() {
        call("recoveryInitialize", "currentOrderSession", layout)
        call("recoveryInitialize", "criticalStorageJournal", navigation)
        call("recoveryWrite", "criticalStorageJournal", "{\"folders\":[],\"custom\":\"new\"}")
        mirror.close(); scope.cancel(); database.close(); open()
        assertTrue(call("recoveryStatus", "currentOrderSession").getBoolean("initialized"))
        call("recoveryInitialize", "currentOrderSession", "{}")
        assertEquals(layout, call("recoveryRead", "currentOrderSession").getString("payload"))
        assertEquals("{\"folders\":[],\"custom\":\"new\"}", call("recoveryRead", "criticalStorageJournal").getString("payload"))
    }
    @Test fun projectionFailureRollsBackDocumentAndMarkerForEachRecoveryKey() = runBlocking {
        for ((key, table, document) in listOf(
            Triple("currentOrderSession", "current_order_session_projection", layout),
            Triple("criticalStorageJournal", "critical_storage_journal_projection", navigation))) {
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_index BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            assertFalse(call("recoveryInitialize", key, document).getBoolean("ok"))
            assertFalse(call("recoveryStatus", key).getBoolean("initialized"))
            assertNull(database.legacyStorageShadowDao().get(key))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_index")
            assertTrue(call("recoveryInitialize", key, document).getBoolean("ok"))
        }
        assertEquals(1, database.currentOrderSessionProjectionDao().get()!!.itemCount)
        assertEquals("payment", database.criticalStorageJournalProjectionDao().current()!!.operationType)
    }

    @Test fun failedJournalClearKeepsFullCommittedWritesForRestartReplay() = runBlocking {
        call("recoveryInitialize", "criticalStorageJournal", navigation)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_clear BEFORE DELETE ON critical_storage_journal_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("recoveryWrite", "criticalStorageJournal", "null").getBoolean("ok"))
        assertEquals(navigation, call("recoveryRead", "criticalStorageJournal").getString("payload"))
        assertNotNull(database.criticalStorageJournalProjectionDao().current())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_clear")
        call("recoveryWrite", "criticalStorageJournal", "null")
        assertEquals("null", call("recoveryRead", "criticalStorageJournal").getString("payload"))
        assertNull(database.criticalStorageJournalProjectionDao().current())
    }

}
