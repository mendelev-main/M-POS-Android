package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.Collator
import java.util.Locale

/** Opening form metadata only; administrator verification remains the existing UI boundary. */
class MPosShiftOpeningRepository(private val database: MPosDatabase) {
    suspend fun read(): JSONObject = database.withTransaction {
        val shifts = MPosShiftStorage(database)
        val employees = MPosEmployeeStorage(database)
        val recovery = MPosRecoveryStorage(database)
        check(shifts.isAuthoritative() && employees.isAuthoritative() && recovery.isAuthoritative("criticalStorageJournal"))
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        val rows = shifts.readRecords()
        val history = (0 until rows.length()).map { rows.getJSONObject(it) }
        check(history.none { it.optString("status") == "open" }) { "shift already open" }
        val previous = history.filter { it.optString("status") == "closed" }.maxByOrNull { MPosJsonNumbers.amount(it, "closedAt").also { n -> require(n.isFinite()) } }
        val opening = previous?.let { MPosJsonNumbers.amount(it, "countedCash") } ?: 0.0
        require(opening.isFinite() && opening >= 0) { "invalid previous counted cash" }
        val envelope = employees.read()
        val parser = JSONTokener(if (envelope.getBoolean("found")) envelope.getString("payload") else "null")
        val parsed = parser.nextValue()
        require(parser.nextClean() == '\u0000' && (parsed is JSONArray || parsed === JSONObject.NULL))
        val staff = parsed as? JSONArray ?: JSONArray()
        val entries = (0 until staff.length()).map { staff.getJSONObject(it) }
        require(entries.all { it.opt("id") is String && it.getString("id").isNotBlank() && it.opt("name") is String })
        require(entries.map { it.getString("id") }.distinct().size == entries.size)
        val collator = Collator.getInstance(Locale("ru", "RU"))
        val sorted = entries.sortedWith { a, b -> collator.compare(a.getString("name"), b.getString("name")) }
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-shift-opening-form").put("openingCash", opening)
            .put("employees", JSONArray(sorted.map { JSONObject().put("id", it.getString("id")).put("name", it.getString("name")).put("role", if (it.opt("role") == "admin") "admin" else "cashier") }))
    }
}
