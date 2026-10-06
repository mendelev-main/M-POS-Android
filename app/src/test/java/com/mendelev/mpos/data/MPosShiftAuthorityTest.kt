package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosShiftAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "shift-authority-${UUID.randomUUID()}.db"

    @Before fun open() {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mirror = MPosStorageMirror(database, scope) { replies.add(it) }
    }
    @After fun close() { mirror.close(); scope.cancel(); database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun send(action: String, id: String = action, payload: String? = null, key: String? = null) {
        mirror.handle(JSONObject().put("action", action).put("requestId", id).also {
            if (payload != null) it.put("payload", payload)
            if (key != null) it.put("key", key)
        })
    }
    private fun reply(id: String): JSONObject = requireNotNull(replies.poll(5, TimeUnit.SECONDS)).also { assertEquals(id, it.getString("requestId")) }
    private fun call(action: String, payload: String? = null): JSONObject { send(action, action, payload); return reply(action) }
    private fun shifts(name: String) = JSONArray().put(JSONObject().put("id", "s1").put("employeeName", name)
        .put("status", "closed").put("employeeId", "e1").put("openedAt", 1000L).put("closedAt", 2000L)
        .put("openingCash", 12.35).put("countedCash", 25.75)
        .put("cashMovements", JSONArray().put(JSONObject().put("id", "m1").put("amount", 12.35).put("type", "expense")))
        .put("custom", JSONObject().put("preserve", true))).toString()

    @Test fun firstMigrationImportsLegacyInsteadOfTrustingStaleShadowAndIsIdempotent() = runBlocking {
        send("put", "stale", shifts("Старая копия"), "shifts"); reply("stale")
        val original = shifts("Текущая смена")
        assertTrue(call("shiftInitialize", original).getBoolean("authoritative"))
        assertTrue(call("shiftStatus").getBoolean("initialized"))
        assertEquals(original, call("shiftRead").getString("payload"))
        assertEquals(12.35, database.shiftProjectionDao().allMovements().single().amount, 0.0)
        call("shiftInitialize", shifts("Устаревший cache"))
        assertEquals(original, call("shiftRead").getString("payload"))
        assertNotNull(database.legacyStorageShadowDao().get(MPosShiftStorage.AUTHORITY_KEY))
    }

    @Test fun writeReadAndRemoveAreFifoAndDoNotReimportAfterRemoval() {
        send("shiftInitialize", "init", shifts("Первый"))
        send("shiftWrite", "write", shifts("Новый"))
        send("shiftRead", "read")
        assertTrue(reply("init").getBoolean("ok")); assertTrue(reply("write").getBoolean("ok"))
        assertEquals(shifts("Новый"), reply("read").getString("payload"))
        assertTrue(call("shiftRemove").getBoolean("ok"))
        assertFalse(call("shiftRead").getBoolean("found"))
        call("shiftInitialize", shifts("Не импортировать повторно"))
        assertFalse(call("shiftRead").getBoolean("found"))
    }

    @Test fun absentAndStoredNullRemainDifferentAndPreserveDefaultSeedingContract() {
        assertTrue(call("shiftInitialize").getBoolean("ok"))
        assertFalse(call("shiftRead").getBoolean("found"))
        assertTrue(call("shiftWrite", "null").getBoolean("ok"))
        val read = call("shiftRead")
        assertTrue(read.getBoolean("found")); assertEquals("null", read.getString("payload"))
    }

    @Test fun obsoleteShadowPutAndDeleteCannotOverwriteNativeAuthority() {
        call("shiftInitialize", shifts("Основной"))
        send("put", "obsolete", shifts("Cache"), "shifts")
        assertTrue(reply("obsolete").getBoolean("ignored"))
        send("remove", "obsolete-remove", key = "shifts")
        assertTrue(reply("obsolete-remove").getBoolean("ignored"))
        assertEquals(shifts("Основной"), call("shiftRead").getString("payload"))
    }

    @Test fun failedMigrationRollsBackAuthorityMarkerDocumentAndIndexesAndCanRetry() = runBlocking {
        send("put", "old", shifts("До миграции"), "shifts"); reply("old")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_shift BEFORE INSERT ON shift_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("shiftInitialize", shifts("Нельзя сохранить")).getBoolean("ok"))
        assertNull(database.legacyStorageShadowDao().get(MPosShiftStorage.AUTHORITY_KEY))
        assertEquals(shifts("До миграции"), database.legacyStorageShadowDao().get("shifts")!!.payload)
        assertEquals("До миграции", database.shiftProjectionDao().allShifts().single().employeeName)
        assertFalse(call("shiftRead").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_shift")
        assertTrue(call("shiftInitialize", shifts("Повторная миграция")).getBoolean("ok"))
    }

    @Test fun primaryWriteAndDeleteFailuresLeavePreviousCommittedCatalogReadable() = runBlocking {
        val original = shifts("Сохранённый")
        call("shiftInitialize", original)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_shift BEFORE INSERT ON shift_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("shiftWrite", shifts("Не сохранённый")).getBoolean("ok"))
        assertEquals(original, call("shiftRead").getString("payload"))
        assertEquals("Сохранённый", database.shiftProjectionDao().allShifts().single().employeeName)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_shift")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON shift_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("shiftRemove").getBoolean("ok"))
        assertEquals(original, call("shiftRead").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_delete")
        assertTrue(call("shiftWrite", shifts("Восстановленный")).getBoolean("ok"))
    }

    @Test fun fullDocumentRetainsUnknownFieldsOrderDuplicateOrMissingIdsRatherThanUsingLossyIndexes() {
        val source = "[{\"id\":\"same\",\"name\":\"Кофе ☕\",\"custom\":null},{\"name\":\"Без ID\"},{\"id\":\"same\",\"name\":\"Другой\"}]"
        call("shiftInitialize", source)
        assertEquals(source, call("shiftRead").getString("payload"))
        for (invalid in listOf("[", "{}", "[] trailing")) assertFalse(call("shiftWrite", invalid).getBoolean("ok"))
        assertEquals(source, call("shiftRead").getString("payload"))
    }

    @Test fun fileBackedCatalogAndAuthoritySurviveReopenWithoutImportingStaleLegacy() {
        call("shiftInitialize", shifts("Первый"))
        call("shiftWrite", shifts("Последний"))
        mirror.close(); scope.cancel(); database.close()
        open()
        assertTrue(call("shiftStatus").getBoolean("initialized"))
        call("shiftInitialize", shifts("Устаревший legacy"))
        assertEquals(shifts("Последний"), call("shiftRead").getString("payload"))
    }
    @Test fun cashMovementFailureRollsBackShiftDocumentAndBothIndexes() = runBlocking {
        val original = shifts("Сохранённая смена")
        assertTrue(call("shiftInitialize", original).getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_movement BEFORE INSERT ON cash_movement_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("shiftWrite", shifts("Новая смена")).getBoolean("ok"))
        assertEquals(original, call("shiftRead").getString("payload"))
        assertEquals("Сохранённая смена", database.shiftProjectionDao().allShifts().single().employeeName)
        assertEquals(12.35, database.shiftProjectionDao().allMovements().single().amount, 0.0)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_movement")
    }

}
