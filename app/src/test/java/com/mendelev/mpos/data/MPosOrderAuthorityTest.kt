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
class MPosOrderAuthorityTest {
    private lateinit var database: MPosDatabase
    private lateinit var mirror: MPosStorageMirror
    private lateinit var scope: CoroutineScope
    private val replies = LinkedBlockingQueue<JSONObject>()
    private val name = "order-authority-${UUID.randomUUID()}.db"

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
    private fun orders(name: String) = JSONArray().put(JSONObject().put("id", "o1").put("employeeName", name)
        .put("receiptNumber", 42).put("shiftId", "s1").put("method", "split").put("total", 12.35)
        .put("items", JSONArray().put(JSONObject().put("productId", "p1").put("name", "Кофе").put("qty", 1.0).put("price", 12.35)
            .put("components", JSONArray()).put("custom", true)))
        .put("payments", JSONArray().put(JSONObject().put("method", "cash").put("amount", 5.0).put("cashGiven", 10.0).put("change", 5.0))
            .put(JSONObject().put("method", "card").put("amount", 7.35)))
        .put("stockConsumption", JSONObject().put("version", 1).put("items", JSONArray()))
        .put("loyaltySync", JSONObject().put("status", "pending"))
        .put("custom", JSONObject().put("preserve", true))).toString()

    @Test fun firstMigrationImportsLegacyInsteadOfTrustingStaleShadowAndIsIdempotent() = runBlocking {
        send("put", "stale", orders("Старая копия"), "orders"); reply("stale")
        val original = orders("Текущий сотрудник")
        assertTrue(call("orderInitialize", original).getBoolean("authoritative"))
        assertTrue(call("orderStatus").getBoolean("initialized"))
        assertEquals(original, call("orderRead").getString("payload"))
        call("orderInitialize", orders("Устаревший cache"))
        assertEquals(original, call("orderRead").getString("payload"))
        assertNotNull(database.legacyStorageShadowDao().get(MPosOrderStorage.AUTHORITY_KEY))
    }

    @Test fun writeReadAndRemoveAreFifoAndDoNotReimportAfterRemoval() {
        send("orderInitialize", "init", orders("Первый"))
        send("orderWrite", "write", orders("Новый"))
        send("orderRead", "read")
        assertTrue(reply("init").getBoolean("ok")); assertTrue(reply("write").getBoolean("ok"))
        assertEquals(orders("Новый"), reply("read").getString("payload"))
        assertTrue(call("orderRemove").getBoolean("ok"))
        assertFalse(call("orderRead").getBoolean("found"))
        call("orderInitialize", orders("Не импортировать повторно"))
        assertFalse(call("orderRead").getBoolean("found"))
    }

    @Test fun absentAndStoredNullRemainDifferentAndPreserveDefaultSeedingContract() {
        assertTrue(call("orderInitialize").getBoolean("ok"))
        assertFalse(call("orderRead").getBoolean("found"))
        assertTrue(call("orderWrite", "null").getBoolean("ok"))
        val read = call("orderRead")
        assertTrue(read.getBoolean("found")); assertEquals("null", read.getString("payload"))
    }

    @Test fun obsoleteShadowPutAndDeleteCannotOverwriteNativeAuthority() {
        call("orderInitialize", orders("Основной"))
        send("put", "obsolete", orders("Cache"), "orders")
        assertTrue(reply("obsolete").getBoolean("ignored"))
        send("remove", "obsolete-remove", key = "orders")
        assertTrue(reply("obsolete-remove").getBoolean("ignored"))
        assertEquals(orders("Основной"), call("orderRead").getString("payload"))
    }

