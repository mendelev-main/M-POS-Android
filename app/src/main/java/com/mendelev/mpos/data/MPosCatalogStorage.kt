package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns the compatible products document; structured indexes never replace its full JSON. */
class MPosCatalogStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val catalog = database.catalogProjectionDao()
    companion object { const val AUTHORITY_KEY = "mpos_catalog_authority_v1" }

    suspend fun isAuthoritative(): Boolean = documents.get(AUTHORITY_KEY) != null

    suspend fun initialize(legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative()) {
            replace(legacy)
            documents.upsert(LegacyStorageShadowEntity(AUTHORITY_KEY, "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement()
    }

    private fun acknowledgement() = JSONObject().put("ok", true).put("authoritative", true).put("source", "room-catalog")

    suspend fun read(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native catalog is not initialized" }
        val document = documents.get("products")
        JSONObject().put("ok", true).put("authoritative", true).put("source", "room-catalog")
            .put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)
            .put("categories", JSONArray(catalog.allCategories().map {
                JSONObject().put("name", it.name).put("sortIndex", it.sortIndex).put("productCount", it.productCount)
            }))
    }

    suspend fun write(serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native catalog is not initialized" }
        replace(serialized)
        acknowledgement()
    }

    suspend fun remove(): JSONObject = database.withTransaction {
        check(isAuthoritative()) { "native catalog is not initialized" }
        replace(null)
        acknowledgement()
    }

    private suspend fun replace(serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid catalog JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONArray) { "catalog must be an array or null" }
        if (serialized == null) documents.delete("products")
        else documents.upsert(LegacyStorageShadowEntity("products", serialized, System.currentTimeMillis()))
        projectArray(parsed as? JSONArray ?: JSONArray())
    }

    /** Compatibility entry point for pre-cutover diagnostic writes. */
    suspend fun project(serialized: String) = database.withTransaction { projectArray(JSONArray(serialized)) }

    private suspend fun projectArray(source: JSONArray) {
        val now = System.currentTimeMillis()
        val products = ArrayList<ProductProjectionEntity>(source.length())
        val categoryOrder = linkedMapOf<String, Int>()
        val categoryCounts = linkedMapOf<String, Int>()
        for (index in 0 until source.length()) {
            val product = source.optJSONObject(index) ?: continue
            val id = product.optString("id").trim()
            if (id.isEmpty()) continue
            val category = product.optString("category").trim().ifEmpty { "Без категории" }
            if (!categoryOrder.containsKey(category)) categoryOrder[category] = categoryOrder.size
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            products += ProductProjectionEntity(id, product.optString("name"), category,
                product.optString("type", "simple"), index, product.toString(), now)
        }
        val categories = categoryOrder.map { (name, sortIndex) ->
            CategoryProjectionEntity(name, sortIndex, categoryCounts[name] ?: 0, now)
        }
        catalog.clearProducts(); catalog.clearCategories()
        if (products.isNotEmpty()) catalog.insertProducts(products)
        if (categories.isNotEmpty()) catalog.insertCategories(categories)
    }
}
