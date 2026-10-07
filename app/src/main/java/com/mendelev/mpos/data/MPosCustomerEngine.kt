package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

/** Order association only; the customer directory remains backend-owned. */
object MPosCustomerEngine {
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val session = JSONObject(input.getJSONObject("session").toString())
        val previous = session.optJSONObject("customer") ?: JSONObject()
        val customer = JSONObject(previous.toString())
        fun assign(target: JSONObject, key: String, source: JSONObject, from: String = key) {
            if (source.has(from)) target.put(key, source.get(from)) else target.remove(key)
        }
        when (input.getString("operation")) {
            "select" -> {
                val selected = input.getJSONObject("customer")
                for (key in listOf("id", "name", "phone")) assign(customer, key, selected)
                session.put("customer", customer).put("loyaltyPrograms", JSONArray())
                    .put("loyaltyRedemptions", JSONObject()).put("loyaltyCustomerId", "")
            }
            "remove" -> session.put("customer", JSONObject().put("name", "").put("phone", "")
                .put("address", MPosJsonNumbers.fallback(previous.opt("address"))))
                .put("loyaltyPrograms", JSONArray()).put("loyaltyRedemptions", JSONObject()).put("loyaltyCustomerId", "")
            "profile" -> {
                val data = input.getJSONObject("data")
                val selected = data.getJSONObject("customer")
                assign(customer, "id", selected); assign(customer, "name", selected)
                assign(customer, "phone", selected, "normalized_phone")
                session.put("customer", customer).put("loyaltyPrograms", MPosJsonNumbers.fallback(data.opt("programs"), JSONArray()))
                    .put("loyaltyRedemptions", JSONObject()).put("loyaltyCustomerId", input.getString("customerId"))
            }
            else -> throw IllegalArgumentException("unsupported customer operation")
        }
        return JSONObject().put("ok", true).put("authoritative", true).put("source", "native-customer-context").put("session", session)
    }
}