    @Test fun failedMigrationRollsBackAuthorityMarkerDocumentAndIndexesAndCanRetry() = runBlocking {
        send("put", "old", orders("До миграции"), "orders"); reply("old")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_order BEFORE INSERT ON order_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("orderInitialize", orders("Нельзя сохранить")).getBoolean("ok"))
        assertNull(database.legacyStorageShadowDao().get(MPosOrderStorage.AUTHORITY_KEY))
        assertEquals(orders("До миграции"), database.legacyStorageShadowDao().get("orders")!!.payload)
        assertEquals("До миграции", database.orderProjectionDao().allOrders().single().employeeName)
        assertFalse(call("orderRead").getBoolean("ok"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_order")
        assertTrue(call("orderInitialize", orders("Повторная миграция")).getBoolean("ok"))
    }

    @Test fun primaryWriteAndDeleteFailuresLeavePreviousCommittedReceiptReadable() = runBlocking {
        val original = orders("Сохранённый")
        call("orderInitialize", original)
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_order BEFORE INSERT ON order_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("orderWrite", orders("Не сохранённый")).getBoolean("ok"))
        assertEquals(original, call("orderRead").getString("payload"))
        assertEquals("Сохранённый", database.orderProjectionDao().allOrders().single().employeeName)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_order")
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON order_projection BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        assertFalse(call("orderRemove").getBoolean("ok"))
        assertEquals(original, call("orderRead").getString("payload"))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_delete")
        assertTrue(call("orderWrite", orders("Восстановленный")).getBoolean("ok"))
    }

    @Test fun fullDocumentRetainsUnknownFieldsOrderDuplicateOrMissingIdsRatherThanUsingLossyIndexes() {
        val source = "[{\"id\":\"same\",\"name\":\"Кофе ☕\",\"custom\":null},{\"name\":\"Без ID\"},{\"id\":\"same\",\"name\":\"Другой\"}]"
        call("orderInitialize", source)
        assertEquals(source, call("orderRead").getString("payload"))
        for (invalid in listOf("[", "{}", "[] trailing")) assertFalse(call("orderWrite", invalid).getBoolean("ok"))
        assertEquals(source, call("orderRead").getString("payload"))
    }

    @Test fun fileBackedReceiptAndAuthoritySurviveReopenWithoutImportingStaleLegacy() {
        call("orderInitialize", orders("Первый"))
        call("orderWrite", orders("Последний"))
        mirror.close(); scope.cancel(); database.close()
        open()
        assertTrue(call("orderStatus").getBoolean("initialized"))
        call("orderInitialize", orders("Устаревший legacy"))
        assertEquals(orders("Последний"), call("orderRead").getString("payload"))
    }
    @Test fun nestedLineOrPaymentFailureRollsBackDocumentAndEveryIndex() = runBlocking {
        val original = orders("Сохранённый чек")
        assertTrue(call("orderInitialize", original).getBoolean("ok"))
        for (table in listOf("order_line_projection", "payment_projection")) {
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_nested BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            assertFalse(call("orderWrite", orders("Новый чек")).getBoolean("ok"))
            assertEquals(original, call("orderRead").getString("payload"))
            assertEquals("Сохранённый чек", database.orderProjectionDao().allOrders().single().employeeName)
            assertEquals(1, database.orderProjectionDao().allLines().size)
            assertEquals(listOf(5.0, 7.35), database.orderProjectionDao().allPayments().map { it.amount })
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_nested")
        }
    }

    @Test fun fullReturnAndLoyaltyFieldsSurviveReopenWithOriginalSaleAndPayments() = runBlocking {
        val returned = JSONArray(orders("Возврат")).also {
            it.getJSONObject(0).put("returnedAt", 2000L).put("returnedShiftId", "s2").put("returnAmount", 12.35)
                .put("loyaltyReversal", JSONObject().put("status", "pending"))
        }.toString()
        assertTrue(call("orderInitialize", returned).getBoolean("ok"))
        mirror.close(); scope.cancel(); database.close(); open()
        assertEquals(returned, call("orderRead").getString("payload"))
        val order = database.orderProjectionDao().allOrders().single()
        assertEquals(2000L, order.returnedAt)
        assertEquals(12.35, order.returnAmount, 0.0)
        assertEquals(12.35, order.total, 0.0)
        assertEquals("pending", order.loyaltyReversalStatus)
        assertEquals(2, database.orderProjectionDao().allPayments().size)
    }

}
