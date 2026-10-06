package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

class MPosRecipeEditRepository(private val database: MPosDatabase) {
    suspend fun calculate(raw: String): JSONObject = database.withTransaction {
        val catalog=MPosCatalogStorage(database);check(catalog.isAuthoritative())
        val input=JSONObject(raw);val doc=catalog.read()
        input.put("products",if(!doc.getBoolean("found")||doc.getString("payload")=="null")JSONArray() else JSONArray(doc.getString("payload")))
        MPosRecipeEditEngine.calculate(input)
    }
}
