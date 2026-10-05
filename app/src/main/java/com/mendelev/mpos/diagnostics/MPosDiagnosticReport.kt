package com.mendelev.mpos.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/** Rebuilds diagnostic data from an allowlist, including records saved by older versions. */
object MPosDiagnosticReport {
    private val events = mapOf(
        "printer" to setOf("network_ready", "network_printed", "network_error"),
        "storage" to setOf("result"),
        "network" to setOf("result", "connected", "reconnecting", "shadow-observed", "background-paused", "foreground-resumed"),
        "lifecycle" to setOf("created", "foreground", "background", "renderer-exit"),
    )

    fun entry(at: Long, category: String, event: String, ok: Boolean?): JSONObject {
        val safeCategory = category.takeIf(events::containsKey) ?: "unknown"
        val safeEvent = event.takeIf { events[safeCategory]?.contains(it) == true } ?: "unknown"
        return JSONObject().put("at", at).put("category", safeCategory).put("event", safeEvent)
            .apply { ok?.let { put("ok", it) } }
    }

    fun sanitize(entries: JSONArray, limit: Int): JSONArray {
        val safe = JSONArray()
        // Scan from newest to oldest so malformed entries do not displace valid records.
        for (index in entries.length() - 1 downTo 0) {
            if (safe.length() >= limit) break
            val item = entries.optJSONObject(index) ?: continue
            val at = item.opt("at") as? Number ?: continue
            if (at.toLong() < 0) continue
            safe.put(entry(at.toLong(), item.optString("category"), item.optString("event"), item.opt("ok") as? Boolean))
        }
        return JSONArray().apply {
            for (index in safe.length() - 1 downTo 0) put(safe.getJSONObject(index))
        }
    }

    fun build(entries: JSONArray, versionName: String, versionCode: Int, sdkInt: Int, generatedAt: Long): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAt", generatedAt)
            .put("app", JSONObject().put("versionName", versionName).put("versionCode", versionCode))
            .put("android", JSONObject().put("sdkInt", sdkInt))
            .put("entries", sanitize(entries, 200))
}
