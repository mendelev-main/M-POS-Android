package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns compatible receipt archives; canonical arrays use full per-receipt JSON rows. */
class MPosOrderStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val orderDao = database.orderProjectionDao()
    companion object { const val AUTHORITY_KEY = "mpos_orders_authority_v1"; const val ROWS_KEY = "mpos_orders_rows_v1" }

    suspend fun isAuthoritative(): Boolean = documents.get(AUTHORITY_KEY) != null

    suspend fun initialize(legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative()) {
            replace(legacy)
            documents.upsert(LegacyStorageShadowEntity(AUTHORITY_KEY, "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement()
    }

    private fun acknowledgement() = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-orders")

    suspend fun read(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native orders is not initialized" }
        ensureRows()
        val state = rowState()
        val payload = when (state.getString("mode")) {
            "rows" -> JSONArray(orderDao.allOrders().map { JSONObject(it.payload) }).toString()
            "null" -> "null"
            "document" -> documents.get("orders")!!.payload
            else -> null
        }
        acknowledgement().put("found", payload != null).put("payload", payload ?: JSONObject.NULL)
            .put("revision", state.optLong("revision"))

    }

    suspend fun write(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native orders is not initialized" }
        replace(serialized)
        acknowledgement()
    }

    suspend fun remove(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native orders is not initialized" }
        replace(null)
        acknowledgement()
    }

    private suspend fun replace(serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid orders JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONArray) { "orders must be an array or null" }
        val array = parsed as? JSONArray
        val ids = array?.let { rows -> (0 until rows.length()).map { rows.optJSONObject(it)?.opt("id") as? String ?: "" } }
        val canonical = ids != null && ids.all { it.isNotBlank() && it == it.trim() } && ids.toSet().size == ids.size
        val mode = when { serialized == null -> "absent"; parsed === JSONObject.NULL -> "null"; canonical -> "rows"; else -> "document" }
        if (mode == "document") documents.upsert(LegacyStorageShadowEntity("orders", serialized!!, System.currentTimeMillis()))
        else documents.delete("orders")
        if (canonical) syncRows(requireNotNull(array)) else projectArray(array ?: JSONArray())
        saveState(mode, rowState().optLong("revision") + 1)

    }

    private fun parseObject(raw: String): JSONObject {
        val parser = JSONTokener(raw)
        val value = parser.nextValue()
        require(value is JSONObject && parser.nextClean() == '\u0000') { "invalid receipt command" }
        return value
    }

    private suspend fun rowState(): JSONObject = documents.get(ROWS_KEY)?.let { JSONObject(it.payload) }
        ?: JSONObject().put("revision", 0)

    private suspend fun saveState(mode: String, revision: Long) {
        documents.upsert(LegacyStorageShadowEntity(ROWS_KEY, JSONObject().put("mode", mode).put("revision", revision).toString(), System.currentTimeMillis()))
    }

    private suspend fun ensureRows() {
        if (documents.get(ROWS_KEY) == null) replace(documents.get("orders")?.payload)
    }

    private suspend fun deleteRow(id: String) {
        orderDao.deletePayments(id); orderDao.deleteLines(id); orderDao.deleteOrder(id)
    }

    private suspend fun syncRows(source: JSONArray) {
        val old = orderDao.allOrders().associateBy { it.id }
        val positions = linkedMapOf<String, Int>()
        val changed = JSONArray()
        for (index in 0 until source.length()) {
            val receipt = source.getJSONObject(index)
            val id = receipt.getString("id")
            positions[id] = index
            if (old[id]?.payload != receipt.toString() || old[id]?.sortIndex != index) changed.put(receipt)
        }
        for (id in old.keys - positions.keys) deleteRow(id)
        if (changed.length() > 0) projectArray(changed, false, positions)
    }

    suspend fun page(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()); ensureRows()
        val options = parseObject(serialized)
        val limit = options.getInt("limit"); val offset = options.getInt("offset")
        require(limit in 1..100 && offset >= 0 && options.getDouble("limit") == limit.toDouble() && options.getDouble("offset") == offset.toDouble()) { "invalid receipt page" }
        check(rowState().getString("mode") in setOf("rows", "null", "absent")) { "archive requires compatible full read" }
        acknowledgement().put("rows", JSONArray(orderDao.page(limit, offset).map { JSONObject(it.payload) }))
            .put("total", orderDao.orderCount()).put("revision", rowState().optLong("revision"))
    }

    suspend fun upsert(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()); ensureRows()
        val envelope = parseObject(serialized)
        val state = rowState()
        check(state.getString("mode") == "rows") { "individual writes require a canonical array archive" }
        check(envelope.getLong("expectedRevision") == state.getLong("revision")) { "stale receipt revision" }
        val receipt = envelope.getJSONObject("receipt"); val id = receipt.getString("id")
        require(id.isNotBlank() && id == id.trim())
        val index = orderDao.get(id)?.sortIndex ?: (orderDao.allOrders().maxOfOrNull { it.sortIndex }?.plus(1) ?: 0)
        projectArray(JSONArray().put(receipt), false, mapOf(id to index))
        saveState("rows", state.getLong("revision") + 1)
        acknowledgement().put("revision", state.getLong("revision") + 1)
    }

    suspend fun appendPayment(receipt: JSONObject) = database.withTransaction {
        check(isAuthoritative()); ensureRows()
        val state = rowState()
        check(state.getString("mode") in setOf("rows", "null", "absent")) { "receipt archive requires compatible repair" }
        val id = receipt.getString("id")
        require(id.isNotBlank() && id == id.trim())
        check(orderDao.get(id) == null) { "receipt ID already exists" }
        val index = (orderDao.lastPosition() ?: -1) + 1
        projectArray(JSONArray().put(receipt), false, mapOf(id to index))
        saveState("rows", state.optLong("revision") + 1)
    }

    suspend fun replaceReturnedReceipt(receipt: JSONObject) = database.withTransaction {
        check(isAuthoritative()); ensureRows()
        val state = rowState()
        check(state.getString("mode") == "rows")
        val id = receipt.getString("id")
        val previous = requireNotNull(orderDao.get(id))
        projectArray(JSONArray().put(receipt), false, mapOf(id to previous.sortIndex))
        saveState("rows", state.getLong("revision") + 1)
    }

    // Missing/non-finite values are normalized only in diagnostic indexes, never in the document.
    private fun indexAmount(record: JSONObject, key: String): Double =
        record.optDouble(key, 0.0).takeIf { it.isFinite() } ?: 0.0

    suspend fun project(serialized: String) {
        projectArray(JSONArray(serialized))
    }

    private suspend fun projectArray(source: JSONArray, replaceAll: Boolean = true, positions: Map<String, Int> = emptyMap()) {
        val now = System.currentTimeMillis()
        val orders = ArrayList<OrderProjectionEntity>(source.length())
        val lines = ArrayList<OrderLineProjectionEntity>()
        val payments = ArrayList<PaymentProjectionEntity>()

        for (orderIndex in 0 until source.length()) {
            val order = source.optJSONObject(orderIndex) ?: continue
            val orderId = order.optString("id").trim()
            if (orderId.isEmpty()) continue

            orders += OrderProjectionEntity(
                id = orderId,
                shiftId = order.optString("shiftId"),
                receiptNumber = order.optInt("receiptNumber"),
                receiptDisplayNumber = order.optString("receiptDisplayNumber"),
                employeeId = order.optString("employeeId"),
                employeeName = order.optString("employeeName"),
                method = order.optString("method"),
                total = indexAmount(order, "total"),
                orderType = order.optString("orderType"),
                orderLabel = order.optString("orderLabel"),
                deliveryFee = indexAmount(order, "deliveryFee"),
                source = order.optString("source"),
                webOrderId = order.optString("webOrderId"),
                timestamp = order.optLong("timestamp"),
                returnedAt = order.optLong("returnedAt"),
                returnAmount = indexAmount(order, "returnAmount"),
                loyaltySyncStatus = order.optJSONObject("loyaltySync")?.optString("status").orEmpty(),
                loyaltyReversalStatus = order.optJSONObject("loyaltyReversal")?.optString("status").orEmpty(),
                sortIndex = positions[orderId] ?: orderIndex,
                payload = order.toString(),
                updatedAt = now,
            )

            val sourceLines = order.optJSONArray("items") ?: JSONArray()
            for (lineIndex in 0 until sourceLines.length()) {
                val line = sourceLines.optJSONObject(lineIndex) ?: continue
                lines += OrderLineProjectionEntity(
                    id = MPosOrderRepository.lineKey(orderId, lineIndex),
                    orderId = orderId,
                    productId = line.optString("productId"),
                    name = line.optString("name"),
                    category = line.optString("category"),
                    qty = indexAmount(line, "qty"),
                    price = indexAmount(line, "price"),
                    cost = indexAmount(line, "cost"),
                    discountName = line.optString("discountName"),
                    discountType = line.optString("discountType"),
                    discountValue = indexAmount(line, "discountValue"),
                    comment = line.optString("comment"),
                    sortIndex = lineIndex,
                    payload = line.toString(),
                    updatedAt = now,
                )
            }

            val sourcePayments = order.optJSONArray("payments") ?: JSONArray()
            for (paymentIndex in 0 until sourcePayments.length()) {
                val payment = sourcePayments.optJSONObject(paymentIndex) ?: continue
                payments += PaymentProjectionEntity(
                    id = MPosOrderRepository.paymentKey(orderId, paymentIndex),
                    orderId = orderId,
                    method = payment.optString("method"),
                    amount = indexAmount(payment, "amount"),
                    cashGiven = indexAmount(payment, "cashGiven"),
                    changeAmount = indexAmount(payment, "change"),
                    sortIndex = paymentIndex,
                    payload = payment.toString(),
                    updatedAt = now,
                )
            }
        }

        database.withTransaction {
            if (replaceAll) { orderDao.clearPayments(); orderDao.clearLines(); orderDao.clearOrders() }
            else for (order in orders) deleteRow(order.id)
            if (orders.isNotEmpty()) orderDao.insertOrders(orders)
            if (lines.isNotEmpty()) orderDao.insertLines(lines)
            if (payments.isNotEmpty()) orderDao.insertPayments(payments)
        }
    }

}
