package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Fresh native root context. No cross-import cache and no implicit journal replay. */
class MPosRootSessionRepository(private val database: MPosDatabase) {
    class Snapshot internal constructor(
        val shifts: JSONArray,
        val employees: JSONArray,
        val recoveryPending: Boolean,
        private val projection: JSONObject,
    ) {
        val currentShift: JSONObject? get() = record("shifts", "activeShiftIndex")
        val selectedEmployee: JSONObject? get() = record("employees", "activeEmployeeIndex")
        val isAdmin: Boolean get() = projection.getBoolean("isAdmin")
        private fun record(key: String, index: String): JSONObject? =
            if (projection.isNull(index)) null else JSONObject(projection.getJSONArray(key).getJSONObject(projection.getInt(index)).toString())
        fun bootstrap(): JSONObject = JSONObject(projection.toString()).put("recoveryPending", recoveryPending)
    }

    suspend fun read(): Snapshot = database.withTransaction {
        check(MPosShiftStorage(database).isAuthoritative())
        check(MPosEmployeeStorage(database).isAuthoritative())
        check(MPosRecoveryStorage(database).isAuthoritative("criticalStorageJournal"))
        val documents = MPosRuntimeSnapshot(database).read(setOf("shifts", "employees", "criticalStorageJournal"))
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
        Snapshot(
            input.getJSONObject("shifts").optJSONArray("value") ?: JSONArray(),
            input.getJSONObject("employees").optJSONArray("value") ?: JSONArray(),
            parse("criticalStorageJournal") !== JSONObject.NULL,
            model,
        )
    }
}
