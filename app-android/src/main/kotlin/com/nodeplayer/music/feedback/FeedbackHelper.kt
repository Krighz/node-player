package com.nodeplayer.music.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.nodeplayer.music.BuildConfig

object FeedbackHelper {
    const val DISCUSSIONS_URL = "https://github.com/Krighz/node-player/discussions"
    const val ISSUES_URL = "https://github.com/Krighz/node-player/issues"

    fun openFeedback(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DISCUSSIONS_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openBugReport(context: Context) {
        try {
            val body = buildString {
                append("\n\n---\n")
                append("### Device & System Info\n")
                append("- App Version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                append("- Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
                append("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                append("- Build: ${Build.DISPLAY}\n")
            }
            val encodedBody = Uri.encode(body)
            val url = "https://github.com/Krighz/node-player/issues/new?template=bug_report.md&body=$encodedBody"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {}
        }
    }
}
