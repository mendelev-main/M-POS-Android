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
class MPosPaymentCommandTest {
    private lateinit var database: MPosDatabase
    private val name = "payment-command-${UUID.randomUUID()}.db"
    private val products = JSONArray("""[{"id":"p1","type":"simple","stock":5.125,"price":12.35,"custom":true}]""")
    private val shifts = JSONArray("""[{"id":"s1","status":"open","employeeId":"e1","openingCash":100,"cashMovements":[]}]""")
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosCatalogStorage(database).initialize(products.toString())
        MPosShiftStorage(database).initialize(shifts.toString())
        MPosOrderStorage(database).initialize("[]")
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
        MPosRecoveryStorage(database).initialize("currentOrderSession", """{"items":[{"productId":"p1","qty":1}],"custom":true}""")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun command(delivery: Boolean = false): JSONObject {
        val total = if (delivery) 14.35 else 12.35
        val order = JSONObject().put("id", "o1").put("shiftId", "s1").put("employeeId", "e1").put("receiptNumber", 1)
            .put("method", "split").put("total", total).put("timestamp", 1000L).put("deliveryFee", if (delivery) 2.0 else 0.0)
            .put("orderType", if (delivery) "Доставка" else "На месте")
            .put("payments", JSONArray().put(JSONObject().put("method", "cash").put("amount", 5.0).put("cashGiven", 10.0).put("change", 5.0))
                .put(JSONObject().put("method", "card").put("amount", total - 5).put("cashGiven", JSONObject.NULL).put("change", JSONObject.NULL)))
            .put("items", JSONArray().put(JSONObject().put("productId", "p1").put("qty", 1).put("price", 12.35).put("modifiers", JSONArray())))
            .put("stockConsumption", JSONObject().put("version", 1).put("items", JSONArray().put(JSONObject().put("productId", "p1").put("qty", 0.125))))
            .put("loyaltySync", JSONObject().put("status", "pending")).put("custom", JSONObject().put("preserved", true))
        val afterProducts = JSONArray(products.toString()).also { it.getJSONObject(0).put("stock", 5.0) }
        val afterShifts = JSONArray(shifts.toString())
        val result = JSONObject().put("order", order).put("products", afterProducts).put("shifts", afterShifts)
            .put("session", JSONObject().put("items", JSONArray())).put("expectedOrderCount", 0)
            .put("expected", JSONObject().put("products", JSONArray(products.toString())).put("shifts", JSONArray(shifts.toString())))
        if (delivery) {
            val movement = JSONObject().put("id", "m1").put("type", "withdrawal").put("subtype", "delivery").put("amount", 2.0).put("timestamp", 1000L).put("note", "🚗 Доставка")
            afterShifts.getJSONObject(0).getJSONArray("cashMovements").put(movement); result.put("deliveryMovement", movement)
        }
        return result
    }
    private suspend fun reject(value: JSONObject) {
        var failed = false
        try { MPosPaymentCommand(database).commit(value.toString()) } catch (_: Exception) { failed = true }
        assertTrue("invalid payment must fail", failed)
    }

