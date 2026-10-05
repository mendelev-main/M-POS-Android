package com.mendelev.mpos.diagnostics

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import com.mendelev.mpos.BuildConfig

class MPosDiagnosticExporter(
    private val resolver: ContentResolver,
    private val store: MPosDiagnosticBreadcrumbStore,
) {
    /** Runs on IO after the user selects a destination; never reads POS storage or network settings. */
    fun write(uri: Uri): Boolean = runCatching {
        val report = MPosDiagnosticReport.build(
            store.snapshot(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            Build.VERSION.SDK_INT, System.currentTimeMillis(),
        ).toString(2).toByteArray(Charsets.UTF_8)
        val output = resolver.openOutputStream(uri, "wt") ?: error("Diagnostic destination unavailable")
        output.use { it.write(report) }
    }.isSuccess
}
