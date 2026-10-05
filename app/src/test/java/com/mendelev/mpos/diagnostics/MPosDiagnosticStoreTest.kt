package com.mendelev.mpos.diagnostics

import org.junit.Assert.*
import org.junit.Test

class MPosDiagnosticStoreTest {
    @Test fun failedDiagnosticWriteDoesNotThrowIntoPosCallback() {
        val store = MPosDiagnosticBreadcrumbStore({ "[]" }, { error("disk full") })
        store.record("printer", "network_error", false)
        // Execution must continue so the caller can deliver its original POS result.
        assertEquals(0, store.snapshot().length())
    }

    @Test fun unreadableDiagnosticsDoNotPreventRecordingOrExport() {
        var persisted = ""
        val store = MPosDiagnosticBreadcrumbStore({ error("unavailable preferences") }, { persisted = it })
        store.record("storage", "result", false)
        assertTrue(persisted.contains("result"))
        assertEquals(0, store.snapshot().length())
    }

    @Test fun corruptPreferencesRecoverAndSurviveStoreRecreation() {
        var persisted = "invalid json"
        val store = MPosDiagnosticBreadcrumbStore({ persisted }, { persisted = it })
        assertEquals(0, store.snapshot().length())
        store.record("network", "result", false)
        val restored = MPosDiagnosticBreadcrumbStore({ persisted }, { persisted = it })
        val snapshot = restored.snapshot()
        assertEquals(1, snapshot.length())
        assertEquals("network", snapshot.getJSONObject(0).getString("category"))
        assertFalse(snapshot.getJSONObject(0).getBoolean("ok"))
    }

    @Test fun recordingMoreThanTheLimitRetainsOnlyLatestEvents() {
        var persisted = "[]"
        val store = MPosDiagnosticBreadcrumbStore({ persisted }, { persisted = it })
        repeat(205) { store.record("printer", "network_printed", true) }
        store.record("printer", "network_error", false)
        val snapshot = store.snapshot()
        assertEquals(200, snapshot.length())
        assertEquals("network_error", snapshot.getJSONObject(199).getString("event"))
        assertFalse(snapshot.getJSONObject(199).getBoolean("ok"))
    }
}
