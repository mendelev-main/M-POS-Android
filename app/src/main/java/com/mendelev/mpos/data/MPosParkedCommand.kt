package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Atomic parked-list transitions. Receipt and kitchen rendering remain compatibility inputs. */
class MPosParkedCommand(private val database: MPosDatabase) {
    private val parked = MPosParkedOrderStorage(database)
    private val recovery = MPosRecoveryStorage(database)
    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val c = JSONObject(raw)
        require(c.getInt("version") == 1)
        check(parked.isAuthoritative() && recovery.isAuthoritative("currentOrderSession") && recovery.isAuthoritative("criticalStorageJournal"))
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        val doc = parked.read()
        val before = if (!doc.getBoolean("found") || doc.getString("payload") == "null") JSONArray() else JSONArray(doc.getString("payload"))
        check(same(before, c.getJSONArray("expected"))) { "parked list changed" }
        val writes = c.getJSONObject("writes")
        val candidate = writes.getJSONArray("parked")
        val next = JSONArray()
        when (c.getString("operation")) {
            "park-order" -> {
                require(writes.keys().asSequence().toSet() == setOf("parked", "currentOrderSession"))
                require(candidate.length() == before.length() + 1)
                for (i in 0 until before.length()) next.put(before.get(i))
                val added = candidate.getJSONObject(before.length())
                require(added.getString("id").isNotBlank())
                require((0 until before.length()).none { same(before.getJSONObject(it).opt("id"), added.opt("id")) })
                require(added.getJSONArray("items").length() > 0)
                require(writes.getJSONObject("currentOrderSession").getJSONArray("items").length() == 0)
                next.put(added)
            }
            "resume-parked", "delete-parked" -> {
                val resume = c.getString("operation") == "resume-parked"
                require(writes.keys().asSequence().toSet() == if (resume) setOf("parked", "currentOrderSession") else setOf("parked"))
                val id = c.get("id")
                val removed = (0 until before.length()).map { before.getJSONObject(it) }.filter { same(it.opt("id"), id) }
                require(removed.isNotEmpty())
                for (i in 0 until before.length()) if (!same(before.getJSONObject(i).opt("id"), id)) next.put(before.get(i))
                if (resume) {
                    require(c.getBoolean("cartEmpty")) { "current order must be empty" }
                    val old = removed.first(); val session = writes.getJSONObject("currentOrderSession")
                    check(same(session.getJSONArray("items"), old.optJSONArray("items") ?: JSONArray()))
                    val customer = JSONObject().put("name", "").put("phone", "").put("address", "")
                    old.optJSONObject("customer")?.let { original -> for (key in original.keys()) customer.put(key, original.get(key)) }
                    check(same(customer, session.getJSONObject("customer")))
                    for ((target, source) in listOf("orderLabel" to "orderLabel", "orderComment" to "comment", "source" to "source", "webOrderId" to "webOrderId", "webOrderStatus" to "webOrderStatus")) {
                        val value = old.opt(source)
                        check(same(session.opt(target), if (truthy(value)) value else ""))
                    }
                    val type = old.opt("orderType")
                    check(same(session.opt("orderType"), if (truthy(type)) type else "На месте"))
                    check(session.opt("deliveryTariffSelected") == (old.opt("deliveryTariffSelected") == true))
                    check(session.opt("kitchenPrinted") == truthy(old.opt("kitchenPrinted")))
                    val fee = old.opt("deliveryFee")
                    val amount = number(if (truthy(fee)) fee else 0)
                    check(if (amount.isFinite()) same(session.opt("deliveryFee"), amount) else session.isNull("deliveryFee"))
                    old.optJSONArray("printedItems")?.let { check(same(it, session.getJSONArray("printedItems"))) }
                }
            }
            "park-order-print-state" -> {
                require(writes.keys().asSequence().toSet() == setOf("parked") && candidate.length() == before.length())
                var changed = 0
                for (i in 0 until before.length()) {
                    val old = before.getJSONObject(i); val updated = candidate.getJSONObject(i)
                    if (!same(old, updated)) {
                        changed++
                        require(updated.opt("kitchenPrinted") == true && updated.optJSONArray("printedItems") != null)
                        val a = JSONObject(old.toString()); val b = JSONObject(updated.toString())
                        for (key in listOf("kitchenPrinted", "printedItems")) { a.remove(key); b.remove(key) }
                        check(same(a, b))
                    }
                    next.put(updated)
                }
                require(changed <= 1)
            }
            else -> throw IllegalArgumentException("unsupported parked command")
        }
        check(same(next, candidate)) { "parked transition differs from compatibility candidate" }
        parked.write(next.toString())
        if (writes.has("currentOrderSession")) recovery.write("currentOrderSession", writes.getJSONObject("currentOrderSession").toString())
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-parked-command")
    }
    private fun number(v: Any?): Double {
        fun arrayText(value: Any?): String = when (value) {
            null, JSONObject.NULL -> ""
            is JSONArray -> (0 until value.length()).joinToString(",") { arrayText(value.opt(it)) }
            is JSONObject -> "[object Object]"
            else -> value.toString()
        }
        return MPosJsonNumbers.number(if (v is JSONArray) arrayText(v) else v)
    }
    private fun truthy(v: Any?): Boolean = when (v) {
        null, JSONObject.NULL -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0 && !v.toDouble().isNaN()
        is String -> v.isNotEmpty()
        else -> true
    }
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }
}
