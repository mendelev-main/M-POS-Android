package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Transient credential verification against the current editor document; no persistent password or flag. */
class MPosEditorAuthorization(
    private val database: MPosDatabase,
    private val grants: MPosEditorGrants,
    private val verifies: (String) -> Boolean = MPosAdministratorCredential::accepts,
) {
    suspend fun authorize(raw: String, credential: String): JSONObject = database.withTransaction {
        val input = JSONObject(raw)
        require(input.getInt("version") == 1)
        val operation = input.getString("operation")
        require(operation in setOf("stock", "no-stock"))
        check(verifies(credential)) { "Неверный пароль администратора" }
        val root = MPosRootSessionRepository(database).read()
        check(!root.recoveryPending) { "Перезапустите M POS для восстановления данных" }
        val document = MPosCatalogStorage(database).read()
        val parsed = if (document.getBoolean("found")) {
            val parser = JSONTokener(document.getString("payload"))
            parser.nextValue().also { require(parser.nextClean() == '\u0000') }
        } else JSONObject.NULL
        val products = if (parsed === JSONObject.NULL) JSONArray() else parsed as JSONArray
        val id = input.opt("productId")
        val existing = (0 until products.length()).map { products.getJSONObject(it) }.firstOrNull { MPosSupplyParity.same(it.opt("id"), id) }
        check(MPosSupplyParity.same(existing ?: JSONObject.NULL, input.get("expected"))) { "Товар изменился. Откройте карточку заново" }
        require(operation != "stock" || existing != null)
        JSONObject().put("ok", true).put("granted", true).put("operation", operation).put("productId", id ?: JSONObject.NULL).put("grant", grants.issue(operation, id, existing ?: JSONObject.NULL))
    }
}
