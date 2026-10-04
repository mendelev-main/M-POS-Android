package com.mendelev.mpos.data

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NativeStorageMirror(
    private val database: MPosDatabase,
    private val scope: LifecycleCoroutineScope,
    private val onResult: (JSONObject) -> Unit,
) {
    private val shadowDao = database.legacyStorageShadowDao()
    private val catalogDao = database.catalogProjectionDao()
    private val catalogRepository = MPosCatalogRepository(database)

    fun handle(payload: JSONObject) {
        val requestId = payload.optString("requestId")
        when (payload.optString("action")) {
            "put" -> {
                val key = payload.optString("key")
                val serialized = payload.optString("payload", null)
                if (key.isBlank() || serialized == null) {
                    result(requestId, false, "invalid shadow storage payload")
                    return
                }
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        shadowDao.upsert(
                            LegacyStorageShadowEntity(
                                key = key,
                                payload = serialized,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                        val projectionOk = if (key == "products") {
                            runCatching { projectCatalog(serialized) }.isSuccess
                        } else {
                            true
                        }
                        result(requestId, true, projectionOk = projectionOk)
                    }.onFailure {
                        result(requestId, false, it.localizedMessage ?: "shadow write failed")
                    }
                }
            }

            "remove" -> {
                val key = payload.optString("key")
                if (key.isBlank()) {
                    result(requestId, false, "invalid shadow storage key")
                    return
                }
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        shadowDao.delete(key)
                        if (key == "products") {
                            database.withTransaction {
                                catalogDao.clearProducts()
                                catalogDao.clearCategories()
                            }
                        }
                    }.onSuccess { result(requestId, true) }
                        .onFailure { result(requestId, false, it.localizedMessage ?: "shadow delete failed") }
                }
            }

            "catalogParity" -> scope.launch(Dispatchers.IO) {
                runCatching { catalogRepository.parityReport() }
                    .onSuccess { report ->
                        report.put("requestId", requestId)
                        onResult(report)
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "catalog parity failed") }
            }

            "stats" -> scope.launch(Dispatchers.IO) {
                runCatching {
                    Triple(
                        shadowDao.count(),
                        catalogDao.productCount(),
                        catalogDao.categoryCount(),
                    )
                }.onSuccess { (shadowCount, productCount, categoryCount) ->
                    onResult(
                        JSONObject()
                            .put("requestId", requestId)
                            .put("ok", true)
                            .put("count", shadowCount)
                            .put("catalogProducts", productCount)
                            .put("catalogCategories", categoryCount)
                            .put("authoritative", false)
                    )
                }.onFailure {
                    result(requestId, false, it.localizedMessage ?: "shadow stats failed")
                }
            }

            else -> result(requestId, false, "unknown storage action")
        }
    }

    private suspend fun projectCatalog(serialized: String) {
        val source = JSONArray(serialized)
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

            products += ProductProjectionEntity(
                id = id,
                name = product.optString("name"),
                category = category,
                type = product.optString("type", "simple"),
                sortIndex = index,
                payload = product.toString(),
                updatedAt = now,
            )
        }

        val categories = categoryOrder.map { (name, sortIndex) ->
            CategoryProjectionEntity(
                name = name,
                sortIndex = sortIndex,
                productCount = categoryCounts[name] ?: 0,
                updatedAt = now,
            )
        }

        database.withTransaction {
            catalogDao.clearProducts()
            catalogDao.clearCategories()
            if (products.isNotEmpty()) catalogDao.insertProducts(products)
            if (categories.isNotEmpty()) catalogDao.insertCategories(categories)
        }
    }

    private fun result(
        requestId: String,
        ok: Boolean,
        message: String? = null,
        projectionOk: Boolean? = null,
    ) {
        val result = JSONObject()
            .put("requestId", requestId)
            .put("ok", ok)
            .put("authoritative", false)
        if (message != null) result.put("message", message)
        if (projectionOk != null) result.put("projectionOk", projectionOk)
        onResult(result)
    }
}
