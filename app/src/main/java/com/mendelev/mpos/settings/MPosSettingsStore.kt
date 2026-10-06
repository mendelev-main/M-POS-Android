package com.mendelev.mpos.settings

import android.content.Context
import android.content.SharedPreferences
import com.mendelev.mpos.data.MPosStorageQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Ordered, disk-confirmed platform mirror; legacy settings remain authoritative. */
class MPosSettingsStore(
    private val preferences: SharedPreferences,
    scope: CoroutineScope,
    private val onResult: (JSONObject) -> Unit,
) {
    constructor(context: Context, scope: CoroutineScope, onResult: (JSONObject) -> Unit) :
        this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), scope, onResult)

    companion object {
        private const val PREFS_NAME = "mpos_native_settings"
        private const val SNAPSHOT_KEY = "platform_settings_snapshot"
        private const val SCHEMA_VERSION = 1
    }

    private data class Command(val action: String, val requestId: String, val settings: String?)
    private val queue = MPosStorageQueue(scope)

    fun handle(payload: JSONObject) {
        val command = Command(payload.optString("action"), payload.optString("requestId"),
            payload.optJSONObject("settings")?.toString())
        if (!queue.submit({ failure(command.requestId, "native settings operation failed") }) { dispatch(command) }) {
            failure(command.requestId, "native settings queue is full or closed")
        }
    }

    fun close() = queue.close()

    private suspend fun dispatch(command: Command) {
        currentCoroutineContext().ensureActive()
        val snapshot = when (command.action) {
            "replacePlatformSettings" -> {
                val settings = command.settings ?: throw IllegalArgumentException("settings payload is required")
                JSONObject().put("schemaVersion", SCHEMA_VERSION)
                    .put("updatedAt", System.currentTimeMillis()).put("settings", JSONObject(settings))
                    .also { check(preferences.edit().putString(SNAPSHOT_KEY, it.toString()).commit()) }
            }
            "getPlatformSettings" -> preferences.getString(SNAPSHOT_KEY, null)?.let(::JSONObject)
            "clearPlatformSettings" -> {
                check(preferences.edit().remove(SNAPSHOT_KEY).commit())
                null
            }
            else -> throw IllegalArgumentException("unknown settings action")
        }
        // A synchronous disk commit can finish after cancellation; suppress callbacks to a closed Activity.
        currentCoroutineContext().ensureActive()
        onResult(JSONObject().put("requestId", command.requestId).put("ok", true)
            .put("snapshot", snapshot ?: JSONObject.NULL).put("authoritative", false))
    }

    private fun failure(requestId: String, message: String) = onResult(
        JSONObject().put("requestId", requestId).put("ok", false).put("message", message).put("authoritative", false)
    )
}
