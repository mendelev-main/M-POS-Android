package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Reviewed form construction remains an adapter; native live permissions govern its durable product write. */
class MPosProductEditorCommand(private val database: MPosDatabase, private val grants: MPosEditorGrants) {
    suspend fun commit(raw: String): JSONObject {
        val input = JSONObject(raw)
        require(input.getInt("version") == 1)
        val tokens = input.optJSONObject("grants") ?: JSONObject()
        val usedTokens = tokens.keys().asSequence().map(tokens::getString).toList()
        val result = database.withTransaction {
            val root = MPosRootSessionRepository(database).read()
            check(!root.recoveryPending) { "Перезапустите M POS для восстановления данных" }
            val storage = MPosCatalogStorage(database)
            fun records(document: JSONObject): JSONArray {
                if (!document.getBoolean("found")) return JSONArray()
                val parser = JSONTokener(document.getString("payload")); val value = parser.nextValue()
                require(parser.nextClean() == '\u0000')
                return if (value === JSONObject.NULL) JSONArray() else value as JSONArray
            }
            val before = records(storage.read())
            check(MPosSupplyParity.same(before, input.getJSONArray("expected"))) { "Товар изменился во время сохранения. Откройте карточку заново" }
            val id = input.opt("editingId")
            val editing = MPosJsonNumbers.truthy(id)
            val index = if (editing) (0 until before.length()).firstOrNull { MPosSupplyParity.same(before.getJSONObject(it).opt("id"), id) } else null
            check(!editing || index != null) { "Товар изменился во время сохранения. Откройте карточку заново" }
            val next = input.getJSONArray("nextProducts")
            require(next.length() == before.length() + (if (editing) 0 else 1))
            for (i in 0 until before.length()) if (i != index) require(MPosSupplyParity.same(before.getJSONObject(i), next.getJSONObject(i)))
            val candidate = next.getJSONObject(index ?: before.length())
            val existing = index?.let(before::getJSONObject)
            if (editing) require(MPosSupplyParity.same(candidate.get("id"), id))
            else require((0 until before.length()).none { MPosSupplyParity.same(before.getJSONObject(it).opt("id"), candidate.get("id")) })
            val baseline = existing ?: JSONObject.NULL
            fun permitted(operation: String) = root.isAdmin || grants.allows(tokens.optString(operation), operation, id, baseline)
            if (MPosJsonNumbers.truthy(candidate.opt("noStockTracking")) != MPosJsonNumbers.truthy(existing?.opt("noStockTracking"))) {
                check(permitted("no-stock")) { "Изменение учёта остатков требует разрешения администратора" }
            }
            if (existing != null && existing.opt("type") == "simple" && candidate.opt("type") == "simple") {
                val stockChanged = existing.optDouble("stock", 0.0) != candidate.optDouble("stock", 0.0)
                val unitChanged = existing.optString("stockUnit") != candidate.optString("stockUnit")
                val displayChanged = existing.optString("stockDisplayUnit", existing.optString("stockUnit")) != candidate.optString("stockDisplayUnit", candidate.optString("stockUnit"))
                val costChanged = existing.optDouble("cost", 0.0) != candidate.optDouble("cost", 0.0)
                val minimumChanged = !MPosSupplyParity.same(existing.opt("minStock") ?: JSONObject.NULL, candidate.opt("minStock") ?: JSONObject.NULL)
                if (stockChanged || unitChanged || displayChanged || costChanged || minimumChanged) check(permitted("stock")) { "Изменение остатка требует разрешения администратора" }
            }
            if (existing == null && candidate.opt("type") == "simple" && candidate.optDouble("stock", 0.0) != 0.0) check(permitted("stock")) { "Изменение остатка требует разрешения администратора" }
            if (!root.isAdmin) {
                fun online(row: JSONObject?, key: String, creating: Boolean = false) = if (row == null) !creating else row.opt(key) != false
                for (key in listOf("availableOnline", "availableInOnlineMenu")) check(online(candidate, key) == online(existing, key, true)) { "Настройку WEB может изменять только администратор при открытой им смене" }
                check(MPosSupplyParity.same(candidate.opt("description") ?: JSONObject.NULL, existing?.opt("description") ?: if (editing) JSONObject.NULL else "")) { "Настройку WEB может изменять только администратор при открытой им смене" }
            }
            // Return/history/type policy is re-evaluated against current Room documents at commit.
            val decision = MPosCatalogEditEngine.calculate(JSONObject().put("version",1).put("operation","product")
                .put("editingId",id ?: JSONObject.NULL).put("name",candidate.getString("name")).put("type",candidate.getString("type"))
                .put("components",candidate.optJSONArray("components") ?: JSONArray()).put("products",before)
                .put("orders",records(MPosOrderStorage(database).read())))
            check(decision.getBoolean("allowed")) { decision.optString("message") }
            storage.write(next.toString())
            JSONObject().put("ok",true).put("authoritative",true).put("source","room-product-editor")
        }
        // Consume only after successful commit, allowing correction after a known rollback.
        grants.consume(usedTokens)
        return result
    }
}
