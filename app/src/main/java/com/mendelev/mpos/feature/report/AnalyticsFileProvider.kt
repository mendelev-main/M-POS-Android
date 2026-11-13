package com.mendelev.mpos.feature.report

/** Separate provider identity prevents Android from aliasing the APK updater provider instance. */
class AnalyticsFileProvider : androidx.core.content.FileProvider()