    @Test fun mixedPaymentAtomicallyPersistsStockReceiptSessionAndPreservesUnknownFields() = runBlocking {
        val request = command(); assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("ok"))
        assertEquals(5.0, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        val receipt = JSONArray(MPosOrderStorage(database).read().getString("payload")).getJSONObject(0)
        assertEquals(request.getJSONObject("order").toString(), receipt.toString())
        assertEquals(2, database.orderProjectionDao().allPayments().size)
        assertEquals(0, JSONObject(MPosRecoveryStorage(database).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
        assertEquals("null", MPosRecoveryStorage(database).read("criticalStorageJournal").getString("payload"))
    }

    @Test fun sameCommandAfterReopenIsIdempotentButChangedCommandWithSameIdIsRejected() = runBlocking {
        val request = command(true).toString(); MPosPaymentCommand(database).commit(request)
        database.close(); database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        assertTrue(MPosPaymentCommand(database).commit(request).getBoolean("replayed"))
        assertEquals(1, database.orderProjectionDao().orderCount())
        assertEquals(1, database.shiftProjectionDao().allMovements().size)
        assertEquals(5.0, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        reject(JSONObject(request).put("expectedOrderCount", 999))
    }

    @Test fun everyTransactionFailureLeavesAllOriginalDataAndAllowsRetry() = runBlocking {
        for (table in listOf("product_projection", "shift_projection", "payment_projection", "current_order_session_projection", "legacy_storage_shadow")) {
            val condition = if (table == "legacy_storage_shadow") "WHEN NEW.key LIKE 'mpos_payment_command_v1:%'" else ""
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_payment BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            reject(command(true))
            assertEquals(0, database.orderProjectionDao().orderCount())
            assertEquals(5.125, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
            assertEquals(1, JSONObject(MPosRecoveryStorage(database).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
            assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
            assertNull(database.legacyStorageShadowDao().get("mpos_payment_command_v1:o1"))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_payment")
        }
        assertTrue(MPosPaymentCommand(database).commit(command(true).toString()).getBoolean("ok"))
    }

    @Test fun staleSnapshotsClosedShiftPendingJournalAndWrongSequencePreventAnyPayment() = runBlocking {
        reject(command().put("expectedOrderCount", 1))
        reject(command().also { it.getJSONObject("expected").getJSONArray("products").getJSONObject(0).put("stock", 999) })
        reject(command().also { it.getJSONObject("order").put("receiptNumber", 2) })
        MPosRecoveryStorage(database).write("criticalStorageJournal", """{"version":1,"writes":[{"key":"orders","value":[]}]}""")
        reject(command()); MPosRecoveryStorage(database).write("criticalStorageJournal", "null")
        MPosShiftStorage(database).write(JSONArray(shifts.toString()).also { it.getJSONObject(0).put("status", "closed") }.toString())
        reject(command()); assertEquals(0, database.orderProjectionDao().orderCount())
    }

    @Test fun invalidPaymentTotalsChangeAndInsufficientStockFailBeforeWrites() = runBlocking {
        reject(command().also { it.getJSONObject("order").put("total", 99) })
        reject(command().also { it.getJSONObject("order").getJSONArray("payments").getJSONObject(0).put("change", 4) })
        reject(command().also { it.getJSONObject("order").getJSONArray("payments").getJSONObject(1).put("cashGiven", 1) })
        reject(command().also { it.getJSONObject("order").getJSONObject("stockConsumption").getJSONArray("items").getJSONObject(0).put("qty", 6) })
        assertEquals(0, database.orderProjectionDao().orderCount())
    }

    @Test fun deliveryRequiresCashDrawerAndOnlyOneExpectedMovement() = runBlocking {
        val emptyShifts = JSONArray(shifts.toString()).also { it.getJSONObject(0).put("openingCash", 0) }
        MPosShiftStorage(database).write(emptyShifts.toString())
        val request = command(true).also {
            it.getJSONObject("expected").put("shifts", emptyShifts)
            it.getJSONArray("shifts").getJSONObject(0).put("openingCash", 0)
            it.getJSONObject("order").getJSONArray("payments").getJSONObject(0).put("amount", 0).put("change", 10)
            it.getJSONObject("order").getJSONArray("payments").getJSONObject(1).put("amount", 14.35)
        }
        reject(request); assertEquals(0, database.orderProjectionDao().orderCount())
    }
    @Test fun fractionalStockAndBinaryCurrencyRoundingAlsoPermitZeroTotalRewardSale() = runBlocking {
        val baseline = JSONArray(products.toString()).also { it.getJSONObject(0).put("stock", 0.3) }
        MPosCatalogStorage(database).write(baseline.toString())
        val request = command().also {
            it.getJSONObject("expected").put("products", baseline)
            it.getJSONArray("products").getJSONObject(0).put("stock", 0.2)
            val order = it.getJSONObject("order"); order.put("total", 0.3)
            order.getJSONObject("stockConsumption").getJSONArray("items").getJSONObject(0).put("qty", 0.1)
            order.getJSONArray("payments").getJSONObject(0).put("amount", 0.1).put("cashGiven", JSONObject.NULL).put("change", JSONObject.NULL)
            order.getJSONArray("payments").getJSONObject(1).put("amount", 0.2)
        }
        MPosPaymentCommand(database).commit(request.toString())
        assertEquals(0.2, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        val free = command().also {
            it.put("expectedOrderCount", 1)
            it.getJSONObject("expected").put("products", JSONArray(it.getJSONArray("products").toString()).also { rows -> rows.getJSONObject(0).put("stock", 0.2) })
            it.getJSONArray("products").getJSONObject(0).put("stock", 0.1)
            val order = it.getJSONObject("order"); order.put("id", "o2").put("receiptNumber", 2).put("method", "card").put("total", 0)
            order.put("payments", JSONArray().put(JSONObject().put("method", "card").put("amount", 0).put("cashGiven", JSONObject.NULL).put("change", JSONObject.NULL)))
            order.getJSONObject("stockConsumption").put("items", JSONArray().put(JSONObject().put("productId", "p1").put("qty", 0.1)))
        }
        assertTrue(MPosPaymentCommand(database).commit(free.toString()).getBoolean("ok"))
        assertEquals(2, database.orderProjectionDao().orderCount())
    }

    @Test fun crossShiftRefundReducesCashAvailableForDelivery() = runBlocking {
        val baseline = JSONArray(shifts.toString())
        val movement = JSONObject().put("id", "refund1").put("type", "withdrawal").put("subtype", "refund").put("amount", 20).put("timestamp", 2000)
        baseline.getJSONObject(0).put("openingCash", 21).getJSONArray("cashMovements").put(movement)
        MPosShiftStorage(database).write(baseline.toString())
        MPosOrderStorage(database).write("""[{"id":"old","shiftId":"previous","total":20,"method":"cash","returnedAt":2000,"returnedShiftId":"s1","returnAmount":20}]""")
        val request = command(true).also {
            it.put("expectedOrderCount", 1)
            it.getJSONObject("expected").put("shifts", baseline)
            val next = JSONArray(baseline.toString())
            next.getJSONObject(0).getJSONArray("cashMovements").put(it.getJSONObject("deliveryMovement"))
            it.put("shifts", next)
            it.getJSONObject("order").getJSONArray("payments").getJSONObject(0).put("amount", 0).put("change", 10)
            it.getJSONObject("order").getJSONArray("payments").getJSONObject(1).put("amount", 14.35)
        }
        reject(request)
        assertEquals(1, database.orderProjectionDao().orderCount())
        assertEquals(1, database.shiftProjectionDao().allMovements().size)
        assertEquals(5.125, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
    }

}
