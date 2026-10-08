package com.mendelev.mpos.data

import androidx.room.withTransaction
import com.mendelev.mpos.payment.MPosOrderContextEngine
import org.json.JSONObject
import org.json.JSONTokener

/** Live authorization, expected document and company write share one Room transaction. */
class MPosCompanyCommand(private val database: MPosDatabase) {
    suspend fun commit(serialized: String): JSONObject = database.withTransaction {
        val input = JSONObject(serialized)
        require(input.getInt("version") == 1)
        val root = MPosRootSessionRepository(database).read()
        val shift = root.currentShift
        check(root.isAdmin && shift != null && MPosSupplyParity.same(shift.opt("id"), input.opt("shiftId"))) {
            "Изменять реквизиты может только администратор при открытой им смене"
        }
        val storage = MPosWorkspaceStorage(database)
        val document = storage.read("company")
        val defaults = JSONObject().apply { FIELDS.forEach { put(it, "") } }
        val value = if (document.getBoolean("found")) {
            val parser = JSONTokener(document.getString("payload"))
            val parsed = parser.nextValue()
            require(parser.nextClean() == '\u0000' && (parsed === JSONObject.NULL || parsed is JSONObject))
            if (parsed is JSONObject) parsed else JSONObject()
        } else JSONObject()
        value.keys().forEach { defaults.put(it, value.get(it)) }
        check(MPosSupplyParity.same(defaults, input.getJSONObject("expected"))) { "Реквизиты изменились. Откройте форму заново" }
        val fields = input.getJSONObject("fields")
        require(fields.keys().asSequence().toSet() == FIELDS.toSet())
        // Reviewed human editing replaces the four fields; import retains raw JSON/unknown fields until editing.
        val next = JSONObject().apply { FIELDS.forEach { put(it, MPosOrderContextEngine.trim(fields.getString(it))) } }
        storage.write("company", next.toString())
        JSONObject().put("ok", true).put("authoritative", true).put("company", next).put("source", "room-company-command")
    }

    companion object {
        private val FIELDS = listOf("establishmentName", "legalName", "address", "deliveryAddress")
    }
}
