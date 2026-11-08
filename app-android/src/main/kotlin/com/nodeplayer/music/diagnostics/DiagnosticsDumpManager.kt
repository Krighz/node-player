package com.nodeplayer.music.diagnostics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.PixelCopy
import androidx.core.content.FileProvider
import com.nodeplayer.common.logging.NodeLog
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DumpContentSummary(
    val zipFile: File,
    val screenshot: Bitmap?,
    val fileNames: List<String>,
    val deviceInfoText: String
)

object DiagnosticsDumpManager : DiagnosticsFeatureApi {

    private const val TAG = "DiagnosticsDumpManager"
    private const val AUTO_DUMP_FILENAME = "auto_graphics_dump.zip"

    // Background recording state
    private var recordJob: Job? = null
    var isRecording: Boolean = false
        private set

    override suspend fun generateDiagnosticsDump(
        context: Context,
        activity: Activity?,
        includeScreenshot: Boolean
    ): DumpContentSummary? = withContext(Dispatchers.IO) {
        if (activity != null) {
            createDiagnosticsZip(activity, "NORMAL")
        } else null
    }

    /**
     * Sanitizes plain text by removing file paths, audio track names, storage URIs, and user tokens.
     */
    override fun sanitizeText(input: String): String {
        var clean = input
        // Scrub absolute Android storage paths
        clean = clean.replace(Regex("""/storage/[^\s"',]+"""), "[REDACTED_PATH]")
        clean = clean.replace(Regex("""/sdcard/[^\s"',]+"""), "[REDACTED_PATH]")
        clean = clean.replace(Regex("""content://[^\s"',]+"""), "[REDACTED_URI]")
        clean = clean.replace(Regex("""file://[^\s"',]+"""), "[REDACTED_URI]")

        // Scrub music track filenames and formats
        clean = clean.replace(Regex("""[^\s"',/]+\.(mp3|flac|wav|m4a|aac|ogg|opus)""", RegexOption.IGNORE_CASE), "[REDACTED_AUDIO_FILE]")

        // Scrub potential credentials or auth keys
        clean = clean.replace(Regex("""(session[_-]?key|token|password|secret)\s*[:=]\s*[^\s,;]+""", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
        clean = clean.replace(Regex("""lastfm_username\s*[:=]\s*[^\s,;]+""", RegexOption.IGNORE_CASE), "lastfm_username=[REDACTED]")

        return clean
    }

    /**
     * Captures a screenshot of the app's own window using PixelCopy,
     * falling back to view drawing if PixelCopy is unavailable or fails.
     */
    fun captureWindowScreenshot(activity: Activity): Bitmap {
        val window = activity.window
        val view = window.decorView
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val latch = CountDownLatch(1)
            var copyResult = PixelCopy.ERROR_UNKNOWN
            val handlerThread = HandlerThread("PixelCopyThread").apply { start() }
            val handler = Handler(handlerThread.looper)

            try {
                PixelCopy.request(
                    window,
                    Rect(0, 0, width, height),
                    bitmap,
                    { result ->
                        copyResult = result
                        latch.countDown()
                    },
                    handler
                )
                val success = latch.await(1000, TimeUnit.MILLISECONDS)
                if (success && copyResult == PixelCopy.SUCCESS) {
                    return bitmap
                }
                Log.w(TAG, "PixelCopy returned $copyResult (success=$success), falling back to view drawing")
            } catch (e: Exception) {
                Log.w(TAG, "PixelCopy request failed, falling back to view drawing", e)
            } finally {
                handlerThread.quitSafely()
            }
        }

        // Fallback: draw view hierarchy on main thread
        try {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "View draw fallback failed", e)
        }
        return bitmap
    }

    /**
     * Reads process-specific logcat output and scrubs personal data.
     */
    fun getSanitizedLogcat(): String {
        return try {
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "--pid=$pid"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()

            var line: String? = reader.readLine()
            while (line != null) {
                lines.add(line)
                if (lines.size > 500) {
                    lines.removeAt(0)
                }
                line = reader.readLine()
            }
            reader.close()

            val rawLog = lines.joinToString("\n")
            sanitizeText(rawLog)
        } catch (e: Exception) {
            "Unable to read logcat: ${e.message}"
        }
    }

