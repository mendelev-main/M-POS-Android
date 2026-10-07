package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject

/** Native startup projection reads the owned document, never a browser cache. */
class MPosSessionRestoreRepository(private val database: MPosDatabase) {
    suspend fun prepare(input: JSONObject): JSONObject = database.withTransaction {
        require(input.getInt("version") == 1)
        check(MPosRecoveryStorage(database).isAuthoritative("currentOrderSession"))
        val document = MPosRuntimeSnapshot(database).read(setOf("currentOrderSession"))["currentOrderSession"]
        check(document != null) { "current-order document is absent" }
        val session = JSONObject(document.payload)
        // A newer saved order must not be installed over the caller's current startup state.
        check(MPosSupplyParity.same(session, input.optJSONObject("session"))) { "current-order restore conflict" }
        MPosSessionRestoreEngine.calculate(JSONObject().put("version", 1).put("session", session))
    }
}
