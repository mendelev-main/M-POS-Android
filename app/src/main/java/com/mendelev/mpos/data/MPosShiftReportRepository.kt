package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** One consistent persisted snapshot for shift totals and all report output formats. */
class MPosShiftReportRepository(private val database: MPosDatabase) {
    suspend fun read(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw)
        val request = parser.nextValue()
        require(request is JSONObject && parser.nextClean() == '\u0000')
        val id = request.getString("shiftId")
        require(request.opt("shiftId") is String && id.isNotBlank())
        val shifts = MPosShiftStorage(database)
        val orders = MPosOrderStorage(database)
        val recovery = MPosRecoveryStorage(database)
        check(shifts.isAuthoritative() && orders.isAuthoritative() && recovery.isAuthoritative("criticalStorageJournal"))
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        val shiftRows = JSONArray(shifts.read().getString("payload"))
        val shift = (0 until shiftRows.length()).map { shiftRows.getJSONObject(it) }.single { it.optString("id") == id }
        if (request.optBoolean("closedOnly")) check(shift.optString("status") == "closed") { "shift is not durably closed" }
        val envelope = orders.read()
        val payload = if (envelope.getBoolean("found")) envelope.getString("payload") else "null"
        val parsed = JSONTokener(payload).nextValue()
        require(parsed is JSONArray || parsed === JSONObject.NULL)
        val model = build(shift, parsed as? JSONArray ?: JSONArray(), request.optString("currency"), request.optString("establishmentName"))
        model.put("ok", true).put("authoritative", true).put("source", "room-shift-report").put("ordersRevision", envelope.getLong("revision"))
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
            return JSONObject().put("report", report).put("summary", summary)
        }
    }
}
