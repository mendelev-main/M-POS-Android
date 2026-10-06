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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosStockPreflightRepositoryTest {
    private lateinit var database: MPosDatabase
    private val name = "stock-preflight-${UUID.randomUUID()}.db"
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosCatalogStorage(database).initialize("[]")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    @Test fun allRecipeReferenceCasesUseRoomWithoutWritingCatalogueCartOrReceipts() = runBlocking {
        val file = listOf(File("../tests/fixtures/recipe-consumption.json"), File("tests/fixtures/recipe-consumption.json")).first { it.exists() }
        val cases = JSONArray(file.readText())
        for (index in 0 until cases.length()) {
            val c = cases.getJSONObject(index); val products = c.getJSONArray("products").toString()
            MPosCatalogStorage(database).write(products)
            val request = JSONObject().put("version", 1).put("items", c.getJSONArray("items"))
            val result = MPosStockPreflightRepository(database).read(request.toString())
            assertTrue(result.getBoolean("ok")); assertTrue(result.getBoolean("authoritative"))
            assertEquals(c.getString("name"), c.getBoolean("valid"), result.getBoolean("allowed"))
            assertEquals(products, MPosCatalogStorage(database).read().getString("payload"))
            assertNull(database.legacyStorageShadowDao().get("currentOrderSession"))
            assertNull(database.legacyStorageShadowDao().get("orders"))
        }
    }
    @Test fun shortageNamesIngredientAndNextReadSeesNewStock() = runBlocking {
        MPosCatalogStorage(database).write("""[{"id":"p","name":"Synthetic milk","type":"simple","stock":0}]""")
        val request = """{"version":1,"items":[{"productId":"p","qty":1}]}"""
        assertEquals("Недостаточно остатка: Synthetic milk", MPosStockPreflightRepository(database).read(request).getString("reason"))
        MPosCatalogStorage(database).write("""[{"id":"p","name":"Synthetic milk","type":"simple","stock":2}]""")
        assertTrue(MPosStockPreflightRepository(database).read(request).getBoolean("allowed"))
    }
    @Test fun malformedProtocolIsUnavailableRatherThanBusinessApproval() = runBlocking {
        for (raw in listOf("{}", "{\"version\":2,\"items\":[]}", "{\"version\":1,\"items\":[]} trailing")) {
            var failed = false
            try { MPosStockPreflightRepository(database).read(raw) } catch (_: Exception) { failed = true }
            assertTrue(failed)
        }
    }
}
