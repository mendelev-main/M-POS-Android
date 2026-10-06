package com.mendelev.mpos.data

import androidx.room.Room
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosShiftLifecycleCommandTest {
    private lateinit var database: MPosDatabase
    private val name = "shift-lifecycle-${UUID.randomUUID()}.db"
    private val staff = JSONArray("""[{"id":"e1","name":"Кассир","phone":"test-phone","role":"cashier","custom":true},{"id":"e2","name":"Администратор","role":"admin"}]""")
    private val history = JSONArray("""[{"id":"recent","status":"closed","openedAt":1,"closedAt":100,"openingCash":100,"countedCash":80,"custom":{"keep":true}},{"id":"older","status":"closed","openedAt":1,"closedAt":50,"countedCash":40}]""")
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosShiftStorage(database).initialize(history.toString())
        MPosEmployeeStorage(database).initialize(staff.toString())
        MPosOrderStorage(database).initialize("[]")
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun opening(before: JSONArray = JSONArray(history.toString()), source: String = "recent", cash: Double = 80.0): JSONObject {
        val shift = JSONObject().put("id", "s1").put("status", "open").put("openedAt", 200).put("openingCash", cash)
            .put("employeeId", "e1").put("employeeName", "Кассир").put("employeePhone", "test-phone").put("openingSourceShiftId", source)
        return JSONObject().put("operation", "open").put("shift", shift).put("expectedEmployees", JSONArray(staff.toString()))
            .put("expectedShifts", before).put("shifts", JSONArray(before.toString()).put(shift))
    }
    private suspend fun closing(counted: Double = 75.0, cash: Double = 80.0): JSONObject {
        val before = JSONArray(MPosShiftStorage(database).read().getString("payload"))
        val index = (0 until before.length()).first { before.getJSONObject(it).optString("status") == "open" }
        val after = JSONArray(before.toString())
        val shift = after.getJSONObject(index).put("status", "closed").put("closedAt", 300).put("countedCash", counted)
        return JSONObject().put("operation", "close").put("shift", shift).put("expectedShifts", before).put("shifts", after)
            .put("expectedCash", cash).put("expectedOrderCount", database.orderProjectionDao().orderCount())
    }
    private suspend fun reject(value: JSONObject) {
        var failed = false
        try { MPosShiftLifecycleCommand(database).commit(value.toString()) } catch (_: Exception) { failed = true }
        assertTrue("invalid shift lifecycle must fail", failed)
    }
    @Test fun carriesMostRecentCountedCashAndEmployeeThenClosesWithDifference() = runBlocking {
        val session = """{"items":[{"productId":"unpaid","qty":1}],"custom":true}"""
        MPosRecoveryStorage(database).initialize("currentOrderSession", session)
        val request = opening(); MPosShiftLifecycleCommand(database).commit(request.toString())
        assertEquals(request.getJSONArray("shifts").toString(), MPosShiftStorage(database).read().getString("payload"))
        val close = closing(); val result = MPosShiftLifecycleCommand(database).commit(close.toString())
        assertEquals(80.0, result.getDouble("expectedCash"), 0.0); assertEquals(-5.0, result.getDouble("difference"), 0.0)
        assertEquals(close.getJSONArray("shifts").toString(), MPosShiftStorage(database).read().getString("payload"))
        assertEquals("null", MPosRecoveryStorage(database).read("criticalStorageJournal").getString("payload"))
        assertEquals(0, database.orderProjectionDao().orderCount())
        assertEquals(session, MPosRecoveryStorage(database).read("currentOrderSession").getString("payload"))
        val beforeNext = JSONArray(MPosShiftStorage(database).read().getString("payload"))
        val next = opening(beforeNext, "s1", 75.0).also { it.getJSONObject("shift").put("id", "s2").put("openedAt", 400) }
        MPosShiftLifecycleCommand(database).commit(next.toString())
        assertEquals(75.0, JSONArray(MPosShiftStorage(database).read().getString("payload")).getJSONObject(3).getDouble("openingCash"), 0.0)
    }
    @Test fun firstShiftStartsAtZeroAndMissingPreviousCountedCashUsesZero() = runBlocking {
        MPosShiftStorage(database).write("[]")
        MPosShiftLifecycleCommand(database).commit(opening(JSONArray(), "", 0.0).toString())
        assertEquals(0.0, JSONArray(MPosShiftStorage(database).read().getString("payload")).getJSONObject(0).getDouble("openingCash"), 0.0)
        val missing = JSONArray(history.toString()).also { it.getJSONObject(0).remove("countedCash") }
        MPosShiftStorage(database).write(missing.toString())
        assertTrue(MPosShiftLifecycleCommand(database).commit(opening(missing, "recent", 0.0).also { it.getJSONObject("shift").put("openedAt", 250) }.toString()).getBoolean("ok"))
    }
    @Test fun reopenAndExactRetriesDoNotDuplicateShiftAndOpeningReplayWorksAfterClosure() = runBlocking {
        val request = opening().toString(); MPosShiftLifecycleCommand(database).commit(request)
        database.close(); database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        assertTrue(MPosShiftLifecycleCommand(database).commit(request).getBoolean("replayed"))
        val close = closing().toString(); MPosShiftLifecycleCommand(database).commit(close)
        assertTrue(MPosShiftLifecycleCommand(database).commit(close).getBoolean("replayed"))
        assertTrue(MPosShiftLifecycleCommand(database).commit(request).getBoolean("replayed"))
        assertEquals(3, JSONArray(MPosShiftStorage(database).read().getString("payload")).length())
        reject(JSONObject(close).also { it.getJSONObject("shift").put("countedCash", 90) })
    }
    @Test fun invalidCarryoverDuplicateOpenStaleEmployeeAndUnrelatedChangesReject() = runBlocking {
        reject(opening(cash = 100.0)); reject(opening(source = "older"))
        reject(opening().also { it.getJSONObject("shift").put("employeeName", "Other") })
        reject(opening().also { it.getJSONArray("expectedEmployees").getJSONObject(0).put("role", "admin") })
        reject(opening().also { it.getJSONArray("shifts").getJSONObject(0).put("custom", false) })
        reject(opening().also { it.getJSONObject("shift").put("employeeId", "missing") })
        val negative = JSONArray(history.toString()).also { it.getJSONObject(0).put("countedCash", -1) }
        MPosShiftStorage(database).write(negative.toString()); reject(opening(negative, cash = -1.0))
        MPosShiftStorage(database).write(history.toString()); MPosShiftLifecycleCommand(database).commit(opening().toString())
        val before = JSONArray(MPosShiftStorage(database).read().getString("payload"))
        reject(opening(before).also { it.getJSONObject("shift").put("id", "s2") })
    }
    @Test fun pendingJournalAndEveryPersistenceFailureLeaveOriginalData() = runBlocking {
        MPosRecoveryStorage(database).write("criticalStorageJournal", """{"version":1,"writes":[]}""")
        reject(opening()); MPosRecoveryStorage(database).write("criticalStorageJournal", "null")
        for (table in listOf("shift_projection", "legacy_storage_shadow")) {
            val condition = if (table == "legacy_storage_shadow") "WHEN NEW.key LIKE 'mpos_shift_lifecycle_v1:%'" else ""
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_lifecycle BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
            reject(opening()); assertEquals(history.toString(), MPosShiftStorage(database).read().getString("payload"))
            assertNull(database.legacyStorageShadowDao().get("mpos_shift_lifecycle_v1:open:s1:200"))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_lifecycle")
        }
        MPosShiftLifecycleCommand(database).commit(opening().toString())
        val close = closing(); val original = MPosShiftStorage(database).read().getString("payload")
        for (table in listOf("shift_projection", "legacy_storage_shadow")) {
            val condition = if (table == "legacy_storage_shadow") "WHEN NEW.key LIKE 'mpos_shift_lifecycle_v1:%'" else ""
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_lifecycle BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
            reject(close); assertEquals(original, MPosShiftStorage(database).read().getString("payload"))
            assertNull(database.legacyStorageShadowDao().get("mpos_shift_lifecycle_v1:close:s1:300"))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_lifecycle")
        }
        assertTrue(MPosShiftLifecycleCommand(database).commit(close.toString()).getBoolean("ok"))
    }
    @Test fun closeUsesCrossShiftRefundsAndPreservesMovementsOnProjectionFailure() = runBlocking {
        MPosShiftLifecycleCommand(database).commit(opening().toString())
        val before = JSONArray(MPosShiftStorage(database).read().getString("payload"))
        before.getJSONObject(2).put("cashMovements", JSONArray().put(JSONObject().put("id", "refund").put("type", "withdrawal").put("subtype", "refund").put("amount", 20).put("timestamp", 250)))
        MPosShiftStorage(database).write(before.toString())
        MPosOrderStorage(database).write("""[{"id":"r1","shiftId":"recent","total":20,"method":"cash","returnedAt":250,"returnedShiftId":"s1","returnAmount":20}]""")
        reject(closing(cash = 80.0)); reject(closing(cash = 60.0).put("expectedOrderCount", 0))
        val command = closing(counted = 65.0, cash = 60.0)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_movement BEFORE INSERT ON cash_movement_projection BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        reject(command); assertEquals(before.toString(), MPosShiftStorage(database).read().getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_movement")
        val result = MPosShiftLifecycleCommand(database).commit(command.toString())
        assertEquals(60.0, result.getDouble("expectedCash"), 0.0); assertEquals(5.0, result.getDouble("difference"), 0.0)
        assertEquals(1, database.shiftProjectionDao().allMovements().size)
    }
    @Test fun backupRestoreRejectsOldAcknowledgementAndAllowsFreshLifecycleTimestamp() = runBlocking {
        val request = opening(); MPosShiftLifecycleCommand(database).commit(request.toString())
        val opened = MPosShiftStorage(database).read().getString("payload")
        val close = closing(); MPosShiftLifecycleCommand(database).commit(close.toString())
        MPosShiftStorage(database).write(opened); reject(close)
        val fresh = closing().also { it.getJSONObject("shift").put("closedAt", 400) }
        MPosShiftLifecycleCommand(database).commit(fresh.toString())
        MPosShiftStorage(database).write(history.toString()); reject(request)
        assertTrue(MPosShiftLifecycleCommand(database).commit(opening().also { it.getJSONObject("shift").put("openedAt", 500) }.toString()).getBoolean("ok"))
    }
    @Test fun countedCashMustBeNonnegativeButNeedNotEqualExpectedOrRoundToCents() = runBlocking {
        MPosShiftLifecycleCommand(database).commit(opening().toString())
        reject(closing(counted = -1.0)); reject(closing().also { it.getJSONObject("shift").put("employeeId", "e2") })
        reject(closing().also { it.getJSONObject("shift").put("countedCash", "75") })
        val result = MPosShiftLifecycleCommand(database).commit(closing(counted = 80.001).toString())
        assertEquals(0.001, result.getDouble("difference"), 0.00000001)
    }
}
