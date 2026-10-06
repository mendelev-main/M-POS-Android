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
class MPosCartQuantityRepositoryTest {
    private lateinit var database: MPosDatabase
    private val name = "quantity-${UUID.randomUUID()}.db"
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosCatalogStorage(database).initialize("""[{"id":"p","name":"Synthetic","type":"simple","stock":2},{"id":"other","type":"simple","stock":0}]""")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun request(qty: Any = 1, delta: Any = 1) = JSONObject().put("version", 1)
        .put("items", JSONArray().put(JSONObject().put("productId", "p").put("qty", qty)))
        .put("targetIndex", 0).put("matchingIndices", JSONArray().put(0)).put("delta", delta)
    @Test fun increaseRefusalAndRemovalUseFreshReadWithoutWritingStockOrSession() = runBlocking {
        val repository = MPosCartQuantityRepository(database)
        assertEquals(2.0, repository.read(request().toString()).getDouble("quantity"), 0.0)
        assertFalse(repository.read(request(2).toString()).getBoolean("allowed"))
        val removed = repository.read(request(1, -1).toString())
        assertTrue(removed.getBoolean("remove")); assertTrue(removed.getBoolean("allowed"))
        assertEquals(2.0, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        assertNull(database.legacyStorageShadowDao().get("currentOrderSession"))
    }
    @Test fun decreasingPositiveQuantityStillChecksOtherRowsWhileDeletionBypassesStock() = runBlocking {
        val input = request(2, -1)
        input.getJSONArray("items").put(JSONObject().put("productId", "other").put("qty", 1))
        assertFalse(MPosCartQuantityRepository(database).read(input.toString()).getBoolean("allowed"))
        input.put("delta", -2)
        assertTrue(MPosCartQuantityRepository(database).read(input.toString()).getBoolean("remove"))
        assertTrue(MPosCartQuantityRepository(database).read(input.toString()).getBoolean("allowed"))
    }
    @Test fun legacyStringAdditionAndDuplicateKeyProposalRemainCompatible() = runBlocking {
        assertEquals("11", MPosCartQuantityRepository.quantity("1", 1))
        assertEquals("1e-71", MPosCartQuantityRepository.quantity(1e-7, "1"))
        val input = request("1", 1)
        val result = MPosCartQuantityRepository(database).read(input.toString())
        assertEquals("11", result.getString("quantity")); assertFalse(result.getBoolean("allowed"))
        val duplicate = request()
        duplicate.getJSONArray("items").put(JSONObject().put("productId", "p").put("qty", 1))
        duplicate.put("matchingIndices", JSONArray().put(0).put(1))
        assertFalse(MPosCartQuantityRepository(database).read(duplicate.toString()).getBoolean("allowed"))
    }
    @Test fun malformedInputsCannotProduceApproval() = runBlocking {
        for (input in listOf(request().put("targetIndex", 5), request().put("matchingIndices", JSONArray()), request("bad", 1))) {
            var failed = false
            try { MPosCartQuantityRepository(database).read(input.toString()) } catch (_: Exception) { failed = true }
            assertTrue(failed)
        }
    }
}
