package com.mendelev.mpos.data

import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class NativeStorageMirror(
    private val dao: LegacyStorageShadowDao,
    private val scope: LifecycleCoroutineScope,
    private val onResult: (JSONObject) -> Unit,
) {
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
                        dao.upsert(
                            LegacyStorageShadowEntity(
                                key = key,
                                payload = serialized,
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }.onSuccess { result(requestId, true) }
                        .onFailure { result(requestId, false, it.localizedMessage ?: "shadow write failed") }
                }
            }
            "remove" -> {
                val key = payload.optString("key")
                if (key.isBlank()) {
                    result(requestId, false, "invalid shadow storage key")
                    return
                }
                scope.launch(Dispatchers.IO) {
                    runCatching { dao.delete(key) }
                        .onSuccess { result(requestId, true) }
                        .onFailure { result(requestId, false, it.localizedMessage ?: "shadow delete failed") }
                }
            }
            "stats" -> scope.launch(Dispatchers.IO) {
                runCatching { dao.count() }
                    .onSuccess { count ->
                        onResult(
                            JSONObject()
                                .put("requestId", requestId)
                                .put("ok", true)
                                .put("count", count)
                                .put("authoritative", false)
                        )
                    }
                    .onFailure { result(requestId, false, it.localizedMessage ?: "shadow stats failed") }
            }
            else -> result(requestId, false, "unknown storage action")
        }
    }

    private fun result(requestId: String, ok: Boolean, message: String? = null) {
        val result = JSONObject()
            .put("requestId", requestId)
            .put("ok", ok)
            .put("authoritative", false)
        if (message != null) result.put("message", message)
        onResult(result)
    }
}
