package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Toggle only the reviewed online-order flag; unrelated editor validation cannot block it. */
class MPosProductWebCommand(private val database: MPosDatabase) {
    suspend fun commit(raw: String): JSONObject = database.withTransaction {
        val input = JSONObject(raw); require(input.getInt("version") == 1)
        val root = MPosRootSessionRepository(database).read()
        check(root.isAdmin) { "Настройку WEB может изменять только администратор при открытой им смене" }
        check(!root.recoveryPending) { "Перезапустите M POS для восстановления данных" }
        val storage = MPosCatalogStorage(database); val document = storage.read()
        val products = if (!document.getBoolean("found")) JSONArray() else {
            val parser = JSONTokener(document.getString("payload")); val parsed = parser.nextValue(); require(parser.nextClean() == '\u0000')
            if (parsed === JSONObject.NULL) JSONArray() else parsed as JSONArray
        }
        check(MPosSupplyParity.same(products, input.getJSONArray("expected"))) { "Каталог изменился. Повторите действие" }
        val product = (0 until products.length()).map { products.getJSONObject(it) }.firstOrNull { MPosSupplyParity.same(it.opt("id"), input.get("id")) }
            ?: return@withTransaction JSONObject().put("ok",true).put("authoritative",true).put("changed",false)
        product.put("availableOnline", product.opt("availableOnline") == false)
        storage.write(products.toString())
        JSONObject().put("ok",true).put("authoritative",true).put("changed",true).put("products",products).put("enabled",product.getBoolean("availableOnline"))
    }
}
