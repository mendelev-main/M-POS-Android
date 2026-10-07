package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONTokener

/** Owned shift/employee documents are read together, without initializing or repairing them. */
class MPosActiveSessionRepository(private val database: MPosDatabase) {
    suspend fun bootstrap(): JSONObject = database.withTransaction {
        check(MPosShiftStorage(database).isAuthoritative())
        check(MPosEmployeeStorage(database).isAuthoritative())
        val snapshot = MPosRuntimeSnapshot(database).read(setOf("shifts", "employees"))
        val input = JSONObject().put("version", 1)
        for (key in listOf("shifts", "employees")) {
            val document = snapshot[key]
            val envelope = JSONObject().put("found", document != null)
            if (document != null) {
                val parser = JSONTokener(document.payload)
                envelope.put("value", parser.nextValue())
                require(parser.nextClean() == '\u0000')
            }
            input.put(key, envelope)
        }
        MPosActiveSessionEngine.calculate(input)
    }
}
