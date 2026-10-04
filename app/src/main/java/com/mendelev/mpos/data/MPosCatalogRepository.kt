package com.mendelev.mpos.data

import org.json.JSONArray
import org.json.JSONObject

class MPosCatalogRepository(
    private val database: MPosDatabase,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val catalogDao = database.catalogProjectionDao()

    suspend fun parityReport(): JSONObject {
        val legacy = shadowDao.get("products")
            ?: return JSONObject()
                .put("ok", false)
                .put("authoritative", false)
                .put("reason", "legacy products shadow is not available")

        val source = JSONArray(legacy.payload)
        val sourceProducts = linkedMapOf<String, JSONObject>()
        val sourceCategories = linkedSetOf<String>()

        for (index in 0 until source.length()) {
            val product = source.optJSONObject(index) ?: continue
            val id = product.optString("id").trim()
            if (id.isEmpty()) continue
            val category = normalizedCategory(product.optString("category"))
            sourceProducts[id] = product
            sourceCategories += category
        }

        val nativeProducts = catalogDao.allProducts()
        val nativeCategories = catalogDao.allCategories()
        val nativeById = nativeProducts.associateBy { it.id }

        val missingIds = sourceProducts.keys.filterNot(nativeById::containsKey)
        val extraIds = nativeById.keys.filterNot(sourceProducts::containsKey)
        val mismatchedIds = sourceProducts.mapNotNull { (id, sourceProduct) ->
            val projected = nativeById[id] ?: return@mapNotNull null
            val matches =
                projected.name == sourceProduct.optString("name") &&
                projected.category == normalizedCategory(sourceProduct.optString("category")) &&
                projected.type == sourceProduct.optString("type", "simple")
            if (matches) null else id
        }

        val nativeCategoryNames = nativeCategories.map { it.name }.toSet()
        val missingCategories = sourceCategories.filterNot(nativeCategoryNames::contains)
        val extraCategories = nativeCategoryNames.filterNot(sourceCategories::contains)

        val matches =
            missingIds.isEmpty() &&
            extraIds.isEmpty() &&
            mismatchedIds.isEmpty() &&
            missingCategories.isEmpty() &&
            extraCategories.isEmpty() &&
            sourceProducts.size == nativeProducts.size

        return JSONObject()
            .put("ok", true)
            .put("matches", matches)
            .put("authoritative", false)
            .put("legacyProductCount", sourceProducts.size)
            .put("nativeProductCount", nativeProducts.size)
            .put("legacyCategoryCount", sourceCategories.size)
            .put("nativeCategoryCount", nativeCategories.size)
            .put("missingProductIds", JSONArray(missingIds))
            .put("extraProductIds", JSONArray(extraIds))
            .put("mismatchedProductIds", JSONArray(mismatchedIds))
            .put("missingCategories", JSONArray(missingCategories.toList()))
            .put("extraCategories", JSONArray(extraCategories.toList()))
    }

    suspend fun snapshot(): JSONObject {
        val parity = parityReport()
        if (!parity.optBoolean("ok") || !parity.optBoolean("matches")) {
            return JSONObject()
                .put("ok", false)
                .put("authoritative", false)
                .put("reason", "catalog projection parity is not confirmed")
                .put("parity", parity)
        }

        val products = catalogDao.allProducts()
        val categories = catalogDao.allCategories()

        return JSONObject()
            .put("ok", true)
            .put("authoritative", false)
            .put("source", "room-projection")
            .put("products", JSONArray(products.map { JSONObject(it.payload) }))
            .put(
                "categories",
                JSONArray(
                    categories.map {
                        JSONObject()
                            .put("name", it.name)
                            .put("sortIndex", it.sortIndex)
                            .put("productCount", it.productCount)
                    }
                )
            )
    }

    private fun normalizedCategory(value: String): String =
        value.trim().ifEmpty { "Без категории" }
}
