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
class MPosCashMovementCommandTest {
    private lateinit var database: MPosDatabase
    private val name = "cash-movement-${UUID.randomUUID()}.db"
    private val shifts = JSONArray("""[{"id":"old","status":"closed","openingCash":100,"custom":{"preserved":true}},{"id":"s1","status":"open","openingCash":100,"cashMovements":[],"employeeId":"e1","custom":"keep"}]""")
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosShiftStorage(database).initialize(shifts.toString())
        MPosOrderStorage(database).initialize("[]")
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun request(type: String = "deposit", amount: Double = 20.0): JSONObject {
        val movement = JSONObject().put("id", "m1").put("type", type).put("amount", amount).put("timestamp", 2000).put("note", "Комментарий")
        return JSONObject().put("shiftId", "s1").put("movement", movement).put("expectedShifts", JSONArray(shifts.toString()))
            .put("shifts", JSONArray(shifts.toString()).also { it.getJSONObject(1).getJSONArray("cashMovements").put(movement) })
    }
    private suspend fun reject(value: JSONObject) {
        var failed = false
        try { MPosCashMovementCommand(database).commit(value.toString()) } catch (_: Exception) { failed = true }
        assertTrue("invalid cash operation must fail", failed)
    }
    private suspend fun balance(): Double {
        val actual = JSONArray(MPosShiftStorage(database).read().getString("payload")).getJSONObject(1)
        return MPosShiftAccounting.balance(actual, JSONArray(database.orderProjectionDao().allOrders().map { JSONObject(it.payload) }))
    }
    @Test fun depositAndWithdrawalPreserveFullRecordAndDoNotTouchReceiptsOrJournal() = runBlocking {
        val command = request(); MPosCashMovementCommand(database).commit(command.toString())
        assertEquals(command.getJSONArray("shifts").toString(), MPosShiftStorage(database).read().getString("payload"))
        assertEquals(120.0, balance(), 0.0)
        val next = request("withdrawal", 20.0).also {
            val before = JSONArray(MPosShiftStorage(database).read().getString("payload"))
            it.put("expectedShifts", before)
            val move = it.getJSONObject("movement").put("id", "m2").put("timestamp", 3000)
            it.put("shifts", JSONArray(before.toString()).also { rows -> rows.getJSONObject(1).getJSONArray("cashMovements").put(move) })
        }
        MPosCashMovementCommand(database).commit(next.toString())
        assertEquals(100.0, balance(), 0.0)
        assertEquals(0, database.orderProjectionDao().orderCount())
        assertEquals("null", MPosRecoveryStorage(database).read("criticalStorageJournal").getString("payload"))
    }
    @Test fun unchangedReplayAfterReopenIsIdempotentAndChangedRequestRejects() = runBlocking {
        val raw = request().toString(); MPosCashMovementCommand(database).commit(raw)
        database.close(); database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        assertTrue(MPosCashMovementCommand(database).commit(raw).getBoolean("replayed"))
        assertEquals(1, database.shiftProjectionDao().allMovements().size)
        reject(JSONObject(raw).also { it.getJSONObject("movement").put("amount", 21) })
        reject(request().also {
            it.getJSONObject("movement").put("timestamp", 3000)
            it.put("expectedShifts", JSONArray(MPosShiftStorage(database).read().getString("payload")))
        })
    }
    @Test fun failuresAtEveryPersistenceBoundaryRollBackAndAllowRetry() = runBlocking {
        for (table in listOf("shift_projection", "cash_movement_projection", "legacy_storage_shadow")) {
            val condition = if (table == "legacy_storage_shadow") "WHEN NEW.key LIKE 'mpos_cash_movement_v1:%'" else ""
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_cash BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
            reject(request())
            assertEquals(shifts.toString(), MPosShiftStorage(database).read().getString("payload"))
            assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
            assertNull(database.legacyStorageShadowDao().get("mpos_cash_movement_v1:m1:2000"))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_cash")
        }
        assertTrue(MPosCashMovementCommand(database).commit(request().toString()).getBoolean("ok"))
    }
    @Test fun insufficientCashInvalidAmountsSubtypesAndStaleSnapshotsReject() = runBlocking {
        reject(request("withdrawal", 101.0)); reject(request("deposit", 0.0)); reject(request("deposit", -1.0)); reject(request("unknown"))
        reject(request().also { it.getJSONObject("movement").put("subtype", "refund") })
        reject(request().also { it.getJSONObject("movement").put("amount", "20") })
        reject(request().also { it.getJSONObject("movement").put("timestamp", 2000.5) })
        reject(request().also { it.getJSONArray("expectedShifts").getJSONObject(1).put("openingCash", 200) })
        reject(request().also { it.getJSONArray("shifts").getJSONObject(0).put("custom", false) })
        assertEquals(100.0, balance(), 0.0)
    }
    @Test fun closedShiftAndPendingJournalPreventMovement() = runBlocking {
        MPosRecoveryStorage(database).write("criticalStorageJournal", """{"version":1,"writes":[]}""")
        reject(request()); MPosRecoveryStorage(database).write("criticalStorageJournal", "null")
        val closed = JSONArray(shifts.toString()).also { it.getJSONObject(1).put("status", "closed") }
        MPosShiftStorage(database).write(closed.toString())
        reject(request().also { it.put("expectedShifts", closed); it.getJSONArray("shifts").getJSONObject(1).put("status", "closed") })
        assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
    }
    @Test fun crossShiftRefundReducesAvailableCashForManualWithdrawal() = runBlocking {
        val before = JSONArray(shifts.toString())
        before.getJSONObject(1).getJSONArray("cashMovements").put(JSONObject().put("id", "refund").put("type", "withdrawal").put("subtype", "refund").put("amount", 20).put("timestamp", 1000))
        MPosShiftStorage(database).write(before.toString())
        MPosOrderStorage(database).write("""[{"id":"r1","shiftId":"old","method":"cash","total":20,"returnedAt":1000,"returnedShiftId":"s1","returnAmount":20}]""")
        val command = request("withdrawal", 81.0).also {
            it.put("expectedShifts", before)
            it.put("shifts", JSONArray(before.toString()).also { rows -> rows.getJSONObject(1).getJSONArray("cashMovements").put(it.getJSONObject("movement")) })
        }
        reject(command)
        assertEquals(80.0, balance(), 0.0)
        command.getJSONObject("movement").put("amount", 80)
        MPosCashMovementCommand(database).commit(command.toString())
        assertEquals(0.0, balance(), 0.0)
    }
    @Test fun restoredBackupRejectsOldReplayAndAllowsFreshMovementTimestamp() = runBlocking {
        val old = request(); MPosCashMovementCommand(database).commit(old.toString())
        MPosShiftStorage(database).write(shifts.toString()); reject(old)
        val fresh = request().also { it.getJSONObject("movement").put("timestamp", 3000) }
        MPosCashMovementCommand(database).commit(fresh.toString())
        assertEquals(120.0, balance(), 0.0)
    }
    @Test fun fractionalAmountsRemainUnroundedAndNegativeDrawerBlocksDeposit() = runBlocking {
        MPosCashMovementCommand(database).commit(request(amount = 0.001).toString())
        assertEquals(100.001, balance(), 0.0000001)
        val negative = JSONArray(shifts.toString()).also { it.getJSONObject(1).put("openingCash", -1) }
        MPosShiftStorage(database).write(negative.toString())
        reject(request().also {
            it.getJSONObject("movement").put("id", "m2")
            it.put("expectedShifts", negative); it.getJSONArray("shifts").getJSONObject(1).put("openingCash", -1)
        })
    }
}
