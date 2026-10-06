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
class MPosParkedOrderAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "parked-authority-${UUID.randomUUID()}.db"

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
    private fun parked(name: String) = JSONArray().put(JSONObject().put("id", "parked1").put("employeeName", name)
        .put("total", 12.35).put("orderType", "Доставка").put("deliveryFee", 2.0)
        .put("customer", JSONObject().put("id", "c1").put("name", "Клиент").put("address", "fixture"))
        .put("items", JSONArray().put(JSONObject().put("productId", "p1").put("name", "Кофе").put("qty", 1).put("price", 10.35).put("custom", true)))
        .put("kitchenPrinted", true).put("printedItems", JSONArray().put(JSONObject().put("fixture", true)))
        .put("webOrderId", "web1").put("webOrderStatus", "ready").put("custom", true)).toString()

    @Test fun firstMigrationImportsLegacyInsteadOfTrustingStaleShadowAndIsIdempotent() = runBlocking {
        send("put", "stale", parked("Старая копия"), "parked"); reply("stale")
        val original = parked("Текущий сотрудник")
        assertTrue(call("parkedInitialize", original).getBoolean("authoritative"))
        assertTrue(call("parkedStatus").getBoolean("initialized"))
        assertEquals(original, call("parkedRead").getString("payload"))
        call("parkedInitialize", parked("Устаревший cache"))
        assertEquals(original, call("parkedRead").getString("payload"))
        assertNotNull(database.legacyStorageShadowDao().get(MPosParkedOrderStorage.AUTHORITY_KEY))
    }

    @Test fun writeReadAndRemoveAreFifoAndDoNotReimportAfterRemoval() {
        send("parkedInitialize", "init", parked("Первый"))
        send("parkedWrite", "write", parked("Новый"))
        send("parkedRead", "read")
        assertTrue(reply("init").getBoolean("ok")); assertTrue(reply("write").getBoolean("ok"))
        assertEquals(parked("Новый"), reply("read").getString("payload"))
        assertTrue(call("parkedRemove").getBoolean("ok"))
        assertFalse(call("parkedRead").getBoolean("found"))
        call("parkedInitialize", parked("Не импортировать повторно"))
        assertFalse(call("parkedRead").getBoolean("found"))
    }

    @Test fun absentAndStoredNullRemainDifferentAndPreserveDefaultSeedingContract() {
        assertTrue(call("parkedInitialize").getBoolean("ok"))
        assertFalse(call("parkedRead").getBoolean("found"))
        assertTrue(call("parkedWrite", "null").getBoolean("ok"))
        val read = call("parkedRead")
        assertTrue(read.getBoolean("found")); assertEquals("null", read.getString("payload"))
    }

    @Test fun obsoleteShadowPutAndDeleteCannotOverwriteNativeAuthority() {
        call("parkedInitialize", parked("Основной"))
        send("put", "obsolete", parked("Cache"), "parked")
        assertTrue(reply("obsolete").getBoolean("ignored"))
        send("remove", "obsolete-remove", key = "parked")
        assertTrue(reply("obsolete-remove").getBoolean("ignored"))
        assertEquals(parked("Основной"), call("parkedRead").getString("payload"))
    }

    @Test fun failedMigrationRollsBackAuthorityMarkerDocumentAndIndexesAndCanRetry() = runBlocking {
        send("put", "old", parked("До миграции"), "parked"); reply("old")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_employee BEFORE INSERT ON parked_order_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("parkedInitialize", parked("Нельзя сохранить")).getBoolean("ok"))
        assertNull(database.legacyStorageShadowDao().get(MPosParkedOrderStorage.AUTHORITY_KEY))
        assertEquals(parked("До миграции"), database.legacyStorageShadowDao().get("parked")!!.payload)
        assertEquals("До миграции", database.parkedOrderProjectionDao().allOrders().single().employeeName)
        assertFalse(call("parkedRead").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_employee")
        assertTrue(call("parkedInitialize", parked("Повторная миграция")).getBoolean("ok"))
    }

    @Test fun primaryWriteAndDeleteFailuresLeavePreviousCommittedCatalogReadable() = runBlocking {
        val original = parked("Сохранённый")
        call("parkedInitialize", original)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_employee BEFORE INSERT ON parked_order_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("parkedWrite", parked("Не сохранённый")).getBoolean("ok"))
        assertEquals(original, call("parkedRead").getString("payload"))
        assertEquals("Сохранённый", database.parkedOrderProjectionDao().allOrders().single().employeeName)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_employee")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON parked_order_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("parkedRemove").getBoolean("ok"))
        assertEquals(original, call("parkedRead").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_delete")
        assertTrue(call("parkedWrite", parked("Восстановленный")).getBoolean("ok"))
    }

    @Test fun fullDocumentRetainsUnknownFieldsOrderDuplicateOrMissingIdsRatherThanUsingLossyIndexes() {
        val source = "[{\"id\":\"same\",\"name\":\"Кофе ☕\",\"custom\":null},{\"name\":\"Без ID\"},{\"id\":\"same\",\"name\":\"Другой\"}]"
        call("parkedInitialize", source)
        assertEquals(source, call("parkedRead").getString("payload"))
        for (invalid in listOf("[", "{}", "[] trailing")) assertFalse(call("parkedWrite", invalid).getBoolean("ok"))
        assertEquals(source, call("parkedRead").getString("payload"))
    }

    @Test fun fileBackedCatalogAndAuthoritySurviveReopenWithoutImportingStaleLegacy() {
        call("parkedInitialize", parked("Первый"))
        call("parkedWrite", parked("Последний"))
        mirror.close(); scope.cancel(); database.close()
        open()
        assertTrue(call("parkedStatus").getBoolean("initialized"))
        call("parkedInitialize", parked("Устаревший legacy"))
        assertEquals(parked("Последний"), call("parkedRead").getString("payload"))
    }
    @Test fun lineFailureRollsBackFullDocumentHeaderAndLines() = runBlocking {
        val original = parked("Сохранённый")
        assertTrue(call("parkedInitialize", original).getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_line BEFORE INSERT ON parked_order_line_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("parkedWrite", parked("Новый")).getBoolean("ok"))
        assertEquals(original, call("parkedRead").getString("payload"))
        assertEquals("Сохранённый", database.parkedOrderProjectionDao().allOrders().single().employeeName)
        assertEquals(1, database.parkedOrderProjectionDao().allLines().size)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_line")
    }

}
