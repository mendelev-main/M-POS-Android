package com.mendelev.mpos.data

import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class MPosActiveSessionRepositoryTest {
    private lateinit var database: MPosDatabase
    @Before fun open() { database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java).build() }
    @After fun close() = database.close()
    private fun shifts(id: Int) = """[{"id":"shift","status":"open","employeeId":"$id","extension":false}]"""
    private fun employees(id: Int) = """[{"id":"$id","name":"Сотрудник","role":"admin","extension":{"zero":0}}]"""

    @Test fun unownedDocumentsCannotInitializeAuthorityByReading() = runBlocking {
        val dao = database.legacyStorageShadowDao()
        dao.upsert(LegacyStorageShadowEntity("shifts", shifts(1), 1))
        dao.upsert(LegacyStorageShadowEntity("employees", employees(1), 1))
        try { MPosActiveSessionRepository(database).bootstrap(); fail("unowned data accepted") }
        catch (_: IllegalStateException) { assertEquals(2, dao.count()) }
    }

    @Test fun readRetainsExactDocumentsAndDistinguishesMissingFromStoredNull() = runBlocking {
        val shifts = MPosShiftStorage(database)
        val employees = MPosEmployeeStorage(database)
        shifts.initialize(null)
        employees.initialize("null")
        val dao = database.legacyStorageShadowDao()
        val before = dao.get("employees")
        val count = dao.count()
        val result = MPosActiveSessionRepository(database).bootstrap()
        assertEquals(0, result.getJSONArray("shifts").length())
        assertEquals("Некорректный формат employees", result.getJSONArray("warnings").getString(0))
        assertFalse(result.getBoolean("isAdmin"))
        assertEquals(before, dao.get("employees"))
        assertEquals(count, dao.count())
        assertNull(dao.get("shifts"))
    }

    @Test fun concurrentPairedWritesNeverMixShiftAndEmployeeGenerations() = runBlocking {
        val shifts = MPosShiftStorage(database)
        val employees = MPosEmployeeStorage(database)
        database.withTransaction { shifts.initialize(shifts(0)); employees.initialize(employees(0)) }
        val writer = launch(Dispatchers.IO) {
            repeat(50) { id -> database.withTransaction { shifts.write(shifts(id)); employees.write(employees(id)) } }
        }
        val reader = launch(Dispatchers.IO) {
            repeat(50) {
                val result = MPosActiveSessionRepository(database).bootstrap()
                assertTrue(result.getBoolean("isAdmin"))
                assertEquals(0, result.getInt("activeShiftIndex"))
                assertEquals(0, result.getInt("activeEmployeeIndex"))
                assertEquals(result.getJSONArray("shifts").getJSONObject(0).getString("employeeId"), result.getJSONArray("employees").getJSONObject(0).getString("id"))
            }
        }
        writer.join(); reader.join()
    }
}
