package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Fresh native root context. No cross-import cache and no implicit journal replay. */
class MPosRootSessionRepository(private val database: MPosDatabase) {
    companion object {
        internal val AUTHORITY_KEYS = setOf(MPosShiftStorage.AUTHORITY_KEY, MPosEmployeeStorage.AUTHORITY_KEY,
            MPosRecoveryStorage.authorityKey("criticalStorageJournal"))
        internal val KEYS = (AUTHORITY_KEYS + setOf("shifts", "employees", "criticalStorageJournal")).toList()
        internal fun owned(rows: List<LegacyStorageShadowEntity>) = rows.map { it.key }.containsAll(AUTHORITY_KEYS)
    }

    class Snapshot internal constructor(
        shifts: JSONArray,
        employees: JSONArray,
        val recoveryPending: Boolean,
        private val projection: JSONObject,
    ) {
        private val shiftDocument = shifts.toString()
        private val employeeDocument = employees.toString()
        val shifts: JSONArray get() = JSONArray(shiftDocument)
        val employees: JSONArray get() = JSONArray(employeeDocument)
        val currentShift: JSONObject? get() = record("shifts", "activeShiftIndex")
        val selectedEmployee: JSONObject? get() = record("employees", "activeEmployeeIndex")
        val isAdmin: Boolean get() = projection.getBoolean("isAdmin")
        private fun record(key: String, index: String): JSONObject? =
            if (projection.isNull(index)) null else JSONObject(projection.getJSONArray(key).getJSONObject(projection.getInt(index)).toString())
        fun bootstrap(): JSONObject = JSONObject(projection.toString()).put("recoveryPending", recoveryPending)
    }

    suspend fun read(): Snapshot = database.withTransaction {
        project(database.legacyStorageShadowDao().getAll(KEYS))
    }

    /** One query supplies documents and authority markers from the same SQLite snapshot. */
    internal fun project(rows: List<LegacyStorageShadowEntity>): Snapshot {
        check(owned(rows))
        val documents = rows.associateBy { it.key }
        fun parse(key: String): Any {
            val document = documents[key] ?: return JSONObject.NULL
            val parser = JSONTokener(document.payload)
            val value = parser.nextValue()
            require(parser.nextClean() == '\u0000')
            return value
        }
        val input = JSONObject().put("version", 1)
        for (key in listOf("shifts", "employees")) {
            val value = parse(key)
            require(value is JSONArray || value === JSONObject.NULL)
            input.put(key, JSONObject().put("found", documents[key] != null).put("value", value))
        }
        val model = MPosActiveSessionEngine.calculate(input)
        return Snapshot(
            input.getJSONObject("shifts").optJSONArray("value") ?: JSONArray(),
            input.getJSONObject("employees").optJSONArray("value") ?: JSONArray(),
            parse("criticalStorageJournal") !== JSONObject.NULL,
            model,
        )
    }
}
