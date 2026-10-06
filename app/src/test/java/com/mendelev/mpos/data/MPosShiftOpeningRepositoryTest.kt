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
class MPosShiftOpeningRepositoryTest {
    private lateinit var database: MPosDatabase
    private val name = "shift-opening-${UUID.randomUUID()}.db"
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosShiftStorage(database).initialize(null); MPosEmployeeStorage(database).initialize(null)
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", null); Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    @Test fun firstRunWithoutEmployeesAndNullRestoreHaveZeroWithoutSyntheticWrites() = runBlocking {
        for (raw in listOf<String?>(null, "null")) {
            MPosShiftStorage(database).remove(); MPosEmployeeStorage(database).remove()
            if (raw != null) { MPosShiftStorage(database).write(raw); MPosEmployeeStorage(database).write(raw) }
            val before = MPosShiftStorage(database).read().toString()
            val model = MPosShiftOpeningRepository(database).read()
            assertEquals(0.0, model.getDouble("openingCash"), 0.0); assertEquals(0, model.getJSONArray("employees").length())
            assertEquals(before, MPosShiftStorage(database).read().toString())
        }
    }
    @Test fun sortedMinimalStaffAndCarryoverMatchNativeOpeningWithoutDisclosingExtraFields() = runBlocking {
        val staff = """[{"id":"cashier","name":"Кассир","role":"cashier","privateField":"synthetic-private"},{"id":"admin","name":"Администратор","role":"admin"}]"""
        val history = """[{"id":"older","status":"closed","closedAt":50,"countedCash":40},{"id":"recent","status":"closed","closedAt":"0x64","countedCash":"0x50"}]"""
        MPosEmployeeStorage(database).write(staff); MPosShiftStorage(database).write(history)
        val model = MPosShiftOpeningRepository(database).read()
        assertEquals(80.0, model.getDouble("openingCash"), 0.0)
        assertEquals("admin", model.getJSONArray("employees").getJSONObject(0).getString("id"))
        assertFalse(model.toString().contains("synthetic-private")); assertFalse(model.has("password"))
        assertEquals(staff, MPosEmployeeStorage(database).read().getString("payload"))
        val shift = JSONObject().put("id", "s1").put("status", "open").put("openedAt", 200).put("openingCash", 80)
            .put("employeeId", "cashier").put("employeeName", "Кассир").put("employeePhone", "").put("openingSourceShiftId", "recent")
        val command = JSONObject().put("operation", "open").put("shift", shift).put("expectedEmployees", JSONArray(staff)).put("expectedShifts", JSONArray(history)).put("shifts", JSONArray(history).put(shift))
        assertTrue(MPosShiftLifecycleCommand(database).commit(command.toString()).getBoolean("ok"))
    }
    @Test fun openShiftPendingRecoveryInvalidCarryoverAndAmbiguousStaffReject() = runBlocking {
        suspend fun reject() { var failed = false; try { MPosShiftOpeningRepository(database).read() } catch (_: Exception) { failed = true }; assertTrue(failed) }
        MPosShiftStorage(database).write("""[{"id":"s1","status":"open"}]"""); reject()
        MPosShiftStorage(database).write("""[{"id":"s1","status":"closed","countedCash":-1}]"""); reject()
        MPosShiftStorage(database).write("[]"); MPosRecoveryStorage(database).write("criticalStorageJournal", """{"version":1,"writes":[]}"""); reject()
        MPosRecoveryStorage(database).write("criticalStorageJournal", "null")
        MPosEmployeeStorage(database).write("""[{"id":"same","name":"Первый"},{"id":"same","name":"Второй"}]"""); reject()
    }
}
