package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.math.floor
import kotlin.math.max

/** Local allocation only. Customer lookup, online eligibility guard and sale publication stay unchanged. */
object MPosLoyaltyRewardEngine {
    private data class Line(val productId: String, val quantity: Double, val price: Double, var used: Int = 0)
    private fun numberOrZero(value: Any?) = MPosJsonNumbers.number(value).let { if (it.isNaN() || it == 0.0) 0.0 else it }
    private fun scalarString(value: Any?, present: Boolean = true): String = when {
        !present -> "undefined"
        value == null || value === JSONObject.NULL -> "null"
        value is String -> value
        value is Boolean -> value.toString()
        value is Number -> {
            val n = value.toDouble()
            require(n.isFinite()) { "invalid loyalty identifier" }
            if (n == 0.0) "0" else if (kotlin.math.abs(n) >= 1e-6 && kotlin.math.abs(n) < 1e21)
                BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            else n.toString().lowercase().replace(Regex("\\.0e"), "e").replace(Regex("e(?!-)"), "e+")
        }
        else -> throw IllegalArgumentException("invalid loyalty scalar")
    }
    private fun fieldString(record: JSONObject, key: String) = scalarString(record.opt(key), record.has(key))

    fun calculate(items: JSONArray, programs: JSONArray, redemptions: JSONObject): JSONObject {
        // JS expands each whole unit then sorts by price. Selecting the earliest eligible
        // line with capacity produces the same stable result without a quantity-sized array.
        val lines = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val quantity = max(0.0, floor(numberOrZero(item.opt("qty"))))
            val price = max(0.0, numberOrZero(item.opt("price")))
            require(quantity.isFinite() && price.isFinite()) { "invalid loyalty amount" }
            Line(fieldString(item, "productId"), quantity, price)
        }
        val allocations = JSONObject(); val programDiscounts = JSONObject(); var discount = 0.0
        for (index in 0 until programs.length()) {
            val program = programs.getJSONObject(index); val id = fieldString(program, "id")
            if (!(MPosJsonNumbers.number(redemptions.opt(id)) > 0)) continue
            val products = if (!MPosJsonNumbers.truthy(program.opt("loyalty_reward_products"))) JSONArray()
                else program.getJSONArray("loyalty_reward_products")
            val allowed = (0 until products.length()).map { fieldString(products.getJSONObject(it), "product_id") }.toSet()
            val selected = lines.filter { it.used.toDouble() < it.quantity && it.productId in allowed }.minByOrNull { it.price } ?: continue
            selected.used++
            discount += selected.price
            allocations.put(id, JSONArray().put(JSONObject().put("productId", selected.productId).put("quantity", 1)))
            programDiscounts.put(id, MPosJsonNumbers.roundMoney(selected.price))
        }
        require(discount.isFinite()) { "invalid loyalty discount" }
        val total = MPosJsonNumbers.roundMoney(discount)
        val snapshot = JSONArray()
        for (index in 0 until programs.length()) {
            val program = programs.getJSONObject(index); val id = fieldString(program, "id")
            val rewards = max(0.0, floor(numberOrZero(redemptions.opt(id))))
            val applied = numberOrZero(programDiscounts.opt(id))
            if (rewards != 0.0 && applied > 0) {
                val name = MPosJsonNumbers.fallback(program.opt("name"), "Программа лояльности")
                snapshot.put(JSONObject().put("id", id).put("name", scalarString(name)).put("rewards", rewards)
                    .put("discount", MPosJsonNumbers.roundMoney(applied)))
            }
        }
        return JSONObject().put("discount", total).put("allocations", allocations).put("programDiscounts", programDiscounts)
            .put("snapshot", JSONObject().put("discount", MPosJsonNumbers.roundMoney(total)).put("programs", snapshot))
    }
    fun validate(order: JSONObject, input: JSONObject) {
        require(input.getInt("version") == 1) { "unsupported loyalty input" }
        val redemptions = order.getJSONObject("loyaltyRedemptions")
        val result = calculate(order.getJSONArray("items"), input.getJSONArray("programs"), redemptions)
        val allocations = result.getJSONObject("allocations")
        for (id in redemptions.keys()) {
            val requested = max(0.0, floor(numberOrZero(redemptions.opt(id))))
            val allocated = allocations.optJSONArray(id)?.let { rows -> (0 until rows.length()).sumOf { numberOrZero(rows.getJSONObject(it).opt("quantity")) } } ?: 0.0
            require(requested.isFinite() && requested == allocated) { "insufficient eligible loyalty products" }
        }
        val snapshot = result.getJSONObject("snapshot")
        require(same(order.getJSONObject("loyaltyRewardAllocations"), allocations)) { "loyalty allocation differs from displayed receipt" }
        require(order.getDouble("loyaltyDiscount") == snapshot.getDouble("discount")) { "loyalty discount differs from displayed receipt" }
        require(same(order.getJSONArray("loyaltyProgramsApplied"), snapshot.getJSONArray("programs"))) { "loyalty snapshot differs from displayed receipt" }
        order.put("loyaltyRewardAllocations", allocations).put("loyaltyDiscount", snapshot.getDouble("discount"))
            .put("loyaltyProgramsApplied", snapshot.getJSONArray("programs"))
    }
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }
}
