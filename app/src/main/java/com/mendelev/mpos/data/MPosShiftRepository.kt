package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

class MPosShiftRepository(
    private val database: MPosDatabase,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val shiftDao = database.shiftProjectionDao()

    suspend fun parityReport(): JSONObject {
        val legacy = shadowDao.get("shifts")
            ?: return JSONObject()
                .put("ok", false)
                .put("authoritative", false)
                .put("reason", "legacy shifts shadow is not available")

        val source = JSONArray(legacy.payload)
        val sourceShifts = linkedMapOf<String, JSONObject>()
        val sourceMovements = linkedMapOf<String, Pair<String, JSONObject>>()

        for (shiftIndex in 0 until source.length()) {
            val shift = source.optJSONObject(shiftIndex) ?: continue
            val shiftId = shift.optString("id").trim()
            if (shiftId.isEmpty()) continue
            sourceShifts[shiftId] = shift
            val movements = shift.optJSONArray("cashMovements") ?: JSONArray()
            for (movementIndex in 0 until movements.length()) {
                val movement = movements.optJSONObject(movementIndex) ?: continue
                val movementId = movement.optString("id").trim()
                if (movementId.isEmpty()) continue
                sourceMovements[movementId] = shiftId to movement
            }
        }

        val nativeShifts = shiftDao.allShifts()
        val nativeMovements = shiftDao.allMovements()
        val nativeShiftById = nativeShifts.associateBy { it.id }
        val nativeMovementById = nativeMovements.associateBy { it.id }

        val missingShiftIds = sourceShifts.keys.filterNot(nativeShiftById::containsKey)
        val extraShiftIds = nativeShiftById.keys.filterNot(sourceShifts::containsKey)
        val mismatchedShiftIds = sourceShifts.mapNotNull { (id, sourceShift) ->
            val projected = nativeShiftById[id] ?: return@mapNotNull null
            val matches =
                projected.status == sourceShift.optString("status") &&
                projected.employeeId == sourceShift.optString("employeeId") &&
                projected.employeeName == sourceShift.optString("employeeName") &&
                projected.employeePhone == sourceShift.optString("employeePhone") &&
                projected.openedAt == sourceShift.optLong("openedAt") &&
                projected.closedAt == sourceShift.optLong("closedAt") &&
                sameMoney(projected.openingCash, sourceShift.optDouble("openingCash")) &&
                sameMoney(projected.countedCash, sourceShift.optDouble("countedCash"))
            if (matches) null else id
        }

        val missingMovementIds = sourceMovements.keys.filterNot(nativeMovementById::containsKey)
        val extraMovementIds = nativeMovementById.keys.filterNot(sourceMovements::containsKey)
        val mismatchedMovementIds = sourceMovements.mapNotNull { (id, pair) ->
            val projected = nativeMovementById[id] ?: return@mapNotNull null
            val (shiftId, sourceMovement) = pair
            val matches =
                projected.shiftId == shiftId &&
                projected.type == sourceMovement.optString("type") &&
                projected.subtype == sourceMovement.optString("subtype") &&
                sameMoney(projected.amount, sourceMovement.optDouble("amount")) &&
                projected.timestamp == sourceMovement.optLong("timestamp") &&
                projected.note == sourceMovement.optString("note")
            if (matches) null else id
        }

        val matches =
            missingShiftIds.isEmpty() &&
            extraShiftIds.isEmpty() &&
            mismatchedShiftIds.isEmpty() &&
            missingMovementIds.isEmpty() &&
            extraMovementIds.isEmpty() &&
            mismatchedMovementIds.isEmpty() &&
            sourceShifts.size == nativeShifts.size &&
            sourceMovements.size == nativeMovements.size

        return JSONObject()
            .put("ok", true)
            .put("matches", matches)
            .put("authoritative", false)
            .put("legacyShiftCount", sourceShifts.size)
            .put("nativeShiftCount", nativeShifts.size)
            .put("legacyMovementCount", sourceMovements.size)
            .put("nativeMovementCount", nativeMovements.size)
            .put("missingShiftIds", JSONArray(missingShiftIds))
            .put("extraShiftIds", JSONArray(extraShiftIds))
            .put("mismatchedShiftIds", JSONArray(mismatchedShiftIds))
            .put("missingMovementIds", JSONArray(missingMovementIds))
            .put("extraMovementIds", JSONArray(extraMovementIds))
            .put("mismatchedMovementIds", JSONArray(mismatchedMovementIds))
    }

    private fun sameMoney(left: Double, right: Double): Boolean =
        kotlin.math.abs(left - right) < 0.0001
}
