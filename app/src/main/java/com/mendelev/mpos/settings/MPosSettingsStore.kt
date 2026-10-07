package com.mendelev.mpos.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.mendelev.mpos.data.MPosStorageQueue
import com.mendelev.mpos.data.MPosSupplyParity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** FIFO, disk-confirmed platform settings with an explicit one-time authority cutover. */
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
        private const val AUTHORITY_KEY = "mpos_platform_settings_authority_v1"
    }

    private data class Command(val action: String, val requestId: String, val settings: String?, val expected: String?)
    private val queue = MPosStorageQueue(scope)

    fun handle(payload: JSONObject) {
        val command = Command(payload.optString("action"), payload.optString("requestId"),
            payload.optJSONObject("settings")?.toString(), payload.optJSONObject("expected")?.toString())
        if (!queue.submit({ failure(command.requestId, "native settings operation failed") }) { dispatch(command) }) {
            failure(command.requestId, "native settings queue is full or closed")
        }
    }

    fun close() = queue.close()

    private var uncertain = false
    private fun owned() = preferences.getString(AUTHORITY_KEY, null) == "1"
    private fun read() = preferences.getString(SNAPSHOT_KEY, null)?.let(::JSONObject)
    private fun validated(raw: String?): JSONObject {
        val value = JSONObject(requireNotNull(raw) { "settings payload is required" })
        require(value.optJSONArray("printers") != null && value.optJSONObject("posNotifications") != null)
        return value
    }
    @SuppressLint("UseKtx") // Disk boolean acknowledgement is required; KTX edit returns Unit.
    private fun write(value: JSONObject): JSONObject {
        val snapshot = JSONObject().put("schemaVersion", SCHEMA_VERSION)
            .put("updatedAt", System.currentTimeMillis()).put("settings", value)
        // SharedPreferences can update memory even when disk commit fails. No further owned
        // operation may treat that in-process value as a successful commit; require restart.
        val previous = preferences.getString(SNAPSHOT_KEY, null)
        val previousMarker = preferences.getString(AUTHORITY_KEY, null)
        val confirmed = runCatching {
            preferences.edit().putString(SNAPSHOT_KEY, snapshot.toString()).putString(AUTHORITY_KEY, "1").commit()
        }.getOrDefault(false)
        if (!confirmed) {
            uncertain = true
            // Revert the in-process preference cache too; failed commit() may already have changed it.
            // Even a successful repair does not authorize this command or clear the restart gate.
            runCatching { preferences.edit().putString(SNAPSHOT_KEY, previous).putString(AUTHORITY_KEY, previousMarker).commit() }
            error("native settings commit status is uncertain")
        }
        return snapshot
    }
    private suspend fun dispatch(command: Command) {
        currentCoroutineContext().ensureActive()
        var authoritative = false
        var ignored = false
        val snapshot = when (command.action) {
            "platformSettingsStatus" -> {
                check(!uncertain)
                authoritative = owned()
                if (authoritative) requireNotNull(read()) else null
            }
            "platformSettingsInitialize" -> {
                check(!uncertain)
                authoritative = true
                if (owned()) requireNotNull(read()) else write(validated(command.settings))
            }
            "platformSettingsRead" -> {
                check(!uncertain && owned())
                authoritative = true
                requireNotNull(read())
            }
            "platformSettingsWrite" -> {
                check(!uncertain && owned())
                val previous = requireNotNull(read()).getJSONObject("settings")
                require(MPosSupplyParity.same(previous, validated(command.expected))) { "native settings conflict" }
                val value = validated(command.settings)
                authoritative = true
                write(value)
            }
            "replacePlatformSettings" -> {
                if (owned() || uncertain) { ignored = true; null } else {
                    val settings = command.settings ?: throw IllegalArgumentException("settings payload is required")
                    JSONObject().put("schemaVersion", SCHEMA_VERSION)
                        .put("updatedAt", System.currentTimeMillis()).put("settings", JSONObject(settings))
                        .also { check(preferences.edit().putString(SNAPSHOT_KEY, it.toString()).commit()) }
                }
            }
            "getPlatformSettings" -> if (owned() || uncertain) { ignored = true; null } else read()
            "clearPlatformSettings" -> {
                if (owned() || uncertain) ignored = true else check(preferences.edit().remove(SNAPSHOT_KEY).commit())
                null
            }
            else -> throw IllegalArgumentException("unknown settings action")
        }
        currentCoroutineContext().ensureActive()
        onResult(JSONObject().put("requestId", command.requestId).put("ok", true)
            .put("snapshot", snapshot ?: JSONObject.NULL).put("authoritative", authoritative).put("ignored", ignored))
    }

    private fun failure(requestId: String, message: String) = onResult(
        JSONObject().put("requestId", requestId).put("ok", false)
            .put("message", if (uncertain) "native settings commit status is uncertain" else message)
            .put("uncertain", uncertain).put("authoritative", false)
    )
}
