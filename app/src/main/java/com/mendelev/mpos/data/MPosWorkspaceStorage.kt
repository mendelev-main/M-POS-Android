package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONObject
import org.json.JSONTokener

/** Native persistence for category layout, navigation and company metadata; presentation rules remain unchanged. */
class MPosWorkspaceStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    companion object { val KEYS = setOf("layout", "posNavigation", "company", "network", "telegram") }

    private fun marker(key: String): String {
        require(key in KEYS) { "unsupported workspace key" }
        return "mpos_workspace_authority_v1:$key"
    }

    suspend fun isAuthoritative(key: String): Boolean = documents.get(marker(key)) != null

    private fun acknowledgement(key: String) = JSONObject().put("ok", true).put("authoritative", true)
        .put("source", "room-workspace").put("key", key)

    suspend fun initialize(key: String, legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative(key)) {
            replace(key, legacy)
            documents.upsert(LegacyStorageShadowEntity(marker(key), "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement(key)
    }

    suspend fun read(key: String): JSONObject = database.withTransaction {
        check(isAuthoritative(key)) { "native workspace is not initialized" }
        val document = documents.get(key)
        acknowledgement(key).put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)
    }

    suspend fun write(key: String, serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative(key)) { "native workspace is not initialized" }
        replace(key, serialized)
        acknowledgement(key)
    }

    suspend fun remove(key: String): JSONObject = database.withTransaction {
        check(isAuthoritative(key)) { "native workspace is not initialized" }
        replace(key, null)
        acknowledgement(key)
    }

    private suspend fun replace(key: String, serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid workspace JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONObject) { "workspace must be an object or null" }
        if (serialized == null) documents.delete(key)
        else documents.upsert(LegacyStorageShadowEntity(key, serialized, System.currentTimeMillis()))
    }
}
