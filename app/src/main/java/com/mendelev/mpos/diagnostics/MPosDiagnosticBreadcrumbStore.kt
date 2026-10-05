package com.mendelev.mpos.diagnostics

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class MPosDiagnosticBreadcrumbStore internal constructor(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) {
    companion object { private const val PREFS = "mpos_diagnostic_breadcrumbs"; private const val KEY = "entries"; private const val MAX_ENTRIES = 200 }
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    private constructor(prefs: SharedPreferences) : this(
        { prefs.getString(KEY, "[]") },
        { prefs.edit().putString(KEY, it).apply() },
    )

    @Synchronized fun record(category: String, event: String, ok: Boolean? = null) {
        // Diagnostics must never prevent delivery of a printer/storage/network result to POS.
        runCatching {
            val entries = readEntries()
            val next = MPosDiagnosticReport.sanitize(entries, MAX_ENTRIES - 1)
            next.put(MPosDiagnosticReport.entry(System.currentTimeMillis(), category, event, ok))
            write(next.toString())
        }
    }

    @Synchronized fun snapshot(): JSONArray = MPosDiagnosticReport.sanitize(readEntries(), MAX_ENTRIES)

    private fun readEntries(): JSONArray =
        runCatching { JSONArray(read() ?: "[]") }.getOrDefault(JSONArray())
}
