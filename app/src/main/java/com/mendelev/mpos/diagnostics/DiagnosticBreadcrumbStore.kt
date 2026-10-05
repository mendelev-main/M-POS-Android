package com.mendelev.mpos.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DiagnosticBreadcrumbStore(context: Context) {
    companion object { private const val PREFS = "mpos_diagnostic_breadcrumbs"; private const val KEY = "entries"; private const val MAX_ENTRIES = 200 }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized fun record(category: String, event: String, ok: Boolean? = null) {
        val entries = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        val start = (entries.length() - (MAX_ENTRIES - 1)).coerceAtLeast(0)
        for (index in start until entries.length()) entries.optJSONObject(index)?.let(next::put)
        next.put(JSONObject().put("at", System.currentTimeMillis()).put("category", category.take(32)).put("event", event.take(64)).apply { ok?.let { put("ok", it) } })
        prefs.edit().putString(KEY, next.toString()).apply()
    }
}
