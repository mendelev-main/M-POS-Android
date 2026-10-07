package com.mendelev.mpos.data

import androidx.room.Room
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
class MPosRuntimeSnapshotTest {
    private lateinit var database: MPosDatabase
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java).build()
    }
    @After fun close() = database.close()

    @Test fun preservesRawExtensionsAndDistinguishesAbsentFromJsonNull() = runBlocking {
        val dao = database.legacyStorageShadowDao()
        val raw = " {\"extension\":{\"zero\":0,\"enabled\":false},\"name\":\"Кофе ☕\"} "
        dao.upsert(LegacyStorageShadowEntity("products", raw, 12L))
        dao.upsert(LegacyStorageShadowEntity("currentShift", "null", 13L))
        dao.upsert(LegacyStorageShadowEntity("unrequested", "private", 14L))
        val result = MPosRuntimeSnapshot(database).read(setOf("products", "currentShift", "missing"))
        assertEquals(setOf("products", "currentShift"), result.keys)
        assertEquals(MPosRuntimeSnapshot.Document(raw, 12L), result["products"])
        assertEquals("null", result["currentShift"]?.payload)
        assertEquals(3, dao.count())
    }

    @Test fun emptyRequestDoesNotInitializeAnyAuthority() = runBlocking {
        assertTrue(MPosRuntimeSnapshot(database).read(emptySet()).isEmpty())
        assertEquals(0, database.legacyStorageShadowDao().count())
    }

    @Test fun invalidRequestLeavesStoredDocumentsUntouched() = runBlocking {
        val dao = database.legacyStorageShadowDao()
        dao.upsert(LegacyStorageShadowEntity("products", "[]", 9L))
        try {
            MPosRuntimeSnapshot(database).read(setOf("products", " "))
            fail("blank key accepted")
        } catch (_: IllegalArgumentException) {
            assertEquals("[]", dao.get("products")?.payload)
            assertEquals(1, dao.count())
        }
    }
}
