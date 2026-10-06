package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** One consistent persisted snapshot for shift totals and all report output formats. */
class MPosShiftReportRepository(private val database: MPosDatabase) {
    private data class Snapshot(val shifts: JSONArray, val orders: JSONArray, val revision: Long)
    private suspend fun snapshot(): Snapshot {
        val shifts = MPosShiftStorage(database)
        val orders = MPosOrderStorage(database)
        val recovery = MPosRecoveryStorage(database)
        check(shifts.isAuthoritative() && orders.isAuthoritative() && recovery.isAuthoritative("criticalStorageJournal"))
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        val shiftRows = shifts.readRecords()
        val envelope = orders.read()
        val parsed = JSONTokener(if (envelope.getBoolean("found")) envelope.getString("payload") else "null").nextValue()
        require(parsed is JSONArray || parsed === JSONObject.NULL)
        return Snapshot(shiftRows, parsed as? JSONArray ?: JSONArray(), envelope.getLong("revision"))
    }
    private fun parse(raw: String): JSONObject {
        val parser = JSONTokener(raw)
        val request = parser.nextValue()
        require(request is JSONObject && parser.nextClean() == '\u0000')
        return request
    }
    suspend fun read(raw: String): JSONObject = database.withTransaction {
        val request = parse(raw)
        val id = request.getString("shiftId")
        require(request.opt("shiftId") is String && id.isNotBlank())
        val source = snapshot()
        val shift = (0 until source.shifts.length()).map { source.shifts.getJSONObject(it) }.single { it.optString("id") == id }
        if (request.optBoolean("closedOnly")) check(shift.optString("status") == "closed") { "shift is not durably closed" }
        build(shift, source.orders, request.optString("currency"), request.optString("establishmentName"))
            .put("ok", true).put("authoritative", true).put("source", "room-shift-report").put("ordersRevision", source.revision)
    }
    suspend fun readScreen(raw: String): JSONObject = database.withTransaction {
        val request = parse(raw)
        val source = snapshot()
        val indexed = (0 until source.shifts.length()).map { it to source.shifts.getJSONObject(it) }
        fun model(entry: Pair<Int, JSONObject>): JSONObject = build(entry.second, source.orders, request.optString("currency"), request.optString("establishmentName"))
            .put("number", entry.first + 1).also { it.getJSONObject("report").remove("orders") }
        val active = indexed.firstOrNull { it.second.optString("status") == "open" }
        val closed = indexed.filter { it.second.optString("status") == "closed" }.sortedByDescending { MPosJsonNumbers.amount(it.second, "closedAt") }.take(20)
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-shift-screen")
            .put("active", active?.let(::model) ?: JSONObject.NULL).put("history", JSONArray(closed.map(::model)))
            .put("ordersRevision", source.revision)
    }

    companion object {
        fun build(shift: JSONObject, archive: JSONArray, currency: String, establishmentName: String): JSONObject {
            val id = shift.getString("id")
            val t = MPosShiftAccounting.totals(shift, archive)
            val expected = MPosShiftAccounting.balance(shift, archive)
            require(expected.isFinite()) { "invalid report drawer" }
            val counted = MPosJsonNumbers.reportAmount(shift, "countedCash")
            val difference = counted - expected
            require(counted.isFinite() && difference.isFinite())
            val records = (0 until archive.length()).map { archive.getJSONObject(it) }.filter { it.optString("shiftId") == id }
                .sortedBy { MPosJsonNumbers.amount(it, "timestamp").let { value -> if (value.isNaN()) 0.0 else value } }
            val movements = shift.optJSONArray("cashMovements") ?: JSONArray()
            val report = JSONObject().put("id", id)
                .put("employeeName", MPosJsonNumbers.fallback(shift.opt("employeeName"))).put("employeePhone", MPosJsonNumbers.fallback(shift.opt("employeePhone")))
                .put("openedAt", MPosJsonNumbers.fallback(shift.opt("openedAt"), 0)).put("closedAt", MPosJsonNumbers.fallback(shift.opt("closedAt"), 0))
                .put("openingCash", MPosJsonNumbers.reportAmount(shift, "openingCash")).put("countedCash", counted).put("expectedCash", expected).put("difference", difference)
                .put("cash", t.getDouble("cash")).put("card", t.getDouble("card")).put("total", t.getDouble("total")).put("count", t.getInt("count"))
                .put("deposits", t.getDouble("deposits")).put("withdrawals", t.getDouble("withdrawals")).put("netMovements", t.getDouble("netMovements"))
                .put("currency", currency).put("establishmentName", establishmentName)
                .put("cashMovements", JSONArray().also { rows ->
                    for (i in 0 until movements.length()) {
                        val m = movements.getJSONObject(i)
                        rows.put(JSONObject().put("type", MPosJsonNumbers.fallback(m.opt("type"))).put("subtype", MPosJsonNumbers.fallback(m.opt("subtype")))
                            .put("amount", MPosJsonNumbers.reportAmount(m, "amount")).put("timestamp", MPosJsonNumbers.fallback(m.opt("timestamp"), 0)).put("note", MPosJsonNumbers.fallback(m.opt("note"))))
                    }
                })
                .put("orders", JSONArray().also { rows ->
                    for (order in records) {
                        val items = order.optJSONArray("items") ?: JSONArray()
                        rows.put(JSONObject().put("timestamp", MPosJsonNumbers.fallback(order.opt("timestamp"), 0)).put("total", MPosJsonNumbers.reportAmount(order, "total"))
                            .put("method", MPosJsonNumbers.fallback(order.opt("method"))).put("orderLabel", MPosJsonNumbers.fallback(order.opt("orderLabel"))).put("orderType", MPosJsonNumbers.fallback(order.opt("orderType")))
                            .put("items", JSONArray().also { lines ->
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    lines.put(JSONObject().put("name", MPosJsonNumbers.fallback(item.opt("name"), MPosJsonNumbers.fallback(item.opt("productName"), "Товар")))
                                        .put("qty", MPosJsonNumbers.reportAmount(item, "qty")).put("price", MPosJsonNumbers.reportAmount(item, "price")))
                                }
                            }))
                    }
                })
            val summary = JSONObject(t.toString()).put("expectedCash", expected).put("countedCash", counted).put("difference", difference)
                .put("grossSales", records.sumOf { MPosJsonNumbers.amount(it, "total") })
            summary.put("netRevenue", summary.getDouble("grossSales") - summary.getDouble("refunds"))
            val discounts = records.sumOf { order ->
                val items = order.optJSONArray("items") ?: JSONArray()
                (0 until items.length()).sumOf { i ->
                    val item = items.getJSONObject(i)
                    if (!MPosJsonNumbers.truthy(item.opt("discountName"))) 0.0 else {
                        val qty = MPosJsonNumbers.amount(item, "qty")
                        val base = MPosJsonNumbers.amount(item, "price") * qty
                        val value = MPosJsonNumbers.reportAmount(item, "discountValue")
                        if (item.optString("discountType") == "percent") base * value.coerceIn(0.0, 100.0) / 100
                        else kotlin.math.min(base, kotlin.math.max(0.0, value) * qty)
                    }
                }
            }
            summary.put("discountsTotal", discounts)
            return JSONObject().put("report", report).put("summary", summary)
        }
    }
}
