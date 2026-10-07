package com.mendelev.mpos.data

import androidx.room.withTransaction

/** Read-only bootstrap boundary for native controllers; never initializes legacy authority. */
class MPosRuntimeSnapshot(private val database: MPosDatabase) {
    data class Document(val payload: String, val updatedAt: Long)

    /** Missing keys remain absent. Raw JSON (including unknown backup fields) is preserved. */
    suspend fun read(keys: Set<String>): Map<String, Document> = database.withTransaction {
        require(keys.all { it.isNotBlank() }) { "document keys must not be blank" }
        val documents = database.legacyStorageShadowDao()
        buildMap {
            for (key in keys) {
                documents.get(key)?.let { put(key, Document(it.payload, it.updatedAt)) }
            }
        }
    }
}
