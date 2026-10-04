package com.mendelev.mpos.settings

import android.content.Context
import org.json.JSONObject

class NativeSettingsStore(
    context: Context,
    private val onResult: (JSONObject) -> Unit,
) {
    companion object {
        private const val PREFS_NAME = "mpos_native_settings"
        private const val SNAPSHOT_KEY = "platform_settings_snapshot"
        private const val SCHEMA_VERSION = 1
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun handle(payload: JSONObject) {
        val requestId = payload.optString("requestId")
        runCatching {
            when (payload.optString("action")) {
                "replacePlatformSettings" -> {
                    val settings = payload.optJSONObject("settings")
                        ?: throw IllegalArgumentException("settings payload is required")
                    val snapshot = JSONObject()
                        .put("schemaVersion", SCHEMA_VERSION)
                        .put("updatedAt", System.currentTimeMillis())
                        .put("settings", settings)
                    preferences.edit().putString(SNAPSHOT_KEY, snapshot.toString()).apply()
                    result(requestId, true, snapshot)
                }
                "getPlatformSettings" -> {
                    val stored = preferences.getString(SNAPSHOT_KEY, null)
                    val snapshot = stored?.let(::JSONObject)
                    result(requestId, true, snapshot)
                }
                "clearPlatformSettings" -> {
                    preferences.edit().remove(SNAPSHOT_KEY).apply()
                    result(requestId, true, null)
                }
                else -> throw IllegalArgumentException("unknown settings action")
            }
        }.onFailure { error ->
            onResult(
                JSONObject()
                    .put("requestId", requestId)
                    .put("ok", false)
                    .put("message", error.localizedMessage ?: "native settings error")
            )
        }
    }

    private fun result(requestId: String, ok: Boolean, snapshot: JSONObject?) {
        onResult(
            JSONObject()
                .put("requestId", requestId)
                .put("ok", ok)
                .put("snapshot", snapshot ?: JSONObject.NULL)
        )
    }
}
