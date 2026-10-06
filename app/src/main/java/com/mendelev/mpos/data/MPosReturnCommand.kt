package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import kotlin.math.floor

/** Full returns of receipts with historical stock consumption; no external effects here. */
class MPosReturnCommand(private val database: MPosDatabase) {
    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw)
        val command = parser.nextValue()
        require(command is JSONObject && parser.nextClean() == '\u0000')
        val receipt = command.getJSONObject("receipt")
        val id = receipt.getString("id")
        require(receipt.opt("id") is String && id.isNotBlank() && id == id.trim())
        val at = receipt.getLong("returnedAt")
        require(at > 0)
        val documents = database.legacyStorageShadowDao()
        val orders = database.orderProjectionDao()
        val marker = "mpos_return_command_v1:$id:$at"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        documents.get(marker)?.let {
            check(it.payload == hash) { "return has a different command" }
            check(orders.get(id)?.let { row -> JSONObject(row.payload).optLong("returnedAt") == at } == true) { "returned receipt was restored or removed; reload data" }
            return@withTransaction acknowledgement(id, true)
        }
        val catalog = MPosCatalogStorage(database)
        val shifts = MPosShiftStorage(database)
        val archive = MPosOrderStorage(database)
        val recovery = MPosRecoveryStorage(database)
        check(catalog.isAuthoritative() && shifts.isAuthoritative() && archive.isAuthoritative() && recovery.isAuthoritative("criticalStorageJournal"))
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        check(orders.orderCount() == command.getInt("expectedOrderCount")) { "stale receipt archive" }
        val original = JSONObject(requireNotNull(orders.get(id)) { "receipt not found" }.payload)
        check(original.optLong("returnedAt") == 0L && same(original, command.getJSONObject("expectedReceipt"))) { "receipt already returned or changed" }
        val beforeProducts = JSONArray(catalog.read().getString("payload"))
        val beforeShifts = shifts.readRecords()
        val expected = command.getJSONObject("expected")
        check(same(beforeProducts, expected.getJSONArray("products")) && same(beforeShifts, expected.getJSONArray("shifts"))) { "return data changed" }
        val shiftId = receipt.getString("returnedShiftId")
        val shift = (0 until beforeShifts.length()).map { beforeShifts.getJSONObject(it) }.single { it.optString("id") == shiftId }
        check(shift.optString("status") == "open") { "return shift is not open" }
        val total = floor(number(original, "total") * 100 + 0.5) / 100
        require(total.isFinite() && total >= 0)
        val parts = original.optJSONArray("payments")
        val cash = if (parts == null) { if (original.optString("method") == "cash") total else 0.0 }
            else (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.optString("method") == "cash" }.sumOf { number(it, "amount").also { value -> require(value >= 0) } }
        require(cash.isFinite() && cash >= 0 && cash <= total + 0.0001)
        if (cash > 0) {
            val balance = MPosShiftAccounting.balance(shift, JSONArray(orders.allOrders().map { JSONObject(it.payload) }))
            require(balance.isFinite() && balance >= 0 && cash <= balance + 0.0001) { "insufficient refund cash" }
        }
        val afterProducts = JSONArray(beforeProducts.toString())
        val saved = original.getJSONObject("stockConsumption")
        require(saved.getInt("version") == 1)
        val consumed = saved.getJSONArray("items")
        val seen = mutableSetOf<String>()
        for (i in 0 until consumed.length()) {
            val item = consumed.getJSONObject(i)
            val productId = item.getString("productId")
            require(seen.add(productId))
            val qty = number(item, "qty"); require(qty > 0)
            val product = (0 until afterProducts.length()).map { afterProducts.getJSONObject(it) }.single { it.optString("id") == productId }
            require(product.optString("type") == "simple")
            val stock = number(product, "stock") + qty
            require(stock.isFinite())
            val rounded = floor((stock + Math.ulp(1.0)) * 1000 + 0.5) / 1000
            require(rounded.isFinite())
            product.put("stock", rounded)
        }
        check(same(afterProducts, command.getJSONArray("products"))) { "restored stock differs" }
        val returned = JSONObject(original.toString()).put("returnedAt", at).put("returnedShiftId", shiftId).put("returnAmount", total)
        if (original.optJSONObject("customer")?.opt("id")?.let { it !== JSONObject.NULL && it != "" && it != false && it != 0 } == true) {
            returned.put("loyaltyReversal", JSONObject().put("status", "pending").put("at", at))
        }
        check(same(returned, receipt)) { "returned receipt differs" }
        val afterShifts = JSONArray(beforeShifts.toString())
        val next = (0 until afterShifts.length()).map { afterShifts.getJSONObject(it) }.single { it.optString("id") == shiftId }
        val movements = next.optJSONArray("cashMovements") ?: JSONArray().also { next.put("cashMovements", it) }
        if (cash > 0) {
            val movement = command.getJSONObject("refundMovement")
            require(movement.optString("id").isNotBlank() && movement.optString("type") == "withdrawal" && movement.optString("subtype") == "refund")
            require(number(movement, "amount") == cash && movement.getLong("timestamp") == at && movement.optString("note") == "Возврат чека")
            movements.put(movement)
        } else require(!command.has("refundMovement"))
        check(same(afterShifts, command.getJSONArray("shifts"))) { "refund movement differs" }
        catalog.write(afterProducts.toString())
        shifts.write(afterShifts.toString())
        archive.replaceReturnedReceipt(returned)
        documents.upsert(LegacyStorageShadowEntity(marker, hash, System.currentTimeMillis()))
        acknowledgement(id, false)
    }

    private fun acknowledgement(id: String, replayed: Boolean) = JSONObject().put("ok", true).put("authoritative", true)
        .put("source", "room-return").put("receiptId", id).put("replayed", replayed)
    private fun number(record: JSONObject, key: String) = record.getDouble(key).also { require(it.isFinite()) }
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }
}
