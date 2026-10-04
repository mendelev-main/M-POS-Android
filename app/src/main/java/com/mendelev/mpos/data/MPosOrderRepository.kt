package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

class MPosOrderRepository(
    private val database: MPosDatabase,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val orderDao = database.orderProjectionDao()

    suspend fun parityReport(): JSONObject {
        val legacy = shadowDao.get("orders")
            ?: return JSONObject()
                .put("ok", false)
                .put("authoritative", false)
                .put("reason", "legacy orders shadow is not available")

        val source = JSONArray(legacy.payload)
        val sourceOrders = linkedMapOf<String, JSONObject>()
        val sourceLines = linkedMapOf<String, Pair<String, JSONObject>>()
        val sourcePayments = linkedMapOf<String, Pair<String, JSONObject>>()

        for (orderIndex in 0 until source.length()) {
            val order = source.optJSONObject(orderIndex) ?: continue
            val orderId = order.optString("id").trim()
            if (orderId.isEmpty()) continue
            sourceOrders[orderId] = order

            val items = order.optJSONArray("items") ?: JSONArray()
            for (lineIndex in 0 until items.length()) {
                val line = items.optJSONObject(lineIndex) ?: continue
                sourceLines[lineKey(orderId, lineIndex)] = orderId to line
            }

            val payments = order.optJSONArray("payments") ?: JSONArray()
            for (paymentIndex in 0 until payments.length()) {
                val payment = payments.optJSONObject(paymentIndex) ?: continue
                sourcePayments[paymentKey(orderId, paymentIndex)] = orderId to payment
            }
        }

        val nativeOrders = orderDao.allOrders()
        val nativeLines = orderDao.allLines()
        val nativePayments = orderDao.allPayments()
        val nativeOrderById = nativeOrders.associateBy { it.id }
        val nativeLineById = nativeLines.associateBy { it.id }
        val nativePaymentById = nativePayments.associateBy { it.id }

        val missingOrderIds = sourceOrders.keys.filterNot(nativeOrderById::containsKey)
        val extraOrderIds = nativeOrderById.keys.filterNot(sourceOrders::containsKey)
        val mismatchedOrderIds = sourceOrders.mapNotNull { (id, sourceOrder) ->
            val projected = nativeOrderById[id] ?: return@mapNotNull null
            val matches =
                projected.shiftId == sourceOrder.optString("shiftId") &&
                projected.receiptNumber == sourceOrder.optInt("receiptNumber") &&
                projected.receiptDisplayNumber == sourceOrder.optString("receiptDisplayNumber") &&
                projected.employeeId == sourceOrder.optString("employeeId") &&
                projected.method == sourceOrder.optString("method") &&
                sameMoney(projected.total, sourceOrder.optDouble("total")) &&
                projected.orderType == sourceOrder.optString("orderType") &&
                projected.orderLabel == sourceOrder.optString("orderLabel") &&
                sameMoney(projected.deliveryFee, sourceOrder.optDouble("deliveryFee")) &&
                projected.source == sourceOrder.optString("source") &&
                projected.webOrderId == sourceOrder.optString("webOrderId") &&
                projected.timestamp == sourceOrder.optLong("timestamp") &&
                projected.returnedAt == sourceOrder.optLong("returnedAt") &&
                sameMoney(projected.returnAmount, sourceOrder.optDouble("returnAmount"))
            if (matches) null else id
        }

        val missingLineIds = sourceLines.keys.filterNot(nativeLineById::containsKey)
        val extraLineIds = nativeLineById.keys.filterNot(sourceLines::containsKey)
        val missingPaymentIds = sourcePayments.keys.filterNot(nativePaymentById::containsKey)
        val extraPaymentIds = nativePaymentById.keys.filterNot(sourcePayments::containsKey)

        val matches =
            missingOrderIds.isEmpty() &&
            extraOrderIds.isEmpty() &&
            mismatchedOrderIds.isEmpty() &&
            missingLineIds.isEmpty() &&
            extraLineIds.isEmpty() &&
            missingPaymentIds.isEmpty() &&
            extraPaymentIds.isEmpty() &&
            sourceOrders.size == nativeOrders.size &&
            sourceLines.size == nativeLines.size &&
            sourcePayments.size == nativePayments.size

        return JSONObject()
            .put("ok", true)
            .put("matches", matches)
            .put("authoritative", false)
            .put("legacyOrderCount", sourceOrders.size)
            .put("nativeOrderCount", nativeOrders.size)
            .put("legacyLineCount", sourceLines.size)
            .put("nativeLineCount", nativeLines.size)
            .put("legacyPaymentCount", sourcePayments.size)
            .put("nativePaymentCount", nativePayments.size)
            .put("missingOrderIds", JSONArray(missingOrderIds))
            .put("extraOrderIds", JSONArray(extraOrderIds))
            .put("mismatchedOrderIds", JSONArray(mismatchedOrderIds))
            .put("missingLineIds", JSONArray(missingLineIds))
            .put("extraLineIds", JSONArray(extraLineIds))
            .put("missingPaymentIds", JSONArray(missingPaymentIds))
            .put("extraPaymentIds", JSONArray(extraPaymentIds))
    }

    companion object {
        fun lineKey(orderId: String, index: Int): String = "$orderId:line:$index"
        fun paymentKey(orderId: String, index: Int): String = "$orderId:payment:$index"
    }

    private fun sameMoney(left: Double, right: Double): Boolean =
        kotlin.math.abs(left - right) < 0.0001
}
