package com.mendelev.mpos.data

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class MPosStorageMirrorTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "mpos-shadow-test-${UUID.randomUUID()}.db"

    @Before fun open() {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mirror = MPosStorageMirror(database, scope) { replies.put(it) }
    }

    @After fun close() {
        mirror.close(); scope.cancel(); database.close()
        RuntimeEnvironment.getApplication().deleteDatabase(name)
    }

    private fun products(label: String) = JSONArray().put(JSONObject().put("id", "product-1")
        .put("name", label).put("category", "Категория").put("type", "simple").put("price", 10).put("stock", 5)).toString()

    private fun send(action: String, id: String, key: String = "", value: String? = null) {
        mirror.handle(JSONObject().put("action", action).put("requestId", id).put("key", key).apply { value?.let { put("payload", it) } })
    }

    private fun reply(id: String): JSONObject {
        val result = replies.poll(10, TimeUnit.SECONDS)
        assertNotNull("Native storage did not complete $id", result)
        assertEquals(id, result!!.getString("requestId"))
        assertFalse(result.getBoolean("authoritative"))
        return result
    }

    @Test fun writesDeletesAndReadsCompleteInArrivalOrder() = runBlocking {
        send("put", "first", "products", products("Первый"))
        send("remove", "remove", "products")
        send("put", "last", "products", products("Последний"))
        send("catalogSnapshot", "snapshot")
        send("catalogParity", "parity")
        for (id in listOf("first", "remove", "last")) assertTrue(reply(id).getBoolean("ok"))
        val snapshot = reply("snapshot")
        assertTrue(snapshot.getBoolean("ok")); assertTrue(snapshot.getBoolean("shadowCaughtUp"))
        assertEquals("Последний", snapshot.getJSONArray("products").getJSONObject(0).getString("name"))
        assertTrue(reply("parity").getBoolean("matches"))
        assertEquals(products("Последний"), database.legacyStorageShadowDao().get("products")!!.payload)
    }

    @Test fun projectionInsertFailureRollsBackRawShadowAndAllCatalogTables() = runBlocking {
        send("put", "seed", "products", products("Сохранённый")); assertTrue(reply("seed").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_category BEFORE INSERT ON category_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        send("put", "failed", "products", products("Не сохранённый"))
        val failure = reply("failed")
        assertFalse(failure.getBoolean("ok")); assertFalse(failure.getBoolean("projectionOk")); assertFalse(failure.getBoolean("shadowCaughtUp"))
        assertEquals(products("Сохранённый"), database.legacyStorageShadowDao().get("products")!!.payload)
        assertEquals("Сохранённый", database.catalogProjectionDao().allProducts().single().name)
        assertEquals(1, database.catalogProjectionDao().categoryCount())
        send("catalogSnapshot", "stale"); assertFalse(reply("stale").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_category")
        send("put", "repaired", "products", products("Новый")); assertTrue(reply("repaired").getBoolean("shadowCaughtUp"))
        send("catalogParity", "parity"); assertTrue(reply("parity").getBoolean("matches"))
    }

    @Test fun projectionDeleteFailureDoesNotDeleteRawShadow() = runBlocking {
        send("put", "seed", "products", products("Сохранённый")); reply("seed")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON product_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        send("remove", "failed", "products"); assertFalse(reply("failed").getBoolean("ok"))
        assertNotNull(database.legacyStorageShadowDao().get("products"))
        assertEquals(1, database.catalogProjectionDao().productCount())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_delete")
        send("remove", "removed", "products"); assertTrue(reply("removed").getBoolean("ok"))
        assertNull(database.legacyStorageShadowDao().get("products"))
        assertEquals(0, database.catalogProjectionDao().productCount())
        assertEquals(0, database.catalogProjectionDao().categoryCount())
    }

    @Test fun malformedProjectionDoesNotKillWorkerOrReplaceLastConsistentData() = runBlocking {
        send("put", "seed", "products", products("Сохранённый")); reply("seed")
        send("put", "invalid", "products", "not-json"); assertFalse(reply("invalid").getBoolean("ok"))
        assertEquals(products("Сохранённый"), database.legacyStorageShadowDao().get("products")!!.payload)
        send("put", "next", "products", products("Следующий")); assertTrue(reply("next").getBoolean("ok"))
        send("stats", "stats")
        val stats = reply("stats")
        assertEquals(1, stats.getInt("catalogProducts")); assertTrue(stats.getBoolean("shadowCaughtUp"))
        assertEquals(0, stats.getInt("pendingShadowKeys"))
    }

    @Test fun committedRawAndProjectionSurviveDatabaseReopen() = runBlocking {
        send("put", "saved", "products", products("После перезапуска")); reply("saved")
        mirror.close(); scope.cancel(); database.close()
        open()
        assertEquals(products("После перезапуска"), database.legacyStorageShadowDao().get("products")!!.payload)
        send("catalogSnapshot", "snapshot")
        val snapshot = reply("snapshot")
        assertTrue(snapshot.getBoolean("ok"))
        assertEquals("После перезапуска", snapshot.getJSONArray("products").getJSONObject(0).getString("name"))
    }
}
