package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

class MPosEmployeeRepository(
    private val database: MPosDatabase,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val employeeDao = database.employeeProjectionDao()

    suspend fun parityReport(): JSONObject {
        val legacy = shadowDao.get("employees")
            ?: return JSONObject()
                .put("ok", false)
                .put("authoritative", false)
                .put("reason", "legacy employees shadow is not available")

        val source = JSONArray(legacy.payload)
        val sourceById = linkedMapOf<String, JSONObject>()
        for (index in 0 until source.length()) {
            val employee = source.optJSONObject(index) ?: continue
            val id = employee.optString("id").trim()
            if (id.isEmpty()) continue
            sourceById[id] = employee
        }

        val native = employeeDao.all()
        val nativeById = native.associateBy { it.id }

        val missingIds = sourceById.keys.filterNot(nativeById::containsKey)
        val extraIds = nativeById.keys.filterNot(sourceById::containsKey)
        val mismatchedIds = sourceById.mapNotNull { (id, sourceEmployee) ->
            val projected = nativeById[id] ?: return@mapNotNull null
            val matches =
                projected.name == sourceEmployee.optString("name") &&
                projected.phone == sourceEmployee.optString("phone") &&
                projected.role == sourceEmployee.optString("role", "employee")
            if (matches) null else id
        }

        val matches =
            missingIds.isEmpty() &&
            extraIds.isEmpty() &&
            mismatchedIds.isEmpty() &&
            sourceById.size == native.size

        return JSONObject()
            .put("ok", true)
            .put("matches", matches)
            .put("authoritative", false)
            .put("legacyEmployeeCount", sourceById.size)
            .put("nativeEmployeeCount", native.size)
            .put("missingEmployeeIds", JSONArray(missingIds))
            .put("extraEmployeeIds", JSONArray(extraIds))
            .put("mismatchedEmployeeIds", JSONArray(mismatchedIds))
    }
}
