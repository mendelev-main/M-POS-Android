package com.mendelev.mpos.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Owns compatible current-session and recovery-journal documents. */
class MPosRecoveryStorage(private val database: MPosDatabase) {
    private val documents = database.legacyStorageShadowDao()
    private val currentOrderSessionDao = database.currentOrderSessionProjectionDao()
    private val criticalJournalDao = database.criticalStorageJournalProjectionDao()
    companion object {
        val KEYS = setOf("currentOrderSession", "criticalStorageJournal")
        internal fun authorityKey(key: String): String {
            require(key in KEYS) { "unsupported recovery key" }
            return "mpos_recovery_authority_v1:$key"
        }
    }

    private fun marker(key: String): String {
        return authorityKey(key)
    }

    suspend fun isAuthoritative(key: String): Boolean = documents.get(marker(key)) != null

    private fun acknowledgement(key: String) = JSONObject().put("ok", true).put("authoritative", true)
        .put("source", "room-recovery").put("key", key)

    suspend fun initialize(key: String, legacy: String?): JSONObject = database.withTransaction {
        if (!isAuthoritative(key)) {
            replace(key, legacy)
            documents.upsert(LegacyStorageShadowEntity(marker(key), "{\"version\":1}", System.currentTimeMillis()))
        }
        acknowledgement(key)
    }

    suspend fun read(key: String): JSONObject = database.withTransaction {
        check(isAuthoritative(key)) { "native recovery is not initialized" }
        val document = documents.get(key)
        acknowledgement(key).put("found", document != null).put("payload", document?.payload ?: JSONObject.NULL)
    }

    suspend fun write(key: String, serialized: String): JSONObject = database.withTransaction {
        check(isAuthoritative(key)) { "native recovery is not initialized" }
        replace(key, serialized)
        acknowledgement(key)
    }

    suspend fun remove(key: String): JSONObject = database.withTransaction {
        check(isAuthoritative(key)) { "native recovery is not initialized" }
        replace(key, null)
        acknowledgement(key)
    }

    private suspend fun replace(key: String, serialized: String?) {
        val parsed = serialized?.let { raw ->
            val parser = JSONTokener(raw)
            parser.nextValue().also { require(parser.nextClean() == '\u0000') { "invalid recovery JSON" } }
        }
        require(parsed == null || parsed === JSONObject.NULL || parsed is JSONObject) { "recovery must be an object or null" }
        if (serialized == null) documents.delete(key)
        else documents.upsert(LegacyStorageShadowEntity(key, serialized, System.currentTimeMillis()))
        if (parsed == null || parsed === JSONObject.NULL) {
            if (key == "currentOrderSession") currentOrderSessionDao.clear() else criticalJournalDao.clear()
        } else project(key, requireNotNull(serialized))
    }
    suspend fun project(key: String, serialized: String) {
        require(key in KEYS)
        if (key == "currentOrderSession") {
            if (serialized == "null") currentOrderSessionDao.clear() else projectCurrentOrderSession(serialized)
        } else projectCriticalStorageJournal(serialized)
    }

    private suspend fun projectCurrentOrderSession(serialized: String) {
        val source = JSONObject(serialized)
        val items = source.optJSONArray("items")
        currentOrderSessionDao.upsert(
            CurrentOrderSessionProjectionEntity(
                itemCount = items?.length() ?: 0,
                orderType = source.optString("orderType"),
                source = source.optString("source"),
                webOrderId = source.optString("webOrderId"),
                webOrderStatus = source.optString("webOrderStatus"),
                updatedAt = source.optLong("updatedAt"),
                payload = source.toString(),
            ),
        )
    }

    private suspend fun projectCriticalStorageJournal(serialized: String) {
        val journal = if (serialized == "null") null else JSONObject(serialized)
        if (journal == null) {
            criticalJournalDao.clear()
            return
        }
        val writes = journal.optJSONArray("writes") ?: JSONArray()
        val keys = mutableListOf<String>()
        for (index in 0 until writes.length()) {
            val key = writes.optJSONObject(index)?.optString("key").orEmpty()
            if (key.isNotBlank()) keys += key
        }
        criticalJournalDao.replace(
            CriticalStorageJournalProjectionEntity(
                journalId = journal.optString("id"),
                operationType = journal.optString("type"),
                createdAt = journal.optLong("createdAt"),
                writeKeys = JSONArray(keys).toString(),
                payload = journal.toString(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

}
