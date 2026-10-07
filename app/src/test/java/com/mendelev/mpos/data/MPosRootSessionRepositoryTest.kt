package com.mendelev.mpos.data

import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosRootSessionRepositoryTest {
    private lateinit var database: MPosDatabase
    private val name = "root-session-${UUID.randomUUID()}.db"
    private fun openDatabase() = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
    @Before fun open() { database = openDatabase() }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private suspend fun initialize() {
        MPosShiftStorage(database).initialize("[]")
        MPosEmployeeStorage(database).initialize("[]")
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", null)
    }
    private suspend fun replace(id: Int, role: String = "admin", journal: String = "null") = database.withTransaction {
        MPosShiftStorage(database).write("""[{"id":"shift-$id","status":"open","employeeId":"$id","extension":false}]""")
        MPosEmployeeStorage(database).write("""[{"id":"$id","name":"Сотрудник $id","role":"$role","extension":{"zero":0}}]""")
        MPosRecoveryStorage(database).write("criticalStorageJournal", journal)
    }

    @Test fun freshReadReflectsRestartRoleChangeAndOwnedDocumentReplacement() = runBlocking {
        initialize(); replace(1)
        val repository = MPosRootSessionRepository(database)
        val first = repository.read()
        assertTrue(first.isAdmin)
        first.currentShift!!.put("id", "mutated-view")
        assertEquals("shift-1", first.currentShift!!.getString("id"))
        replace(2, "employee")
        val next = repository.read()
        assertFalse(next.isAdmin)
        assertEquals("2", next.selectedEmployee!!.getString("id"))
        assertEquals("shift-2", next.currentShift!!.getString("id"))
        database.close(); database = openDatabase()
        val restarted = MPosRootSessionRepository(database).read()
        assertEquals(next.bootstrap().toString(), restarted.bootstrap().toString())
    }

    @Test fun pendingJournalIsReportedWithoutReplayOrPermissionMutation() = runBlocking {
        initialize()
        val journal = """{"version":1,"writes":[{"key":"shifts","value":[]}]}"""
        replace(1, journal = journal)
        val before = database.legacyStorageShadowDao().get("criticalStorageJournal")
        val snapshot = MPosRootSessionRepository(database).read()
        assertTrue(snapshot.recoveryPending)
        assertTrue(snapshot.isAdmin)
        assertEquals(before, database.legacyStorageShadowDao().get("criticalStorageJournal"))
        assertEquals("shift-1", snapshot.currentShift!!.getString("id"))
        try { MPosShiftOpeningRepository(database).read(); fail("pending recovery allowed opening") }
        catch (error: IllegalStateException) { assertEquals("pending critical operation", error.message) }
    }

    @Test fun uninitializedRootReadDoesNotCreateAuthorityOrDefaultDocuments() = runBlocking {
        try { MPosRootSessionRepository(database).read(); fail("missing authority accepted") }
        catch (_: IllegalStateException) { assertEquals(0, database.legacyStorageShadowDao().count()) }
        initialize()
        val snapshot = MPosRootSessionRepository(database).read()
        assertNull(snapshot.currentShift); assertNull(snapshot.selectedEmployee)
        assertFalse(snapshot.isAdmin); assertFalse(snapshot.recoveryPending)
    }

    @Test fun threeDocumentContextIsCoherentDuringConcurrentReplacement() = runBlocking {
        initialize(); replace(0)
        val writer = launch(Dispatchers.IO) { repeat(40) { replace(it, if (it % 2 == 0) "admin" else "employee", if (it % 2 == 0) "null" else "{\"version\":1}") } }
        val reader = launch(Dispatchers.IO) {
            repeat(40) {
                val snapshot = MPosRootSessionRepository(database).read()
                val id = snapshot.selectedEmployee!!.getString("id").toInt()
                assertEquals("shift-$id", snapshot.currentShift!!.getString("id"))
                assertEquals(id % 2 == 0, snapshot.isAdmin)
                assertEquals(id % 2 != 0, snapshot.recoveryPending)
            }
        }
        writer.join(); reader.join()
    }
}
