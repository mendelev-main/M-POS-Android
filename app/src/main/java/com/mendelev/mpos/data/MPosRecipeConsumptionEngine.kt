package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/** Recipe expansion at settlement; stock units and unrounded consumption snapshots stay unchanged. */
object MPosRecipeConsumptionEngine {
    private data class Id(val type: String, val value: Any?)
    private fun id(record: JSONObject, key: String): Id = when (val value = record.opt(key)) {
        null -> Id("missing", null)
        JSONObject.NULL -> Id("null", null)
        is Number -> Id("number", value.toDouble())
        is String -> Id("string", value)
        is Boolean -> Id("boolean", value)
        else -> throw IllegalArgumentException("invalid recipe identifier")
    }
    private data class Step(val product: JSONObject, val qty: Double, val exit: Boolean = false)
    fun calculate(products: JSONArray, items: JSONArray, checkStock: Boolean = true): JSONObject {
        val catalogue = linkedMapOf<Id, JSONObject>()
        for (index in 0 until products.length()) {
            val p = products.getJSONObject(index); catalogue.putIfAbsent(id(p, "id"), p)
        }
        val totals = linkedMapOf<Id, Double>()
        fun expand(productId: Id, quantity: Double) {
            val root = requireNotNull(catalogue[productId]) { "recipe product missing" }
            val stack = ArrayDeque<Step>(); val trail = mutableSetOf<Id>()
            stack.addLast(Step(root, quantity))
            while (stack.isNotEmpty()) {
                val step = stack.removeLast(); val p = step.product; val key = id(p, "id")
                if (step.exit) { trail.remove(key); continue }
                require(step.qty.isFinite() && step.qty > 0) { "invalid ingredient quantity" }
                require(key !in trail) { "cyclic recipe" }
                if (p.opt("type") == "simple") {
                    val sum = (totals[key] ?: 0.0) + step.qty
                    require(sum.isFinite()) { "ingredient quantity overflow" }
                    totals[key] = sum
                } else {
                    require(p.opt("type") == "composite") { "invalid recipe type" }
                    val components = p.getJSONArray("components")
                    require(components.length() > 0) { "empty recipe" }
                    trail.add(key); stack.addLast(Step(p, step.qty, true))
                    // Stack order preserves the reference's depth-first left-to-right arithmetic.
                    for (index in components.length() - 1 downTo 0) {
                        val component = components.getJSONObject(index)
                        val child = requireNotNull(catalogue[id(component, "productId")]) { "recipe ingredient missing" }
                        stack.addLast(Step(child, step.qty * MPosJsonNumbers.number(component.opt("qty"))))
                    }
                }
            }
        }
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            expand(id(item, "productId"), MPosJsonNumbers.number(item.opt("qty")))
            val modifiers = if (!MPosJsonNumbers.truthy(item.opt("selectedModifiers"))) JSONArray() else item.getJSONArray("selectedModifiers")
            for (modifierIndex in 0 until modifiers.length()) {
                val modifier = modifiers.getJSONObject(modifierIndex)
                expand(id(modifier, "productId"), MPosJsonNumbers.amount(item, "qty") * MPosJsonNumbers.amount(modifier, "qty"))
            }
        }
        val result = JSONArray()
        for ((key, quantity) in totals) {
            val p = catalogue.getValue(key)
            if (p.opt("type") != "simple" || MPosJsonNumbers.truthy(p.opt("noStockTracking"))) continue
            if (checkStock) {
                val stock = MPosJsonNumbers.reportAmount(p, "stock")
                val tolerance = Math.ulp(1.0) * 8 * max(abs(stock), quantity)
                require(stock.isFinite() && stock + tolerance >= quantity) { "insufficient ingredient stock" }
            }
            result.put(JSONObject().put("productId", p.opt("id") ?: JSONObject.NULL).put("qty", quantity))
        }
        return JSONObject().put("version", 1).put("items", result)
    }
    fun validate(products: JSONArray, order: JSONObject, input: JSONObject): JSONObject {
        require(input.getInt("version") == 1) { "unsupported recipe consumption input" }
        val result = calculate(products, order.getJSONArray("items"))
        val provided = order.getJSONObject("stockConsumption")
        require(provided.getInt("version") == 1) { "unsupported stock consumption" }
        val actual = provided.getJSONArray("items"); val expected = result.getJSONArray("items")
        require(actual.length() == expected.length()) { "recipe consumption differs from receipt" }
        for (index in 0 until expected.length()) {
            val a = actual.getJSONObject(index); val e = expected.getJSONObject(index)
            require(id(a, "productId") == id(e, "productId") && a.getDouble("qty") == e.getDouble("qty")) { "recipe consumption differs from receipt" }
        }
        // Preserve any receipt snapshot extensions; quantity/order were verified exactly.
        return result
    }
}
