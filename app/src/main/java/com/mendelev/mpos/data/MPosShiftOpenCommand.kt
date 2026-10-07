package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Native opening authority. Credentials are transient arguments, never command/journal fields. */
class MPosShiftOpenCommand(private val database: MPosDatabase) {
    suspend fun commit(input: JSONObject, credential: String): JSONObject = database.withTransaction {
        require(input.getInt("version") == 1)
        val shifts = MPosShiftStorage(database)
        val employees = MPosEmployeeStorage(database)
        check(shifts.isAuthoritative() && employees.isAuthoritative())
        val before = shifts.readRecords()
        val document = employees.read()
        val parser = JSONTokener(if (document.getBoolean("found")) document.getString("payload") else "null")
        val parsed = parser.nextValue()
        require(parser.nextClean() == '\u0000' && (parsed is JSONArray || parsed === JSONObject.NULL))
        val staff = parsed as? JSONArray ?: JSONArray()
        check(MPosSupplyParity.same(before, input.getJSONArray("expectedShifts"))) { "shift state changed" }
        check(MPosSupplyParity.same(staff, input.getJSONArray("expectedEmployees"))) { "employee state changed" }
        val employeeId = input.getString("employeeId")
        require(employeeId.isNotBlank()) { "employee not selected" }
        val matches = (0 until staff.length()).map { staff.getJSONObject(it) }.filter { it.opt("id") == employeeId }
        check(matches.size == 1) { "employee not selected" }
        val employee = matches.single()
        if (employee.opt("role") == "admin") check(MPosAdministratorCredential.accepts(credential)) { "administrator credential rejected" }
        // Reuse the established read gate: no open shift, valid carryover, no pending journal.
        val opening = MPosShiftOpeningRepository(database).read().getDouble("openingCash")
        val records = (0 until before.length()).map { before.getJSONObject(it) }
        val previous = records.filter { it.opt("status") == "closed" }
            .maxByOrNull { MPosJsonNumbers.amount(it, "closedAt") }
        val shift = JSONObject().put("id", input.getString("id")).put("status", "open")
            .put("openedAt", input.get("openedAt")).put("openingCash", opening)
            .put("employeeId", employee.get("id")).put("employeeName", employee.get("name"))
            .put("employeePhone", employee.opt("phone").takeIf(MPosJsonNumbers::truthy) ?: "")
            .put("openingSourceShiftId", previous?.opt("id").takeIf(MPosJsonNumbers::truthy) ?: "")
        val after = JSONArray(before.toString()).put(shift)
        val command = JSONObject().put("operation", "open").put("shift", shift)
            .put("expectedEmployees", staff).put("expectedShifts", before).put("shifts", after)
        val result = MPosShiftLifecycleCommand(database).commit(command.toString())
        result.put("source", "room-native-shift-open").put("shift", shift).put("shifts", after)
    }

    companion object {
        fun failure(error: Throwable): JSONObject {
            val message = when (error.message) {
                "administrator credential rejected" -> "Для открытия смены администратором введите верный пароль"
                "employee not selected" -> "Выберите сотрудника"
                else -> MPosShiftLifecycleCommand.failureMessage(error)
            }
            val blocked = error.message in setOf("shift state changed", "employee state changed", "shift already open", "pending critical operation")
            return JSONObject().put("ok", false).put("message", message).put("blocked", blocked)
        }
    }
}
