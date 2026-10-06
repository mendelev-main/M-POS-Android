package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class MPosCatalogEditRepository(private val database: MPosDatabase) {
    suspend fun calculate(raw: String): JSONObject = database.withTransaction {
        val input = JSONObject(raw)
        if (input.optString("operation") == "product") {
            val catalog = MPosCatalogStorage(database); val archive = MPosOrderStorage(database)
            check(catalog.isAuthoritative() && archive.isAuthoritative())
            fun array(doc: JSONObject) = if (!doc.getBoolean("found") || doc.getString("payload") == "null") JSONArray() else JSONArray(doc.getString("payload"))
            input.put("products", array(catalog.read())).put("orders", array(archive.read()))
        }
        MPosCatalogEditEngine.calculate(input)
    }
}
