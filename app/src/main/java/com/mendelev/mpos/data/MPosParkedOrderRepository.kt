package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

class MPosParkedOrderRepository(
    private val database: MPosDatabase,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val dao = database.parkedOrderProjectionDao()

    suspend fun parityReport(): JSONObject {
        val legacy = shadowDao.get("parked")
            ?: return JSONObject().put("ok", false).put("authoritative", false)
                .put("reason", "legacy parked orders shadow is not available")

        val source = JSONArray(legacy.payload)
        val sourceOrders = linkedMapOf<String, JSONObject>()
        val sourceLines = linkedMapOf<String, Pair<String, JSONObject>>()
        for (orderIndex in 0 until source.length()) {
            val order = source.optJSONObject(orderIndex) ?: continue
            val id = order.optString("id").trim()
            if (id.isEmpty()) continue
            sourceOrders[id] = order
            val items = order.optJSONArray("items") ?: JSONArray()
            for (lineIndex in 0 until items.length()) {
                val line = items.optJSONObject(lineIndex) ?: continue
                sourceLines[lineKey(id, lineIndex)] = id to line
            }
        }

        val nativeOrders = dao.allOrders()
        val nativeLines = dao.allLines()
        val nativeById = nativeOrders.associateBy { it.id }
        val nativeLineById = nativeLines.associateBy { it.id }

        val missingOrderIds = sourceOrders.keys.filterNot(nativeById::containsKey)
        val extraOrderIds = nativeById.keys.filterNot(sourceOrders::containsKey)
        val mismatchedOrderIds = sourceOrders.mapNotNull { (id, sourceOrder) ->
            val projected = nativeById[id] ?: return@mapNotNull null
            val customer = sourceOrder.optJSONObject("customer") ?: JSONObject()
            val matches =
                projected.receiptDisplayNumber == sourceOrder.optString("receiptDisplayNumber") &&
                sameMoney(projected.total, sourceOrder.optDouble("total")) &&
                projected.orderLabel == sourceOrder.optString("orderLabel") &&
                projected.orderType == sourceOrder.optString("orderType") &&
                projected.webOrderId == sourceOrder.optString("webOrderId") &&
                projected.customerId == customer.optString("id") &&
                projected.createdAt == sourceOrder.optLong("createdAt") &&
                projected.kitchenPrinted == sourceOrder.optBoolean("kitchenPrinted")
            if (matches) null else id
        }

        val missingLineIds = sourceLines.keys.filterNot(nativeLineById::containsKey)
        val extraLineIds = nativeLineById.keys.filterNot(sourceLines::containsKey)
        val matches = missingOrderIds.isEmpty() && extraOrderIds.isEmpty() &&
            mismatchedOrderIds.isEmpty() && missingLineIds.isEmpty() && extraLineIds.isEmpty() &&
            sourceOrders.size == nativeOrders.size && sourceLines.size == nativeLines.size

        return JSONObject()
            .put("ok", true).put("matches", matches).put("authoritative", false)
            .put("legacyParkedOrderCount", sourceOrders.size)
            .put("nativeParkedOrderCount", nativeOrders.size)
            .put("legacyParkedLineCount", sourceLines.size)
            .put("nativeParkedLineCount", nativeLines.size)
            .put("missingParkedOrderIds", JSONArray(missingOrderIds))
            .put("extraParkedOrderIds", JSONArray(extraOrderIds))
            .put("mismatchedParkedOrderIds", JSONArray(mismatchedOrderIds))
            .put("missingParkedLineIds", JSONArray(missingLineIds))
            .put("extraParkedLineIds", JSONArray(extraLineIds))
    }

    companion object {
        fun lineKey(orderId: String, index: Int): String = "$orderId:parked-line:$index"
    }

    private fun sameMoney(left: Double, right: Double): Boolean =
        kotlin.math.abs(left - right) < 0.0001
}