    /**
     * Builds the diagnostics dump ZIP package.
     */
    fun createDiagnosticsZip(
        activity: Activity,
        currentShaderMode: String,
        timelineCsv: String? = null
    ): DumpContentSummary {
        val context = activity.applicationContext
        val outputDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val zipFile = File(outputDir, "nodeplayer-diagnostics-$dateStr.zip")

        val deviceInfo = GpuInspector.getDeviceInfo(context)
        val selfTestResult = GlassShaderSelfTest.getLastResult(context)
        val plainTextDeviceInfo = deviceInfo.toSanitizedPlainText(selfTestResult, currentShaderMode)

        val screenshot = captureWindowScreenshot(activity)
        val logcatContent = getSanitizedLogcat()
        val eventsLog = GraphicsEventTracker.getRecentEvents().joinToString("\n")

        val includedFiles = mutableListOf<String>()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. Device Info
            zos.putNextEntry(ZipEntry("device_info.txt"))
            zos.write(plainTextDeviceInfo.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            includedFiles.add("device_info.txt")

            // 2. Screenshot
            val baos = ByteArrayOutputStream()
            screenshot.compress(Bitmap.CompressFormat.PNG, 95, baos)
            zos.putNextEntry(ZipEntry("screenshot.png"))
            zos.write(baos.toByteArray())
            zos.closeEntry()
            includedFiles.add("screenshot.png")

            // 3. Graphics Event Log
            zos.putNextEntry(ZipEntry("graphics_events.log"))
            zos.write(eventsLog.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            includedFiles.add("graphics_events.log")

            // 4. Logcat
            zos.putNextEntry(ZipEntry("logcat.txt"))
            zos.write(logcatContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            includedFiles.add("logcat.txt")

            // 4b. NodeLog grouped by feature
            val nodeLogGrouped = NodeLog.getEntriesGroupedByFeature().entries.joinToString("\n\n") { (feature, entries) ->
                "=== FEATURE: $feature (${entries.size} entries) ===\n" +
                        entries.joinToString("\n") { "[${it.level}] ${it.tag}: ${it.message}" }
            }
            zos.putNextEntry(ZipEntry("node_log_by_feature.txt"))
            zos.write(sanitizeText(nodeLogGrouped).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            includedFiles.add("node_log_by_feature.txt")

            // 5. Timeline recording if present
            if (timelineCsv != null) {
                zos.putNextEntry(ZipEntry("recording_timeline.csv"))
                zos.write(timelineCsv.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                includedFiles.add("recording_timeline.csv")
            }
        }

        GraphicsEventTracker.logEvent("DUMP_CREATED", "Diagnostics ZIP built: ${zipFile.name} (${zipFile.length() / 1024} KB)")

        return DumpContentSummary(
            zipFile = zipFile,
            screenshot = screenshot,
            fileNames = includedFiles,
            deviceInfoText = plainTextDeviceInfo
        )
    }

    /**
     * Starts 60-second record mode, sampling metrics once per second.
     */
    fun startRecording(
        activity: Activity,
        currentScreenProvider: () -> String,
        currentShaderMode: String,
        onTick: (secondsRemaining: Int) -> Unit,
        onComplete: (DumpContentSummary) -> Unit
    ) {
        if (isRecording) return
        isRecording = true

        val timelineBuilder = StringBuilder()
        timelineBuilder.appendLine("timestamp_ms,elapsed_sec,screen,shader_mode,dock_width,dock_height,used_heap_mb,max_heap_mb")

        val runtime = Runtime.getRuntime()

        recordJob = CoroutineScope(Dispatchers.Main).launch {
            for (sec in 60 downTo 1) {
                if (!isActive) break
                onTick(sec)

                val elapsed = 60 - sec
                val now = System.currentTimeMillis()
                val usedHeap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxHeap = runtime.maxMemory() / (1024 * 1024)
                val screen = currentScreenProvider()

                timelineBuilder.appendLine("$now,$elapsed,$screen,$currentShaderMode,${GlassShaderSelfTest.DOCK_WIDTH},${GlassShaderSelfTest.DOCK_HEIGHT},$usedHeap,$maxHeap")

                delay(1000)
            }

            isRecording = false
            onTick(0)

            val summary = withContext(Dispatchers.IO) {
                createDiagnosticsZip(activity, currentShaderMode, timelineBuilder.toString())
            }
            onComplete(summary)
        }
    }

    fun cancelRecording() {
        recordJob?.cancel()
        recordJob = null
        isRecording = false
    }

    /**
     * Prepares an Android share intent for the generated diagnostics ZIP.
     */
    fun createShareIntent(context: Context, zipFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Node Player Graphics Diagnostics Dump")
            putExtra(Intent.EXTRA_TEXT, "Node Player graphics diagnostics report attached.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Saves an automatic diagnostics dump when an unhandled error or self-test failure happens.
     */
    fun saveAutoDump(context: Context, reason: String) {
        try {
            val outputDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            val autoFile = File(outputDir, AUTO_DUMP_FILENAME)

            val deviceInfo = GpuInspector.getDeviceInfo(context)
            val selfTestResult = GlassShaderSelfTest.getLastResult(context)
            val text = "AUTO-GENERATED REPORT ON FAILURE\nReason: $reason\n\n" +
                    deviceInfo.toSanitizedPlainText(selfTestResult, "AUTO")

            ZipOutputStream(FileOutputStream(autoFile)).use { zos ->
                zos.putNextEntry(ZipEntry("device_info.txt"))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("graphics_events.log"))
                val events = GraphicsEventTracker.getRecentEvents().joinToString("\n")
                zos.write(events.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            Log.i(TAG, "Saved automatic failure dump to ${autoFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving auto dump", e)
        }
    }

    fun hasPendingAutoReport(context: Context): Boolean {
        val file = File(File(context.filesDir, "diagnostics"), AUTO_DUMP_FILENAME)
        return file.exists() && file.length() > 0
    }

    fun getPendingAutoReportFile(context: Context): File? {
        val file = File(File(context.filesDir, "diagnostics"), AUTO_DUMP_FILENAME)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun clearAutoReport(context: Context) {
        val file = File(File(context.filesDir, "diagnostics"), AUTO_DUMP_FILENAME)
        if (file.exists()) {
            file.delete()
        }
    }
}
