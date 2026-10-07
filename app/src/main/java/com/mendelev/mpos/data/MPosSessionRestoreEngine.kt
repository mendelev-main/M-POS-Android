package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

/** Read-only projection of the reviewed current-order restore block. No payment or print effects. */
object MPosSessionRestoreEngine {
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val session = input.optJSONObject("session")
        val result = JSONObject().put("ok", true).put("authoritative", true)
            .put("source", "native-session-restore").put("restore", false)
        val items = session?.optJSONArray("items") ?: return result
        val cart = JSONArray()
        for (i in 0 until items.length()) items.optJSONObject(i)?.let { cart.put(JSONObject(it.toString())) }
        val customer = JSONObject().put("name", "").put("phone", "").put("address", "")
        val storedCustomer = session.optJSONObject("customer")
        storedCustomer?.keys()?.forEach { customer.put(it, storedCustomer.get(it)) }
        val warnings = JSONArray()
        if (storedCustomer == null && !session.isNull("customer"))
            warnings.put("Некорректный формат currentOrderSession.customer")
        val fee = MPosJsonNumbers.amount(session, "deliveryFee")
        // Non-finite and unsupported legacy coercions retain the reviewed compatibility path.
        require(fee.isFinite())
        val state = JSONObject().put("cart", cart).put("customer", customer).put("deliveryFee", fee)
            .put("deliveryTariffSelected", session.opt("deliveryTariffSelected") == true)
            .put("loyaltyPrograms", session.optJSONArray("loyaltyPrograms") ?: JSONArray())
            .put("loyaltyRedemptions", session.optJSONObject("loyaltyRedemptions") ?: JSONObject())
            .put("loyaltyLoadingCustomerId", "").put("loyaltyLoadError", "")
        for ((target, source) in mapOf("orderLabel" to "orderLabel", "orderType" to "orderType",
            "orderComment" to "orderComment", "currentOrderSource" to "source",
            "currentWebOrderId" to "webOrderId", "currentWebOrderStatus" to "webOrderStatus")) {
            state.put(target, MPosJsonNumbers.fallback(session.opt(source), if (target == "orderType") "На месте" else ""))
        }
        val loyaltyId = MPosJsonNumbers.fallback(session.opt("loyaltyCustomerId"))
        require(loyaltyId is String) // Unusual legacy String(...) coercions use the reviewed fallback.
        state.put("loyaltyCustomerId", loyaltyId)
        return result.put("restore", true).put("state", state).put("warnings", warnings)
            .put("kitchenPrinted", MPosJsonNumbers.truthy(session.opt("kitchenPrinted")))
            .put("printedItems", session.optJSONArray("printedItems") ?: JSONArray())
    }
}
