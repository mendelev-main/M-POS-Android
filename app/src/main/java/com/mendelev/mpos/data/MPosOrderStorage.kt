package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns the compatible orders document; structured indexes never replace its full JSON. */
class MPosOrderStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val orderDao = database.orderProjectionDao()
    companion object { const val AUTHORITY_KEY = "mpos_orders_authority_v1" }

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
        val document = documents.get("orders")
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-orders")
            .put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)

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
        if (serialized == null) documents.delete("orders")
        else documents.upsert(LegacyStorageShadowEntity("orders", serialized, System.currentTimeMillis()))
        projectArray(parsed as? JSONArray ?: JSONArray())
    }

    // Missing/non-finite values are normalized only in diagnostic indexes, never in the document.
    private fun indexAmount(record: JSONObject, key: String): Double =
        record.optDouble(key, 0.0).takeIf { it.isFinite() } ?: 0.0

    suspend fun project(serialized: String) {
        projectArray(JSONArray(serialized))
    }

    private suspend fun projectArray(source: JSONArray) {
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
                sortIndex = orderIndex,
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
            orderDao.clearPayments()
            orderDao.clearLines()
            orderDao.clearOrders()
            if (orders.isNotEmpty()) orderDao.insertOrders(orders)
            if (lines.isNotEmpty()) orderDao.insertLines(lines)
            if (payments.isNotEmpty()) orderDao.insertPayments(payments)
        }
    }

}
