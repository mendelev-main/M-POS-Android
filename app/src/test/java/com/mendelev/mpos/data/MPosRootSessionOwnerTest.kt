package com.mendelev.mpos.data

import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosRootSessionOwnerTest {
    private lateinit var database: MPosDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var owner: MPosRootSessionOwner
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        owner = MPosRootSessionOwner(database, scope)
    }
    @After fun close() { owner.close(); scope.cancel(); database.close() }
    private suspend fun awaitState(predicate: (MPosRootSessionOwner.State) -> Boolean) =
        withTimeout(10_000) { owner.state.first(predicate) }
    private suspend fun ready(predicate: (MPosRootSessionOwner.State.Ready) -> Boolean = { true }) =
        awaitState { it is MPosRootSessionOwner.State.Ready && predicate(it) } as MPosRootSessionOwner.State.Ready
    private suspend fun initialize() = database.withTransaction {
        MPosShiftStorage(database).initialize("[]")
        MPosEmployeeStorage(database).initialize("[]")
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
    }
    private suspend fun replace(id: String, role: String) = database.withTransaction {
        MPosShiftStorage(database).write("""[{"id":"shift-$id","status":"open","employeeId":"$id"}]""")
        MPosEmployeeStorage(database).write("""[{"id":"$id","name":"Сотрудник","role":"$role"}]""")
    }

    @Test fun freshInstallWaitsForAuthorityAndDoesNotCreateDocuments() = runBlocking {
        awaitState { it == MPosRootSessionOwner.State.AwaitingMigration }
        assertEquals(0, database.legacyStorageShadowDao().count())
        initialize()
        val state = ready()
        assertNull(state.snapshot.currentShift)
        assertNull(state.snapshot.selectedEmployee)
        assertFalse(state.snapshot.isAdmin)
        assertFalse(state.snapshot.recoveryPending)
    }

    @Test fun ownedReplacementAndRoleChangesRefreshWithoutBrowserNotification() = runBlocking {
        initialize(); replace("a", "admin")
        val first = ready { it.snapshot.currentShift?.optString("id") == "shift-a" }
        assertTrue(first.snapshot.isAdmin)
        replace("b", "employee")
        val next = ready { it.revision > first.revision && it.snapshot.currentShift?.optString("id") == "shift-b" }
        assertEquals("b", next.snapshot.selectedEmployee!!.getString("id"))
        assertFalse(next.snapshot.isAdmin)
        MPosShiftStorage(database).write("[]")
        val ended = ready { it.revision > next.revision && it.snapshot.currentShift == null }
        assertNull(ended.snapshot.selectedEmployee)
        assertFalse(ended.snapshot.isAdmin)
    }

    @Test fun journalAndMalformedDocumentsCannotLeaveOldReadyStateActive() = runBlocking<Unit> {
        initialize(); replace("a", "admin"); ready()
        val journal = """{"version":1,"writes":[{"key":"shifts","value":[]}]}"""
        MPosRecoveryStorage(database).write("criticalStorageJournal", journal)
        val blocked = ready { it.snapshot.recoveryPending }
        assertEquals("shift-a", blocked.snapshot.currentShift!!.getString("id"))
        assertEquals(journal, database.legacyStorageShadowDao().get("criticalStorageJournal")!!.payload)
        database.legacyStorageShadowDao().upsert(LegacyStorageShadowEntity("employees", "{invalid", 10))
        awaitState { it == MPosRootSessionOwner.State.Failed }
        replace("b", "employee")
        val recovered = ready { it.snapshot.currentShift?.optString("id") == "shift-b" }
        assertFalse(recovered.snapshot.isAdmin)
        assertTrue(recovered.snapshot.recoveryPending)
        database.legacyStorageShadowDao().delete(MPosEmployeeStorage.AUTHORITY_KEY)
        awaitState { it == MPosRootSessionOwner.State.AwaitingMigration }
    }

    @Test fun foregroundRefreshAndBootstrapUseLiveDataWithoutReplayingEffects() = runBlocking {
        initialize(); replace("a", "admin")
        val before = ready { it.snapshot.currentShift != null }
        owner.refresh()
        val foreground = ready()
        assertSame(before.snapshot, foreground.snapshot) // Confirmed unchanged documents do not reparse/reset the view.
        assertEquals(before.revision, foreground.revision)
        assertEquals("a", foreground.snapshot.selectedEmployee!!.getString("id"))
        replace("b", "employee")
        // No waiting for the observer: bootstrap must read its own current transaction.
        val bootstrap = owner.bootstrap()
        assertEquals("b", bootstrap.getJSONArray("employees").getJSONObject(0).getString("id"))
        assertFalse(bootstrap.getBoolean("isAdmin"))
        assertEquals("null", database.legacyStorageShadowDao().get("criticalStorageJournal")!!.payload)
    }

    @Test fun observedRecordsCannotBeMutatedByConsumers() = runBlocking {
        initialize(); replace("a", "employee")
        val snapshot = ready { it.snapshot.currentShift != null }.snapshot
        snapshot.shifts.getJSONObject(0).put("status", "closed")
        snapshot.employees.getJSONObject(0).put("role", "admin")
        snapshot.selectedEmployee!!.put("id", "changed")
        snapshot.bootstrap().put("isAdmin", true)
        assertEquals("open", snapshot.currentShift!!.getString("status"))
        assertEquals("a", snapshot.selectedEmployee!!.getString("id"))
        assertEquals("employee", snapshot.employees.getJSONObject(0).getString("role"))
        assertFalse(snapshot.isAdmin)
        assertFalse(owner.bootstrap().getBoolean("isAdmin"))
    }

    @Test fun closeStopsObservationAndRejectsLaterBootstrapOrRefresh() = runBlocking {
        initialize(); replace("a", "admin"); ready()
        owner.close()
        replace("b", "employee")
        owner.refresh()
        assertEquals(MPosRootSessionOwner.State.Closed, owner.state.value)
        try { owner.bootstrap(); fail("closed owner served bootstrap") }
        catch (_: IllegalStateException) { assertEquals(MPosRootSessionOwner.State.Closed, owner.state.value) }
    }
}
