package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest

/** Manual cash deposit/withdrawal only; payment/refund movements have their own commands. */
class MPosCashMovementCommand(private val database: MPosDatabase) {
    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val parser = JSONTokener(raw)
        val command = parser.nextValue()
        require(command is JSONObject && parser.nextClean() == '\u0000')
        val shiftId = command.getString("shiftId")
        require(command.opt("shiftId") is String && shiftId.isNotBlank() && shiftId == shiftId.trim())
        val movement = command.getJSONObject("movement")
        require(movement.keys().asSequence().toSet() == setOf("id", "type", "amount", "timestamp", "note"))
        val id = movement.getString("id")
        require(movement.opt("id") is String && id.isNotBlank() && id == id.trim())
        val type = movement.getString("type")
        require(type in setOf("deposit", "withdrawal"))
        require(movement.opt("amount") is Number)
        val amount = movement.getDouble("amount")
        require(amount.isFinite() && amount > 0)
        val at = movement.getLong("timestamp")
        require(movement.opt("timestamp") is Number && at > 0 && movement.getDouble("timestamp") == at.toDouble())
        require(movement.opt("note") is String)
        val documents = database.legacyStorageShadowDao()
        val shifts = MPosShiftStorage(database)
        val archive = MPosOrderStorage(database)
        val recovery = MPosRecoveryStorage(database)
        check(shifts.isAuthoritative() && archive.isAuthoritative() && recovery.isAuthoritative("criticalStorageJournal"))
        val before = JSONArray(shifts.read().getString("payload"))
        val marker = "mpos_cash_movement_v1:$id:$at"
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        documents.get(marker)?.let {
            check(it.payload == hash) { "cash movement has a different command" }
            val present = (0 until before.length()).mapNotNull { before.optJSONObject(it) }.filter { it.optString("id") == shiftId }
                .flatMap { shift -> val moves = shift.optJSONArray("cashMovements") ?: JSONArray(); (0 until moves.length()).mapNotNull { moves.optJSONObject(it) } }
                .count { same(it, movement) }
            check(present == 1) { "committed movement was restored or removed; reload data" }
            return@withTransaction acknowledgement(id, true)
        }
        val journal = recovery.read("criticalStorageJournal")
        check(!journal.getBoolean("found") || JSONTokener(journal.getString("payload")).nextValue() === JSONObject.NULL) { "pending critical operation" }
        check(same(before, command.getJSONArray("expectedShifts"))) { "shift changed before cash movement" }
        val target = (0 until before.length()).map { before.getJSONObject(it) }.single { it.optString("id") == shiftId }
        check(target.optString("status") == "open") { "cash shift is not open" }
        for (i in 0 until before.length()) {
            val moves = before.getJSONObject(i).optJSONArray("cashMovements") ?: continue
            check((0 until moves.length()).none { moves.optJSONObject(it)?.optString("id") == id }) { "cash movement ID already exists" }
        }
        val receipts = JSONArray(database.orderProjectionDao().allOrders().map { JSONObject(it.payload) })
        val balance = MPosShiftAccounting.balance(target, receipts)
        // Preserve the reviewed rule: a negative/invalid drawer blocks deposits too.
        require(balance.isFinite() && balance >= 0) { "invalid drawer balance" }
        if (type == "withdrawal") require(amount <= balance + 0.0001) { "insufficient cash" }
        val after = JSONArray(before.toString())
        val next = (0 until after.length()).map { after.getJSONObject(it) }.single { it.optString("id") == shiftId }
        val movements = next.optJSONArray("cashMovements") ?: JSONArray().also { next.put("cashMovements", it) }
        movements.put(movement)
        check(same(after, command.getJSONArray("shifts"))) { "cash movement result differs" }
        shifts.write(after.toString())
        documents.upsert(LegacyStorageShadowEntity(marker, hash, System.currentTimeMillis()))
        acknowledgement(id, false)
    }

    private fun acknowledgement(id: String, replayed: Boolean) = JSONObject().put("ok", true).put("authoritative", true)
        .put("source", "room-cash-movement").put("movementId", id).put("replayed", replayed)
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }
}
