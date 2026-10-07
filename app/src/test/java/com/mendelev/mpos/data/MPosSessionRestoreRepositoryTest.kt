package com.mendelev.mpos.data

import androidx.room.Room
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosSessionRestoreRepositoryTest {
    private lateinit var database: MPosDatabase
    private val raw = """{"items":[{"productId":"p","qty":2,"extension":false}],"kitchenPrinted":true,"printedItems":[{"id":"p","qty":2}],"paymentDraft":{"parts":[{"paid":true,"amount":5}]},"extension":{"zero":0}}"""
    private fun input() = JSONObject().put("version", 1).put("session", JSONObject(raw))
    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java).build()
    }
    @After fun close() = database.close()

    @Test fun ownedSnapshotProjectsWithoutWritingOrDiscardingPaidMetadata() = runBlocking {
        val storage = MPosRecoveryStorage(database)
        storage.initialize("currentOrderSession", raw)
        val dao = database.legacyStorageShadowDao()
        val before = dao.get("currentOrderSession")
        val count = dao.count()
        val result = MPosSessionRestoreRepository(database).prepare(input())
        assertTrue(result.getBoolean("restore"))
        assertTrue(result.getBoolean("kitchenPrinted"))
        assertEquals(2, result.getJSONObject("state").getJSONArray("cart").getJSONObject(0).getInt("qty"))
        assertEquals(before, dao.get("currentOrderSession"))
        assertEquals(count, dao.count())
        assertTrue(JSONObject(dao.get("currentOrderSession")!!.payload).getJSONObject("paymentDraft").getJSONArray("parts").getJSONObject(0).getBoolean("paid"))
    }

    @Test fun unownedShadowCannotBecomeNativeStartupAuthority() = runBlocking {
        database.legacyStorageShadowDao().upsert(LegacyStorageShadowEntity("currentOrderSession", raw, 1))
        try {
            MPosSessionRestoreRepository(database).prepare(input())
            fail("unowned shadow accepted")
        } catch (_: IllegalStateException) {
            assertEquals(1, database.legacyStorageShadowDao().count())
        }
    }

    @Test fun changedOrRemovedDocumentCannotRestoreStaleOrder() = runBlocking {
        val storage = MPosRecoveryStorage(database)
        storage.initialize("currentOrderSession", raw)
        storage.write("currentOrderSession", "{\"items\":[]}")
        try { MPosSessionRestoreRepository(database).prepare(input()); fail("stale order accepted") }
        catch (_: IllegalStateException) { assertEquals("{\"items\":[]}", storage.read("currentOrderSession").getString("payload")) }
        storage.remove("currentOrderSession")
        try { MPosSessionRestoreRepository(database).prepare(input()); fail("removed order restored") }
        catch (_: IllegalStateException) { assertFalse(storage.read("currentOrderSession").getBoolean("found")) }
    }
}
