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
class MPosCatalogAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "catalog-authority-${UUID.randomUUID()}.db"

    @Before fun open() {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mirror = MPosStorageMirror(database, scope) { replies.add(it) }
    }
    @After fun close() { mirror.close(); scope.cancel(); database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun send(action: String, id: String = action, payload: String? = null, key: String? = null) {
        mirror.handle(JSONObject().put("action", action).put("requestId", id).also {
            if (payload != null) it.put("payload", payload)
            if (key != null) it.put("key", key)
        })
    }
    private fun reply(id: String): JSONObject = requireNotNull(replies.poll(5, TimeUnit.SECONDS)).also { assertEquals(id, it.getString("requestId")) }
    private fun call(action: String, payload: String? = null): JSONObject { send(action, action, payload); return reply(action) }
    private fun products(name: String) = JSONArray().put(JSONObject().put("id", "p1").put("name", name)
        .put("category", "Напитки").put("price", 12.35).put("stock", 5.75)
        .put("components", JSONArray().put(JSONObject().put("productId", "ingredient").put("qty", 0.25)))
        .put("modifierGroups", JSONArray()).put("localImageId", "fixture-photo").put("custom", JSONObject().put("preserve", true))).toString()

    @Test fun firstMigrationImportsLegacyInsteadOfTrustingStaleShadowAndIsIdempotent() = runBlocking {
        send("put", "stale", products("Старая копия"), "products"); reply("stale")
        val original = products("Текущий каталог")
        assertTrue(call("catalogInitialize", original).getBoolean("authoritative"))
        assertTrue(call("catalogStatus").getBoolean("initialized"))
        assertEquals(original, call("catalogRead").getString("payload"))
        call("catalogInitialize", products("Устаревший cache"))
        assertEquals(original, call("catalogRead").getString("payload"))
        assertNotNull(database.legacyStorageShadowDao().get(MPosCatalogStorage.AUTHORITY_KEY))
    }

    @Test fun writeReadAndRemoveAreFifoAndDoNotReimportAfterRemoval() {
        send("catalogInitialize", "init", products("Первый"))
        send("catalogWrite", "write", products("Новый"))
        send("catalogRead", "read")
        assertTrue(reply("init").getBoolean("ok")); assertTrue(reply("write").getBoolean("ok"))
        assertEquals(products("Новый"), reply("read").getString("payload"))
        assertTrue(call("catalogRemove").getBoolean("ok"))
        assertFalse(call("catalogRead").getBoolean("found"))
        call("catalogInitialize", products("Не импортировать повторно"))
        assertFalse(call("catalogRead").getBoolean("found"))
    }

    @Test fun absentAndStoredNullRemainDifferentAndPreserveDefaultSeedingContract() {
        assertTrue(call("catalogInitialize").getBoolean("ok"))
        assertFalse(call("catalogRead").getBoolean("found"))
        assertTrue(call("catalogWrite", "null").getBoolean("ok"))
        val read = call("catalogRead")
        assertTrue(read.getBoolean("found")); assertEquals("null", read.getString("payload"))
    }

    @Test fun obsoleteShadowPutAndDeleteCannotOverwriteNativeAuthority() {
        call("catalogInitialize", products("Основной"))
        send("put", "obsolete", products("Cache"), "products")
        assertTrue(reply("obsolete").getBoolean("ignored"))
        send("remove", "obsolete-remove", key = "products")
        assertTrue(reply("obsolete-remove").getBoolean("ignored"))
        assertEquals(products("Основной"), call("catalogRead").getString("payload"))
    }

    @Test fun failedMigrationRollsBackAuthorityMarkerDocumentAndIndexesAndCanRetry() = runBlocking {
        send("put", "old", products("До миграции"), "products"); reply("old")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_catalog BEFORE INSERT ON category_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("catalogInitialize", products("Нельзя сохранить")).getBoolean("ok"))
        assertNull(database.legacyStorageShadowDao().get(MPosCatalogStorage.AUTHORITY_KEY))
        assertEquals(products("До миграции"), database.legacyStorageShadowDao().get("products")!!.payload)
        assertEquals("До миграции", database.catalogProjectionDao().allProducts().single().name)
        assertFalse(call("catalogRead").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_catalog")
        assertTrue(call("catalogInitialize", products("Повторная миграция")).getBoolean("ok"))
    }

    @Test fun primaryWriteAndDeleteFailuresLeavePreviousCommittedCatalogReadable() = runBlocking {
        val original = products("Сохранённый")
        call("catalogInitialize", original)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_catalog BEFORE INSERT ON category_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("catalogWrite", products("Не сохранённый")).getBoolean("ok"))
        assertEquals(original, call("catalogRead").getString("payload"))
        assertEquals("Сохранённый", database.catalogProjectionDao().allProducts().single().name)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_catalog")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON product_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("catalogRemove").getBoolean("ok"))
        assertEquals(original, call("catalogRead").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_delete")
        assertTrue(call("catalogWrite", products("Восстановленный")).getBoolean("ok"))
    }

    @Test fun fullDocumentRetainsUnknownFieldsOrderDuplicateOrMissingIdsRatherThanUsingLossyIndexes() {
        val source = "[{\"id\":\"same\",\"name\":\"Кофе ☕\",\"custom\":null},{\"name\":\"Без ID\"},{\"id\":\"same\",\"name\":\"Другой\"}]"
        call("catalogInitialize", source)
        assertEquals(source, call("catalogRead").getString("payload"))
        for (invalid in listOf("[", "{}", "[] trailing")) assertFalse(call("catalogWrite", invalid).getBoolean("ok"))
        assertEquals(source, call("catalogRead").getString("payload"))
    }

    @Test fun fileBackedCatalogAndAuthoritySurviveReopenWithoutImportingStaleLegacy() {
        call("catalogInitialize", products("Первый"))
        call("catalogWrite", products("Последний"))
        mirror.close(); scope.cancel(); database.close()
        open()
        assertTrue(call("catalogStatus").getBoolean("initialized"))
        call("catalogInitialize", products("Устаревший legacy"))
        assertEquals(products("Последний"), call("catalogRead").getString("payload"))
    }
}
