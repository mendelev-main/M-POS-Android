package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

/** Attribute sales to their original shift and refunds to their execution shift. */
object MPosShiftAccounting {
    fun amount(record: JSONObject, key: String): Double = record.optDouble(key, 0.0)
    fun paid(order: JSONObject, method: String): Double {
        val parts = order.optJSONArray("payments") ?: return if (order.optString("method") == method) amount(order, "total") else 0.0
        return (0 until parts.length()).map { parts.getJSONObject(it) }.filter { it.optString("method") == method }.sumOf { amount(it, "amount") }
    }
    fun totals(shift: JSONObject, archive: JSONArray): JSONObject {
        val id = shift.getString("id")
        val records = (0 until archive.length()).mapNotNull { archive.optJSONObject(it) }
        val sales = records.filter { it.optString("shiftId") == id }
        val returned = records.filter { it.optLong("returnedAt") != 0L && it.optString("returnedShiftId").ifBlank { it.optString("shiftId") } == id }
        val movements = shift.optJSONArray("cashMovements") ?: JSONArray()
        val refunds = (0 until movements.length()).mapNotNull { movements.optJSONObject(it) }.filter { it.optString("type") == "withdrawal" && it.optString("subtype") == "refund" }
        val used = mutableSetOf<Int>()
        var cashRefunds = 0.0; var cardRefunds = 0.0; var matched = 0.0; var refundTotal = 0.0
        for (order in returned) {
            val total = amount(order, "total")
            val refund = amount(order, "returnAmount").takeIf { it != 0.0 } ?: total
            val factor = if (total > 0) refund / total else 1.0
            val cash = paid(order, "cash") * factor
            cashRefunds += cash; cardRefunds += paid(order, "card") * factor; refundTotal += refund
            val match = refunds.indices.firstOrNull { it !in used && refunds[it].optLong("timestamp") == order.optLong("returnedAt") && kotlin.math.abs(amount(refunds[it], "amount") - cash) <= 0.001 }
            if (match != null) { used += match; matched += amount(refunds[match], "amount") }
        }
        val rows = (0 until movements.length()).mapNotNull { movements.optJSONObject(it) }
        val deposits = rows.filter { it.optString("type") == "deposit" }.sumOf { amount(it, "amount") }
        val withdrawals = rows.filter { it.optString("type") == "withdrawal" }.sumOf { amount(it, "amount") }
        val cash = sales.sumOf { paid(it, "cash") } - cashRefunds
        val card = sales.sumOf { paid(it, "card") } - cardRefunds
        return JSONObject().put("cash", cash).put("card", card).put("total", cash + card)
            .put("cashRefunds", cashRefunds).put("refunds", refundTotal).put("deposits", deposits).put("withdrawals", withdrawals)
            .put("netMovements", deposits - withdrawals).put("refundCashMovements", matched)
            .put("count", sales.count { it.optLong("returnedAt") == 0L || it.optString("returnedShiftId").ifBlank { it.optString("shiftId") } != id })
    }
    fun balance(shift: JSONObject, archive: JSONArray): Double {
        val t = totals(shift, archive)
        return amount(shift, "openingCash") + t.getDouble("cash") + t.getDouble("netMovements") + t.getDouble("refundCashMovements")
    }
}
