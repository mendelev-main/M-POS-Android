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

    private fun withPricing(request: JSONObject): JSONObject {
        val order = request.getJSONObject("order")
        order.put("productDiscountTotal", 0.0).put("subtotalBeforeDiscounts", 12.35).put("loyaltyDiscount", 0.0)
        return request.put("pricing", JSONObject().put("version", 1).put("discounts", JSONArray()).put("loyaltyDiscount", 0.0))
    }
    private fun withLoyalty(request: JSONObject, programs: JSONArray = JSONArray(), redemptions: JSONObject = JSONObject()): JSONObject {
        val order = request.getJSONObject("order")
        val allocation = MPosLoyaltyRewardEngine.calculate(order.getJSONArray("items"), programs, redemptions)
        order.put("loyaltyRedemptions", redemptions).put("loyaltyRewardAllocations", allocation.getJSONObject("allocations"))
            .put("loyaltyDiscount", allocation.getJSONObject("snapshot").getDouble("discount"))
            .put("loyaltyProgramsApplied", allocation.getJSONObject("snapshot").getJSONArray("programs"))
        return request.put("loyalty", JSONObject().put("version", 1).put("programs", programs))
    }
    private fun giftPrograms() = JSONArray("""[{"id":"gift","name":"Synthetic gift","loyalty_reward_products":[{"product_id":"p1"}]}]""")
    private fun giftCommand(): JSONObject {
        val request = withPricing(command(true)); val order = request.getJSONObject("order")
        order.getJSONArray("items").getJSONObject(0).put("price", 10).put("discountId", "sale")
        withLoyalty(request, giftPrograms(), JSONObject("""{"gift":1}"""))
        order.put("total", 0).put("productDiscountTotal", 2).put("subtotalBeforeDiscounts", 10)
        order.getJSONArray("payments").getJSONObject(0).put("amount", 0).put("cashGiven", 0).put("change", 0)
        order.getJSONArray("payments").getJSONObject(1).put("amount", 0)
        request.getJSONObject("pricing").put("loyaltyDiscount", 10).put("discounts", JSONArray("""[{"id":"sale","type":"percent","value":20}]"""))
        return request
    }
    @Test fun nativeGiftAndDiscountPreserveDeliveryAndReplayAtomicity() = runBlocking {
        val request = giftCommand()
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("ok"))
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("replayed"))
        val receipt = JSONArray(MPosOrderStorage(database).read().getString("payload")).getJSONObject(0)
        assertEquals(0.0, receipt.getDouble("total"), 0.0)
        assertEquals(10.0, receipt.getDouble("loyaltyDiscount"), 0.0)
        assertEquals("p1", receipt.getJSONObject("loyaltyRewardAllocations").getJSONArray("gift").getJSONObject(0).getString("productId"))
        assertFalse(receipt.has("loyalty")); assertFalse(receipt.has("pricing"))
        assertTrue(receipt.getJSONObject("custom").getBoolean("preserved"))
        assertEquals(1, database.orderProjectionDao().orderCount())
        assertEquals(5.0, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        assertEquals(1, JSONArray(MPosShiftStorage(database).read().getString("payload")).getJSONObject(0).getJSONArray("cashMovements").length())
    }
    @Test fun incorrectNativeGiftFailsBeforeAnyStockReceiptSessionOrShiftWrites() = runBlocking {
        reject(giftCommand().also { it.getJSONObject("order").put("loyaltyDiscount", 9) })
        reject(giftCommand().also { it.getJSONObject("order").getJSONObject("loyaltyRewardAllocations").getJSONArray("gift").getJSONObject(0).put("productId", "different") })
        reject(giftCommand().also { it.getJSONObject("order").getJSONArray("loyaltyProgramsApplied").getJSONObject(0).put("name", "changed") })
        reject(giftCommand().also { it.getJSONObject("order").getJSONObject("loyaltyRedemptions").put("gift", 2) })
        reject(giftCommand().also { it.getJSONObject("loyalty").getJSONArray("programs").getJSONObject(0).put("loyalty_reward_products", JSONArray()) })
        reject(giftCommand().put("loyalty", JSONObject.NULL))
        reject(giftCommand().also { it.getJSONObject("loyalty").put("version", 2) })
        assertEquals(0, database.orderProjectionDao().orderCount())
        assertEquals(5.125, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        assertEquals(0, JSONArray(MPosShiftStorage(database).read().getString("payload")).getJSONObject(0).getJSONArray("cashMovements").length())
        assertEquals(1, JSONObject(MPosRecoveryStorage(database).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
    }
    private suspend fun recipeCommand(): JSONObject {
        val request = command().put("recipeConsumption", JSONObject().put("version", 1))
        val catalogue = JSONArray(products.toString()).put(JSONObject("""{"id":"recipe","type":"composite","components":[{"productId":"p1","qty":0.1}]}"""))
            .put(JSONObject("""{"id":"addon","type":"composite","components":[{"productId":"p1","qty":0.025}]}"""))
        MPosCatalogStorage(database).write(catalogue.toString())
        request.getJSONObject("expected").put("products", JSONArray(catalogue.toString()))
        request.put("products", JSONArray(catalogue.toString()).also { it.getJSONObject(0).put("stock", 5.0) })
        request.getJSONObject("order").getJSONArray("items").getJSONObject(0).put("productId", "recipe")
            .put("selectedModifiers", JSONArray("""[{"productId":"addon","qty":1}]"""))
        return request
    }
    @Test fun nativeRecipeAndModifierExpansionSettlesAndRetainsHistoricalSnapshotAfterRecipeChange() = runBlocking {
        val request = recipeCommand()
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("ok"))
        val receipt = JSONArray(MPosOrderStorage(database).read().getString("payload")).getJSONObject(0)
        assertEquals(0.125, receipt.getJSONObject("stockConsumption").getJSONArray("items").getJSONObject(0).getDouble("qty"), 0.0)
        val changed = JSONArray(MPosCatalogStorage(database).read().getString("payload"))
        changed.getJSONObject(1).getJSONArray("components").getJSONObject(0).put("qty", 4)
        MPosCatalogStorage(database).write(changed.toString())
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("replayed"))
        assertEquals(receipt.toString(), JSONArray(MPosOrderStorage(database).read().getString("payload")).getJSONObject(0).toString())
        assertEquals(5.0, changed.getJSONObject(0).getDouble("stock"), 0.0)
        assertFalse(receipt.has("recipeConsumption"))
    }
    @Test fun wrongConsumptionOrInvalidRecipeRejectsWithoutFinancialWrites() = runBlocking {
        val request = recipeCommand()
        request.getJSONObject("order").getJSONObject("stockConsumption").getJSONArray("items").getJSONObject(0).put("qty", 0.1)
        reject(request)
        request.getJSONObject("order").getJSONObject("stockConsumption").getJSONArray("items").getJSONObject(0).put("qty", 0.125)
        reject(request.put("recipeConsumption", JSONObject.NULL))
        request.put("recipeConsumption", JSONObject().put("version", 1))
        val cycle = JSONArray(request.getJSONObject("expected").getJSONArray("products").toString())
        cycle.getJSONObject(1).getJSONArray("components").getJSONObject(0).put("productId", "recipe")
        MPosCatalogStorage(database).write(cycle.toString()); request.getJSONObject("expected").put("products", cycle)
        reject(request)
        assertEquals(0, database.orderProjectionDao().orderCount())
        assertEquals(5.125, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        assertEquals(1, JSONObject(MPosRecoveryStorage(database).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
    }
    @Test fun configuredUnitPriceIsPersistedUsingFrozenBaseAndModifiers() = runBlocking {
        val request = withLoyalty(withPricing(command())).put("configuredPrices", JSONObject().put("version", 1))
        request.getJSONObject("order").getJSONArray("items").getJSONObject(0).put("basePrice", 10).put("manualPrice", false)
            .put("selectedModifiers", JSONArray("""[{"priceDelta":2.35,"qty":3}]"""))
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("ok"))
        val receipt = JSONArray(MPosOrderStorage(database).read().getString("payload")).getJSONObject(0)
        assertEquals(12.35, receipt.getJSONArray("items").getJSONObject(0).getDouble("price"), 0.0)
        assertFalse(receipt.has("configuredPrices"))
    }
    @Test fun configuredPriceMismatchAndMalformedEnvelopeCannotWriteAnyPaymentData() = runBlocking {
        val request = withPricing(command()).put("configuredPrices", JSONObject().put("version", 1))
        request.getJSONObject("order").getJSONArray("items").getJSONObject(0).put("basePrice", 20)
        reject(request)
        reject(command().put("configuredPrices", JSONObject.NULL))
        assertEquals(0, database.orderProjectionDao().orderCount())
        assertEquals(5.125, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        assertEquals(1, JSONObject(MPosRecoveryStorage(database).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
    }
    @Test fun paymentWithoutGiftAcceptsEmptyNativeLoyaltySnapshot() = runBlocking {
        assertTrue(MPosPaymentCommand(database).commit(withLoyalty(withPricing(command())).toString()).getBoolean("ok"))
    }
    @Test fun nativePricingCommitsAndExactReplayDoesNotDuplicateReceipt() = runBlocking {
        val request = withPricing(command(true))
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("ok"))
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("replayed"))
        assertEquals(1, database.orderProjectionDao().orderCount())
    }
    @Test fun discountDeliveryAndAllocatedGiftSettleTogetherWithoutChangingStockRules() = runBlocking {
        val request = withPricing(command(true)); val order = request.getJSONObject("order")
        order.getJSONArray("items").getJSONObject(0).put("price", 10).put("discountId", "d1")
        order.put("total", 7).put("productDiscountTotal", 2).put("subtotalBeforeDiscounts", 10).put("loyaltyDiscount", 3)
        order.getJSONArray("payments").getJSONObject(1).put("amount", 2)
        request.getJSONObject("pricing").put("loyaltyDiscount", 3).put("discounts", JSONArray().put(JSONObject().put("id", "d1").put("type", "percent").put("value", 20)))
        assertTrue(MPosPaymentCommand(database).commit(request.toString()).getBoolean("ok"))
        val receipt = JSONArray(MPosOrderStorage(database).read().getString("payload")).getJSONObject(0)
        assertEquals(7.0, receipt.getDouble("total"), 0.0)
        assertFalse(receipt.has("pricing"))
        assertEquals(5.0, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
    }
    @Test fun malformedPresentPricingCannotUseCompatibilityBypass() = runBlocking {
        reject(command().put("pricing", JSONObject.NULL))
        reject(withPricing(command()).also { it.getJSONObject("pricing").put("version", 2) })
        assertEquals(0, database.orderProjectionDao().orderCount())
    }
    @Test fun pricingMismatchRollsBackStockReceiptAndCart() = runBlocking {
        val request = withPricing(command())
        request.getJSONObject("order").getJSONArray("items").getJSONObject(0).put("price", 20)
        reject(request)
        assertEquals(0, database.orderProjectionDao().orderCount())
        assertEquals(5.125, JSONArray(MPosCatalogStorage(database).read().getString("payload")).getJSONObject(0).getDouble("stock"), 0.0)
        assertEquals(1, JSONObject(MPosRecoveryStorage(database).read("currentOrderSession").getString("payload")).getJSONArray("items").length())
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
