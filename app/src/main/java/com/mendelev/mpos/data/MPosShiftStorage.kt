package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns the compatible shifts document; structured indexes never replace its full JSON. */
class MPosShiftStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val shiftDao = database.shiftProjectionDao()
    companion object { const val AUTHORITY_KEY = "mpos_shifts_authority_v1" }

    suspend fun isAuthoritative(): Boolean = documents.get(AUTHORITY_KEY) != null

    suspend fun initialize(legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative()) {
            replace(legacy)
            documents.upsert(LegacyStorageShadowEntity(AUTHORITY_KEY, "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement()
    }

    private fun acknowledgement() = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-shifts")

    suspend fun read(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native shifts is not initialized" }
        val document = documents.get("shifts")
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-shifts")
            .put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)

    }

    suspend fun write(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native shifts is not initialized" }
        replace(serialized)
        acknowledgement()
    }

    suspend fun remove(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native shifts is not initialized" }
        replace(null)
        acknowledgement()
    }

    private suspend fun replace(serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid shifts JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONArray) { "shifts must be an array or null" }
        if (serialized == null) documents.delete("shifts")
        else documents.upsert(LegacyStorageShadowEntity("shifts", serialized, System.currentTimeMillis()))
        projectArray(parsed as? JSONArray ?: JSONArray())
    }

    suspend fun project(serialized: String) {
        projectArray(JSONArray(serialized))
    }

    // SQLite cannot store NaN in a NOT NULL index. Keep the untouched document authoritative;
    // this fallback is only for diagnostic indexes, never a cash calculation.
    private fun indexAmount(record: JSONObject, key: String): Double =
        record.optDouble(key, 0.0).takeIf { it.isFinite() } ?: 0.0

    private suspend fun projectArray(source: JSONArray) {
        val now = System.currentTimeMillis()
        val shifts = ArrayList<ShiftProjectionEntity>(source.length())
        val movements = ArrayList<CashMovementProjectionEntity>()

        for (shiftIndex in 0 until source.length()) {
            val shift = source.optJSONObject(shiftIndex) ?: continue
            val shiftId = shift.optString("id").trim()
            if (shiftId.isEmpty()) continue

            shifts += ShiftProjectionEntity(
                id = shiftId,
                status = shift.optString("status"),
                employeeId = shift.optString("employeeId"),
                employeeName = shift.optString("employeeName"),
                employeePhone = shift.optString("employeePhone"),
                openedAt = shift.optLong("openedAt"),
                closedAt = shift.optLong("closedAt"),
                openingCash = indexAmount(shift, "openingCash"),
                countedCash = indexAmount(shift, "countedCash"),
                sortIndex = shiftIndex,
                payload = shift.toString(),
                updatedAt = now,
            )

            val sourceMovements = shift.optJSONArray("cashMovements") ?: JSONArray()
            for (movementIndex in 0 until sourceMovements.length()) {
                val movement = sourceMovements.optJSONObject(movementIndex) ?: continue
                val movementId = movement.optString("id").trim()
                if (movementId.isEmpty()) continue
                movements += CashMovementProjectionEntity(
                    id = movementId,
                    shiftId = shiftId,
                    type = movement.optString("type"),
                    subtype = movement.optString("subtype"),
                    amount = indexAmount(movement, "amount"),
                    timestamp = movement.optLong("timestamp"),
                    note = movement.optString("note"),
                    sortIndex = movementIndex,
                    payload = movement.toString(),
                    updatedAt = now,
                )
            }
        }

        database.withTransaction {
            shiftDao.clearMovements()
            shiftDao.clearShifts()
            if (shifts.isNotEmpty()) shiftDao.insertShifts(shifts)
            if (movements.isNotEmpty()) shiftDao.insertMovements(movements)
        }
    }

}
