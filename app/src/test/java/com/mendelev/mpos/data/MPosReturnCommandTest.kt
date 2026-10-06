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
class MPosReturnCommandTest {
    private lateinit var database: MPosDatabase
    private val name = "return-${UUID.randomUUID()}.db"
    private val products = JSONArray("""[{"id":"p1","type":"simple","stock":0.2,"noStockTracking":true,"custom":true}]""")
    private val shifts = JSONArray("""[{"id":"old","status":"closed","openingCash":100,"cashMovements":[]},{"id":"new","status":"open","openingCash":100,"cashMovements":[]}]""")
    private val receipt = JSONObject("""{"id":"o1","shiftId":"old","method":"split","total":20,"timestamp":1000,"payments":[{"method":"cash","amount":8},{"method":"card","amount":12}],"items":[{"productId":"recipe","qty":1}],"stockConsumption":{"version":1,"items":[{"productId":"p1","qty":0.1}]},"customer":{"id":"c1"},"custom":{"keep":true}}""")
    @Before fun open() = runBlocking {
        database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        MPosCatalogStorage(database).initialize(products.toString())
        MPosShiftStorage(database).initialize(shifts.toString())
        MPosOrderStorage(database).initialize(JSONArray().put(receipt).toString())
        MPosRecoveryStorage(database).initialize("criticalStorageJournal", "null")
        Unit
    }
    @After fun close() { database.close(); RuntimeEnvironment.getApplication().deleteDatabase(name) }
    private fun request(): JSONObject {
        val returned = JSONObject(receipt.toString()).put("returnedAt", 2000).put("returnedShiftId", "new").put("returnAmount", 20)
            .put("loyaltyReversal", JSONObject().put("status", "pending").put("at", 2000))
        val movement = JSONObject().put("id", "m1").put("type", "withdrawal").put("subtype", "refund").put("amount", 8).put("timestamp", 2000).put("note", "Возврат чека")
        val nextShifts = JSONArray(shifts.toString()).also { it.getJSONObject(1).getJSONArray("cashMovements").put(movement) }
        return JSONObject().put("receipt", returned).put("expectedReceipt", JSONObject(receipt.toString())).put("expectedOrderCount", 1)
            .put("expected", JSONObject().put("products", JSONArray(products.toString())).put("shifts", JSONArray(shifts.toString())))
            .put("products", JSONArray(products.toString()).also { it.getJSONObject(0).put("stock", 0.3) }).put("shifts", nextShifts).put("refundMovement", movement)
    }
    private suspend fun reject(value: JSONObject) {
        var failed = false
        try { MPosReturnCommand(database).commit(value.toString()) } catch (_: Exception) { failed = true }
        assertTrue("invalid return must fail", failed)
    }
    @Test fun fullReturnAtomicallyRestoresHistoricalStockAndAttributesMixedPayment() = runBlocking {
        val command = request()
        MPosReturnCommand(database).commit(command.toString())
        val archive = JSONArray(MPosOrderStorage(database).read().getString("payload"))
        assertEquals(command.getJSONObject("receipt").toString(), archive.getJSONObject(0).toString())
        assertEquals(0.3, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        val actualShifts = JSONArray(MPosShiftStorage(database).read().getString("payload"))
        assertEquals(108.0, MPosShiftAccounting.balance(actualShifts.getJSONObject(0), archive), 0.0)
        assertEquals(92.0, MPosShiftAccounting.balance(actualShifts.getJSONObject(1), archive), 0.0)
        assertEquals(-12.0, MPosShiftAccounting.totals(actualShifts.getJSONObject(1), archive).getDouble("card"), 0.0)
        assertEquals(2, database.orderProjectionDao().allPayments().size)
        assertEquals("null", MPosRecoveryStorage(database).read("criticalStorageJournal").getString("payload"))
    }
    @Test fun lostAcknowledgementAfterReopenDoesNotDuplicateStockOrMovement() = runBlocking {
        val raw = request().toString(); MPosReturnCommand(database).commit(raw)
        database.close(); database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MPosDatabase::class.java, name).build()
        assertTrue(MPosReturnCommand(database).commit(raw).getBoolean("replayed"))
        assertEquals(1, database.shiftProjectionDao().allMovements().size)
        assertEquals(0.3, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        reject(JSONObject(raw).put("expectedOrderCount", 99))
        reject(request().also { it.getJSONObject("receipt").put("returnedAt", 3000) })
    }
    @Test fun failuresAtEveryWriteBoundaryRollBackAndAllowRetry() = runBlocking {
        for (table in listOf("product_projection", "shift_projection", "payment_projection", "legacy_storage_shadow")) {
            val condition = if (table == "legacy_storage_shadow") "WHEN NEW.key LIKE 'mpos_return_command_v1:%'" else ""
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_return BEFORE INSERT ON $table $condition BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
            reject(request())
            assertEquals(0L, JSONObject(database.orderProjectionDao().get("o1")!!.payload).optLong("returnedAt"))
            assertEquals(0.2, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
            assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
            assertNull(database.legacyStorageShadowDao().get("mpos_return_command_v1:o1:2000"))
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_return")
        }
        assertTrue(MPosReturnCommand(database).commit(request().toString()).getBoolean("ok"))
    }
    @Test fun staleDataPendingJournalClosedShiftAndInsufficientCashRejectWithoutWrites() = runBlocking {
        reject(request().put("expectedOrderCount", 0))
        reject(request().also { it.getJSONObject("expectedReceipt").put("custom", false) })
        reject(request().also { it.getJSONObject("expected").getJSONArray("products").getJSONObject(0).put("stock", 1) })
        MPosRecoveryStorage(database).write("criticalStorageJournal", """{"version":1,"writes":[]}""")
        reject(request()); MPosRecoveryStorage(database).write("criticalStorageJournal", "null")
        val low = JSONArray(shifts.toString()).also { it.getJSONObject(1).put("openingCash", 7) }
        MPosShiftStorage(database).write(low.toString())
        reject(request().also { it.getJSONObject("expected").put("shifts", low); it.getJSONArray("shifts").getJSONObject(1).put("openingCash", 7) })
        val closed = JSONArray(shifts.toString()).also { it.getJSONObject(1).put("status", "closed") }
        MPosShiftStorage(database).write(closed.toString())
        reject(request().also { it.getJSONObject("expected").put("shifts", closed); it.getJSONArray("shifts").getJSONObject(1).put("status", "closed") })
        assertEquals(0L, JSONObject(database.orderProjectionDao().get("o1")!!.payload).optLong("returnedAt"))
    }
    @Test fun restoreBeforeReturnAllowsNewReturnButRejectsOldMarkerReplay() = runBlocking {
        val old = request(); MPosReturnCommand(database).commit(old.toString())
        MPosOrderStorage(database).write(JSONArray().put(receipt).toString())
        MPosCatalogStorage(database).write(products.toString()); MPosShiftStorage(database).write(shifts.toString())
        reject(old)
        val fresh = request().also {
            it.getJSONObject("receipt").put("returnedAt", 3000).getJSONObject("loyaltyReversal").put("at", 3000)
            it.getJSONObject("refundMovement").put("timestamp", 3000)
        }
        assertTrue(MPosReturnCommand(database).commit(fresh.toString()).getBoolean("ok"))
    }
    @Test fun missingHistoricalProductInvalidConsumptionAndAlteredReceiptCannotMutateData() = runBlocking {
        reject(request().also { it.getJSONObject("receipt").put("total", 100) })
        reject(request().also { it.getJSONObject("refundMovement").put("amount", 9) })
        reject(request().also { it.getJSONArray("products").getJSONObject(0).put("stock", 900) })
        MPosCatalogStorage(database).write("[]")
        reject(request().also { it.getJSONObject("expected").put("products", JSONArray()); it.put("products", JSONArray()) })
        assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
    }
    @Test fun cardOnlyReturnNeedsNoCashMovementAndSupportsEmptyConsumption() = runBlocking {
        val card = JSONObject(receipt.toString()).put("method", "card").put("payments", JSONArray().put(JSONObject().put("method", "card").put("amount", 20)))
            .put("stockConsumption", JSONObject().put("version", 1).put("items", JSONArray()))
        MPosOrderStorage(database).write(JSONArray().put(card).toString())
        val command = request().also {
            it.put("expectedReceipt", card)
            it.put("receipt", JSONObject(card.toString()).put("returnedAt", 2000).put("returnedShiftId", "new").put("returnAmount", 20).put("loyaltyReversal", JSONObject().put("status", "pending").put("at", 2000)))
            it.put("products", products); it.put("shifts", shifts); it.remove("refundMovement")
        }
        MPosReturnCommand(database).commit(command.toString())
        assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
    }
    @Test fun replacingOneReceiptPreservesArchivePositionAndNeverTouchesOtherRows() = runBlocking {
        val other = JSONObject().put("id", "other").put("custom", "untouched")
        MPosOrderStorage(database).write(JSONArray().put(other).put(receipt).toString())
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER preserve_other BEFORE INSERT ON order_projection WHEN NEW.id = 'other' BEGIN SELECT RAISE(ABORT, 'must not rewrite unrelated receipt'); END")
        MPosReturnCommand(database).commit(request().put("expectedOrderCount", 2).toString())
        val actual = JSONArray(MPosOrderStorage(database).read().getString("payload"))
        assertEquals("other", actual.getJSONObject(0).getString("id"))
        assertEquals("untouched", actual.getJSONObject(0).getString("custom"))
        assertEquals("o1", actual.getJSONObject(1).getString("id"))
        assertEquals(2000L, actual.getJSONObject(1).getLong("returnedAt"))
    }
    @Test fun malformedHistoricalConsumptionRejectsInsteadOfUsingCurrentRecipe() = runBlocking {
        for (bad in listOf(
            JSONObject().put("version", 2).put("items", JSONArray()),
            JSONObject().put("version", 1).put("items", JSONArray().put(JSONObject().put("productId", "p1").put("qty", -1))),
            JSONObject().put("version", 1).put("items", JSONArray().put(JSONObject().put("productId", "p1").put("qty", 0.1)).put(JSONObject().put("productId", "p1").put("qty", 0.1)))
        )) {
            val invalid = JSONObject(receipt.toString()).put("stockConsumption", bad)
            MPosOrderStorage(database).write(JSONArray().put(invalid).toString())
            reject(request().also {
                it.put("expectedReceipt", invalid)
                it.getJSONObject("receipt").put("stockConsumption", bad)
            })
            assertEquals(0.2, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
            assertTrue(database.shiftProjectionDao().allMovements().isEmpty())
        }
    }

}
