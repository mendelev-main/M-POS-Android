package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns the compatible employees document; structured indexes never replace its full JSON. */
class MPosEmployeeStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val employeeDao = database.employeeProjectionDao()
    companion object { const val AUTHORITY_KEY = "mpos_employees_authority_v1" }

    suspend fun isAuthoritative(): Boolean = documents.get(AUTHORITY_KEY) != null

    suspend fun initialize(legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative()) {
            replace(legacy)
            documents.upsert(LegacyStorageShadowEntity(AUTHORITY_KEY, "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement()
    }

    private fun acknowledgement() = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-employees")

    suspend fun read(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native employees is not initialized" }
        val document = documents.get("employees")
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-employees")
            .put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)

    }

    suspend fun write(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native employees is not initialized" }
        replace(serialized)
        acknowledgement()
    }

    suspend fun remove(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native employees is not initialized" }
        replace(null)
        acknowledgement()
    }

    private suspend fun replace(serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid employees JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONArray) { "employees must be an array or null" }
        if (serialized == null) documents.delete("employees")
        else documents.upsert(LegacyStorageShadowEntity("employees", serialized, System.currentTimeMillis()))
        projectArray(parsed as? JSONArray ?: JSONArray())
    }

    suspend fun project(serialized: String) {
        projectArray(JSONArray(serialized))
    }

    private suspend fun projectArray(source: JSONArray) {
        val now = System.currentTimeMillis()
        val employees = ArrayList<EmployeeProjectionEntity>(source.length())

        for (index in 0 until source.length()) {
            val employee = source.optJSONObject(index) ?: continue
            val id = employee.optString("id").trim()
            if (id.isEmpty()) continue

            employees += EmployeeProjectionEntity(
                id = id,
                name = employee.optString("name"),
                phone = employee.optString("phone"),
                role = employee.optString("role", "employee"),
                sortIndex = index,
                payload = employee.toString(),
                updatedAt = now,
            )
        }

        database.withTransaction {
            employeeDao.clear()
            if (employees.isNotEmpty()) employeeDao.insertAll(employees)
        }
    }

}
