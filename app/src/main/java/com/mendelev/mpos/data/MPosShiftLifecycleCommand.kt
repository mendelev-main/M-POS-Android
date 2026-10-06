package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest

/** Native shift lifecycle/state; the reviewed administrator authentication remains in the UI. */
class MPosShiftLifecycleCommand(private val database: MPosDatabase) {
    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw)
        val command = parser.nextValue()
        require(command is JSONObject && parser.nextClean() == '\u0000')
        val operation = command.getString("operation")
        require(operation in setOf("open", "close"))
        val offered = command.getJSONObject("shift")
        val id = offered.getString("id")
        require(offered.opt("id") is String && id.isNotBlank() && id == id.trim())
        val timeKey = if (operation == "open") "openedAt" else "closedAt"
        val at = offered.getLong(timeKey)
        require(offered.opt(timeKey) is Number && at > 0 && offered.getDouble(timeKey) == at.toDouble())
        val documents = database.legacyStorageShadowDao()
        val shifts = MPosShiftStorage(database)
        val recovery = MPosRecoveryStorage(database)
        check(shifts.isAuthoritative() && recovery.isAuthoritative("criticalStorageJournal"))
        val before = shifts.readRecords()
        val records = (0 until before.length()).map { before.getJSONObject(it) }
        val marker = "mpos_shift_lifecycle_v1:$operation:$id:$at"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        documents.get(marker)?.let {
            check(it.payload == hash) { "shift lifecycle has a different command" }
            val existing = records.singleOrNull { record -> record.optString("id") == id }
            check(existing != null) { "committed shift was removed; reload data" }
            if (operation == "close") check(same(existing, offered) && existing.optString("status") == "closed") { "closed shift was restored or changed" }
            else check(existing.optString("status") in setOf("open", "closed") && offered.keys().asSequence().filter { key -> key != "status" }.all { key -> same(existing.opt(key), offered.opt(key)) }) { "opened shift was restored or changed" }
            return@withTransaction acknowledgement(id, operation, true)
        }
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        check(same(before, command.getJSONArray("expectedShifts"))) { "shift state changed" }
        val after = JSONArray(before.toString())
        var expectedCash: Double? = null
        var difference: Double? = null
        if (operation == "open") {
            check(records.none { it.optString("status") == "open" }) { "shift already open" }
            check(records.none { it.optString("id") == id }) { "shift ID already exists" }
            val employees = MPosEmployeeStorage(database)
            check(employees.isAuthoritative())
            val staff = JSONArray(employees.read().getString("payload"))
            check(same(staff, command.getJSONArray("expectedEmployees"))) { "employee state changed" }
            val employee = (0 until staff.length()).map { staff.getJSONObject(it) }.single { it.optString("id") == offered.optString("employeeId") }
            val previous = records.filter { it.optString("status") == "closed" }.maxByOrNull { numberOrZero(it, "closedAt").also { value -> require(value.isFinite()) } }
            val opening = previous?.let { numberOrZero(it, "countedCash") } ?: 0.0
            require(opening.isFinite() && opening >= 0) { "invalid previous counted cash" }
            val created = JSONObject().put("id", id).put("status", "open").put("openedAt", at).put("openingCash", opening)
                .put("employeeId", employee.get("id")).put("employeeName", employee.get("name"))
                .put("employeePhone", employee.opt("phone").takeIf(::truthy) ?: "").put("openingSourceShiftId", previous?.opt("id").takeIf(::truthy) ?: "")
            check(same(created, offered)) { "opening shift differs from native carryover/employee" }
            after.put(created)
        } else {
            val target = records.firstOrNull { it.optString("status") == "open" }
            check(target != null && target.optString("id") == id) { "target is not the current open shift" }
            val orders = MPosOrderStorage(database)
            check(orders.isAuthoritative())
            val envelope = orders.read()
            val parsed = JSONTokener(if (envelope.getBoolean("found")) envelope.getString("payload") else "null").nextValue()
            require(parsed is JSONArray || parsed === JSONObject.NULL)
            val archive = parsed as? JSONArray ?: JSONArray()
            check(archive.length() == command.getInt("expectedOrderCount")) { "receipt archive changed" }
            expectedCash = MPosShiftAccounting.balance(target, archive)
            require(expectedCash.isFinite() && expectedCash >= 0) { "invalid closing drawer" }
            val offeredExpected = command.getDouble("expectedCash")
            require(offeredExpected.isFinite() && kotlin.math.abs(expectedCash - offeredExpected) <= 0.000001) { "closing cash changed" }
            require(offered.opt("countedCash") is Number)
            val counted = offered.getDouble("countedCash")
            require(counted.isFinite() && counted >= 0) { "invalid counted cash" }
            difference = counted - expectedCash
            val closed = JSONObject(target.toString()).put("status", "closed").put("closedAt", at).put("countedCash", counted)
            check(same(closed, offered)) { "closing shift differs" }
            val index = records.indexOf(target)
            after.put(index, closed)
        }
        check(same(after, command.getJSONArray("shifts"))) { "lifecycle result differs" }
        shifts.write(after.toString())
        documents.upsert(LegacyStorageShadowEntity(marker, hash, System.currentTimeMillis()))
        acknowledgement(id, operation, false).also { result ->
            expectedCash?.let { result.put("expectedCash", it).put("difference", difference) }
        }
    }

    companion object {
        /** Only fixed messages leave this boundary; parser/SQL text may contain source records. */
        fun failureMessage(error: Throwable): String = when (error.message) {
            "shift state changed", "employee state changed", "receipt archive changed", "closing cash changed" -> "Данные смены изменились. Перезапустите приложение перед повтором."
            "shift already open", "target is not the current open shift" -> "Текущая смена изменилась. Перезапустите приложение."
            "pending critical operation" -> "Есть незавершённая операция хранения. Перезапустите приложение для восстановления."
            "invalid previous counted cash", "invalid closing drawer", "invalid counted cash" -> "Некорректная сумма наличных в смене. Проверьте данные кассы."
            else -> "Не удалось сохранить смену в локальной базе. Перезапустите приложение; если ошибка повторяется, сообщите в поддержку."
        }
    }

    private fun acknowledgement(id: String, operation: String, replayed: Boolean) = JSONObject().put("ok", true).put("authoritative", true)
        .put("source", "room-shift-lifecycle").put("shiftId", id).put("operation", operation).put("replayed", replayed)
    private fun truthy(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
        is String -> value.isNotEmpty()
        else -> true
    }
    private fun numberOrZero(record: JSONObject, key: String): Double {
        val value = record.opt(key)
        if (!truthy(value)) return 0.0
        if (value is Boolean) return 1.0
        if (value is String && value.isBlank()) return 0.0
        return record.optDouble(key, Double.NaN)
    }
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }
}
