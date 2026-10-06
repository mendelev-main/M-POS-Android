package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns the compatible parked document; structured indexes never replace its full JSON. */
class MPosParkedOrderStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val parkedDao = database.parkedOrderProjectionDao()
    companion object { const val AUTHORITY_KEY = "mpos_parked_authority_v1" }

    suspend fun isAuthoritative(): Boolean = documents.get(AUTHORITY_KEY) != null

    suspend fun initialize(legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative()) {
            replace(legacy)
            documents.upsert(LegacyStorageShadowEntity(AUTHORITY_KEY, "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement()
    }

    private fun acknowledgement() = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-parked")

    suspend fun read(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native parked is not initialized" }
        val document = documents.get("parked")
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-parked")
            .put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)

    }

    suspend fun write(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native parked is not initialized" }
        replace(serialized)
        acknowledgement()
    }

    suspend fun remove(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native parked is not initialized" }
        replace(null)
        acknowledgement()
    }

    private suspend fun replace(serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid parked JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONArray) { "parked must be an array or null" }
        if (serialized == null) documents.delete("parked")
        else documents.upsert(LegacyStorageShadowEntity("parked", serialized, System.currentTimeMillis()))
        projectArray(parsed as? JSONArray ?: JSONArray())
    }

    // Only diagnostic indexes normalize missing values; business JSON stays unchanged.
    private fun indexAmount(record: JSONObject, key: String): Double =
        record.optDouble(key, 0.0).takeIf { it.isFinite() } ?: 0.0

    suspend fun project(serialized: String) {
        projectArray(JSONArray(serialized))
    }

    private suspend fun projectArray(source: JSONArray) {
        val now = System.currentTimeMillis()
        val orders = ArrayList<ParkedOrderProjectionEntity>(source.length())
        val lines = ArrayList<ParkedOrderLineProjectionEntity>()

        for (orderIndex in 0 until source.length()) {
            val order = source.optJSONObject(orderIndex) ?: continue
            val orderId = order.optString("id").trim()
            if (orderId.isEmpty()) continue
            val customer = order.optJSONObject("customer") ?: JSONObject()

            orders += ParkedOrderProjectionEntity(
                id = orderId,
                receiptDisplayNumber = order.optString("receiptDisplayNumber"),
                total = indexAmount(order, "total"),
                subtotal = indexAmount(order, "subtotal"),
                orderLabel = order.optString("orderLabel"),
                orderType = order.optString("orderType"),
                deliveryFee = indexAmount(order, "deliveryFee"),
                comment = order.optString("comment"),
                source = order.optString("source"),
                webOrderId = order.optString("webOrderId"),
                webOrderStatus = order.optString("webOrderStatus"),
                employeeName = order.optString("employeeName"),
                customerId = customer.optString("id"),
                customerName = customer.optString("name"),
                customerPhone = customer.optString("phone"),
                createdAt = order.optLong("createdAt"),
                kitchenPrinted = order.optBoolean("kitchenPrinted"),
                sortIndex = orderIndex,
                payload = order.toString(),
                updatedAt = now,
            )

            val items = order.optJSONArray("items") ?: JSONArray()
            for (lineIndex in 0 until items.length()) {
                val line = items.optJSONObject(lineIndex) ?: continue
                lines += ParkedOrderLineProjectionEntity(
                    id = MPosParkedOrderRepository.lineKey(orderId, lineIndex),
                    parkedOrderId = orderId,
                    productId = line.optString("productId"),
                    name = line.optString("name"),
                    category = line.optString("category"),
                    qty = indexAmount(line, "qty"),
                    price = indexAmount(line, "price"),
                    comment = line.optString("comment"),
                    sortIndex = lineIndex,
                    payload = line.toString(),
                    updatedAt = now,
                )
            }
        }

        database.withTransaction {
            parkedDao.clearLines()
            parkedDao.clearOrders()
            if (orders.isNotEmpty()) parkedDao.insertOrders(orders)
            if (lines.isNotEmpty()) parkedDao.insertLines(lines)
        }
    }

}
