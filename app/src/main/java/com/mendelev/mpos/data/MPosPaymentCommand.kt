package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Atomic local settlement; pricing, recipe expansion and loyalty allocation remain parity inputs. */
class MPosPaymentCommand(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val orders = database.orderProjectionDao()
    private val catalog = MPosCatalogStorage(database)
    private val shifts = MPosShiftStorage(database)
    private val recovery = MPosRecoveryStorage(database)
    private val archive = MPosOrderStorage(database)

    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw)
        val command = parser.nextValue()
        require(command is JSONObject && parser.nextClean() == '\u0000') { "invalid payment command" }
        val order = command.getJSONObject("order")
        val id = order.getString("id")
        require(order.opt("id") is String)
        require(id.isNotBlank() && id == id.trim())
        val marker = "mpos_payment_command_v1:$id"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        documents.get(marker)?.let {
            check(it.payload == hash) { "payment ID has a different command" }
            check(orders.get(id) != null) { "committed receipt was removed; reload restored data" }
            return@withTransaction acknowledgement(id, true)
        }
        check(catalog.isAuthoritative() && shifts.isAuthoritative() && archive.isAuthoritative()) { "payment domains are not initialized" }
        check(recovery.isAuthoritative("criticalStorageJournal") && recovery.isAuthoritative("currentOrderSession"))
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        check(orders.get(id) == null && orders.orderCount() == command.getInt("expectedOrderCount")) { "stale receipt archive" }
        val expected = command.getJSONObject("expected")
        val beforeProducts = JSONArray(catalog.read().getString("payload"))
        val beforeShifts = shifts.readRecords()
        check(same(beforeProducts, expected.getJSONArray("products")) && same(beforeShifts, expected.getJSONArray("shifts"))) { "local data changed before payment" }
        val shiftId = order.getString("shiftId")
        val shift = (0 until beforeShifts.length()).map { beforeShifts.getJSONObject(it) }.single { it.optString("id") == shiftId }
        check(shift.optString("status") == "open") { "shift is not open" }
        check(order.optString("employeeId") == shift.optString("employeeId")) { "shift employee changed" }
        check(order.getInt("receiptNumber") == orders.countForShift(shiftId) + 1) { "receipt sequence changed" }
        require(order.getJSONArray("items").length() > 0) { "empty receipt" }
        require(order.optLong("returnedAt") == 0L) { "new payment cannot be a returned receipt" }
        validatePayments(order)
        val afterProducts = JSONArray(beforeProducts.toString())
        val consumption = order.getJSONObject("stockConsumption")
        require(consumption.getInt("version") == 1)
        val consumed = consumption.getJSONArray("items")
        val seen = mutableSetOf<String>()
        for (index in 0 until consumed.length()) {
            val item = consumed.getJSONObject(index); val productId = item.getString("productId")
            require(seen.add(productId)) { "duplicate stock consumption" }
            val quantity = number(item, "qty"); require(quantity > 0)
            val product = (0 until afterProducts.length()).map { afterProducts.getJSONObject(it) }.single { it.optString("id") == productId }
            require(product.optString("type") == "simple" && !product.optBoolean("noStockTracking"))
            val stock = optionalNumber(product, "stock")
            val tolerance = Math.ulp(1.0) * 8 * max(abs(stock), quantity)
            require(stock + tolerance >= quantity) { "insufficient stock" }
            product.put("stock", floor((max(0.0, stock - quantity) + Math.ulp(1.0)) * 1000 + 0.5) / 1000)
        }
        check(same(afterProducts, command.getJSONArray("products"))) { "stock result differs from parity calculation" }
        val afterShifts = JSONArray(beforeShifts.toString())
        val nextShift = (0 until afterShifts.length()).map { afterShifts.getJSONObject(it) }.single { it.optString("id") == shiftId }
        val delivery = optionalNumber(order, "deliveryFee")
        require(delivery >= 0)
        if (delivery > 0) {
            require(order.optString("orderType") == "Доставка")
            val drawer = cashDrawer(shift)
            require(drawer.isFinite() && drawer >= 0) { "invalid drawer balance" }
            val cash = drawer + cashPayments(order)
            require(cash.isFinite() && cash >= 0 && delivery <= cash + 0.0001) { "insufficient delivery cash" }
            val movement = command.getJSONObject("deliveryMovement")
            require(movement.optString("id").isNotBlank() && movement.optString("type") == "withdrawal" && movement.optString("subtype") == "delivery")
            require(number(movement, "amount") == delivery)
            val movements = nextShift.optJSONArray("cashMovements") ?: JSONArray().also { nextShift.put("cashMovements", it) }
            movements.put(movement)
        }
        check(same(afterShifts, command.getJSONArray("shifts"))) { "shift result differs from parity calculation" }
        val session = command.getJSONObject("session")
        require(session.getJSONArray("items").length() == 0 && !session.has("splitPaymentDraft")) { "payment must clear the cart" }
        catalog.write(afterProducts.toString())
        shifts.write(afterShifts.toString())
        archive.appendPayment(order)
        recovery.write("currentOrderSession", session.toString())
        documents.upsert(LegacyStorageShadowEntity(marker, hash, System.currentTimeMillis()))
        acknowledgement(id, false)
    }

    private fun acknowledgement(id: String, replayed: Boolean) = JSONObject().put("ok", true).put("authoritative", true)
        .put("source", "room-payment").put("receiptId", id).put("replayed", replayed)

    private fun rounded(value: Double) = floor(value * 100 + 0.5) / 100
    private fun number(source: JSONObject, key: String): Double = source.getDouble(key).also { require(it.isFinite()) }
    private fun optionalNumber(source: JSONObject, key: String): Double = if (!source.has(key) || source.isNull(key)) 0.0 else number(source, key)

    private fun validatePayments(order: JSONObject) {
        val total = number(order, "total"); require(total >= 0)
        val parts = order.getJSONArray("payments"); require(parts.length() > 0)
        var sum = 0.0
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index); val method = part.getString("method")
            require(method in setOf("cash", "card"))
            val amount = number(part, "amount"); require(amount >= 0 && amount == rounded(amount)); sum += amount
            val given = if (part.isNull("cashGiven") || !part.has("cashGiven")) null else number(part, "cashGiven")
            val change = if (part.isNull("change") || !part.has("change")) null else number(part, "change")
            require(given == null || given >= 0); require(change == null || change >= 0)
            if (method == "card") require(given == null && change == null)
            else { require(given == null || given + 0.0001 >= amount); if (given != null && change != null) require(abs(change - (given - amount)) <= 0.001) }
        }
        require(abs(rounded(sum) - total) <= 0.001) { "payment total differs from receipt" }
        require(order.optString("method") == if (parts.length() == 1) parts.getJSONObject(0).getString("method") else "split")
    }

    private fun cashPayments(order: JSONObject): Double {
        val parts = order.optJSONArray("payments") ?: return if (order.optString("method") == "cash") optionalNumber(order, "total") else 0.0
        return (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.optString("method") == "cash" }.sumOf { optionalNumber(it, "amount") }
    }

    private suspend fun cashDrawer(shift: JSONObject): Double {
        return MPosShiftAccounting.balance(shift, JSONArray(orders.allOrders().map { JSONObject(it.payload) }))
    }

    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }
}
