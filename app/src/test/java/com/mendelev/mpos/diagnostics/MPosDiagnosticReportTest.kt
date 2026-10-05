package com.mendelev.mpos.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MPosDiagnosticReportTest {
    @Test fun reportDropsExtraFieldsAndUntrustedEventText() {
        val oldRecord = JSONObject().put("at", 42).put("category", "network")
            .put("event", "https://backend.invalid/?deviceKey=secret").put("ok", false)
            .put("customer", "private customer").put("deviceKey", "secret")
        val report = MPosDiagnosticReport.build(JSONArray().put(oldRecord), "0.1.0", 1, 35, 123)
        val entry = report.getJSONArray("entries").getJSONObject(0)
        assertEquals(setOf("at", "category", "event", "ok"), entry.keys().asSequence().toSet())
        assertEquals("network", entry.getString("category"))
        assertEquals("unknown", entry.getString("event"))
        assertFalse(entry.getBoolean("ok"))
        assertFalse(report.toString().contains("secret"))
        assertFalse(report.toString().contains("private customer"))
    }

    @Test fun unknownCategoryCannotExportArbitraryText() {
        val entry = MPosDiagnosticReport.entry(10, "customer phone", "cash payment details", null)
        assertEquals("unknown", entry.getString("category"))
        assertEquals("unknown", entry.getString("event"))
        assertFalse(entry.has("ok"))
    }

    @Test fun retainsNewestTwoHundredEntriesInChronologicalOrder() {
        val entries = JSONArray()
        for (at in 0 until 250) entries.put(MPosDiagnosticReport.entry(at.toLong(), "storage", "result", true))
        val result = MPosDiagnosticReport.build(entries, "0.1.0", 1, 28, 500).getJSONArray("entries")
        assertEquals(200, result.length())
        assertEquals(50L, result.getJSONObject(0).getLong("at"))
        assertEquals(249L, result.getJSONObject(199).getLong("at"))
        assertEquals(250, entries.length())
    }

    @Test fun malformedRecordsDoNotDisplaceValidRecords() {
        val entries = JSONArray().put(MPosDiagnosticReport.entry(1, "printer", "network_error", false))
            .put("broken").put(JSONObject().put("at", -1))
            .put(JSONObject().put("at", "not a timestamp"))
        val result = MPosDiagnosticReport.sanitize(entries, 200)
        assertEquals(1, result.length())
        assertEquals("network_error", result.getJSONObject(0).getString("event"))
        assertFalse(result.getJSONObject(0).getBoolean("ok"))
    }

    @Test fun reportIsDetachedAndContainsOnlyDeclaredMetadata() {
        val entries = JSONArray().put(MPosDiagnosticReport.entry(1, "lifecycle", "renderer-exit", null))
        val result = MPosDiagnosticReport.build(entries, "0.1.0", 1, 35, 500)
        entries.getJSONObject(0).put("event", "changed")
        assertEquals(setOf("schemaVersion", "generatedAt", "app", "android", "entries"), result.keys().asSequence().toSet())
        assertEquals(1, result.getInt("schemaVersion"))
        assertEquals(500L, result.getLong("generatedAt"))
        assertEquals(35, result.getJSONObject("android").getInt("sdkInt"))
        assertEquals("renderer-exit", result.getJSONArray("entries").getJSONObject(0).getString("event"))
    }
}
