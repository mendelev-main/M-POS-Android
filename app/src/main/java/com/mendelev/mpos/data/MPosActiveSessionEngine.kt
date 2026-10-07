package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

/** Reviewed bootstrap normalization and first-open-shift selection, without effects. */
object MPosActiveSessionEngine {
    fun calculate(input: JSONObject): JSONObject {
        require(input.getInt("version") == 1)
        val warnings = JSONArray()
        fun records(key: String): JSONArray {
            val envelope = input.getJSONObject(key)
            val value = if (envelope.getBoolean("found")) envelope.opt("value") else JSONArray()
            if (value !is JSONArray) {
                warnings.put("Некорректный формат $key")
                return JSONArray()
            }
            val result = JSONArray()
            for (i in 0 until value.length()) value.optJSONObject(i)?.let { result.put(JSONObject(it.toString())) }
            if (result.length() != value.length()) warnings.put("Некорректные записи $key")
            return result
        }
        val shifts = records("shifts")
        val employees = records("employees")
        for (i in 0 until employees.length()) {
            val row = employees.getJSONObject(i)
            row.put("role", if (row.opt("role") == "admin") "admin" else "employee")
        }
        val shiftIndex = (0 until shifts.length()).firstOrNull { shifts.getJSONObject(it).opt("status") == "open" }
        val shift = shiftIndex?.let(shifts::getJSONObject)
        val employeeIndex = shift?.let { current ->
            (0 until employees.length()).firstOrNull { sameId(employees.getJSONObject(it), "id", current, "employeeId") }
        }
        return JSONObject().put("ok", true).put("authoritative", true).put("source", "native-active-session")
            .put("shifts", shifts).put("employees", employees).put("warnings", warnings)
            .put("activeShiftIndex", shiftIndex ?: JSONObject.NULL)
            .put("activeEmployeeIndex", employeeIndex ?: JSONObject.NULL)
            .put("isAdmin", employeeIndex?.let { employees.getJSONObject(it).opt("role") == "admin" } ?: false)
    }

    /** JavaScript strict equality: absent differs from JSON null; object IDs never match. */
    private fun sameId(a: JSONObject, ak: String, b: JSONObject, bk: String): Boolean {
        if (!a.has(ak) || !b.has(bk)) return !a.has(ak) && !b.has(bk)
        val left = a.get(ak)
        val right = b.get(bk)
        return when {
            left === JSONObject.NULL || right === JSONObject.NULL -> left === right
            left is Number && right is Number -> left.toDouble() == right.toDouble()
            left is String && right is String -> left == right
            left is Boolean && right is Boolean -> left == right
            else -> false
        }
    }
}
