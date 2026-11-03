package com.nodeplayer.music.diagnostics

import android.app.Activity
import android.content.Context

/**
 * Public feature contract for diagnostics, shader self-tests, and bug reporting dumps.
 */
interface DiagnosticsFeatureApi {
    suspend fun generateDiagnosticsDump(
        context: Context,
        activity: Activity?,
        includeScreenshot: Boolean = true
    ): DumpContentSummary?

    fun sanitizeText(input: String): String
}
