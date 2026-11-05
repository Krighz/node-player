package com.nodeplayer.network.discovery

import com.nodeplayer.common.logging.NodeLog
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages deterministic USB Debugging links over ADB.
 * Allows zero-Wi-Fi, zero-network Node Connect communication via direct USB loopback forwarding.
 */
object AdbBridge {

    const val DEFAULT_FORWARD_PORT = 54825 // PC connects to 127.0.0.1:54825 -> Phone 54820
    const val DEFAULT_REVERSE_PORT = 54826 // Phone connects to 127.0.0.1:54826 -> PC 54820
    const val DEFAULT_NODE_PORT = 54820

    fun findAdbPath(): String? {
        // 1. Check PATH
        try {
            val isWindows = System.getProperty("os.name", "").lowercase().contains("win")
            val checkCmd = if (isWindows) listOf("where", "adb") else listOf("which", "adb")
            val proc = ProcessBuilder(checkCmd).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor(2, TimeUnit.SECONDS) && proc.exitValue() == 0 && output.isNotBlank()) {
                val firstLine = output.lines().firstOrNull()?.trim()
                if (!firstLine.isNullOrBlank() && File(firstLine).exists()) {
                    return firstLine
                }
            }
        } catch (_: Exception) {}

        // 2. Check ANDROID_HOME / ANDROID_SDK_ROOT
        val envRoots = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" },
            System.getProperty("user.home")?.let { "$it/AppData/Local/Android/Sdk" },
            System.getProperty("user.home")?.let { "$it/Library/Android/sdk" }
        )

        for (root in envRoots) {
            val candidates = listOf(
                File(root, "platform-tools/adb.exe"),
                File(root, "platform-tools/adb")
            )
            for (candidate in candidates) {
                if (candidate.exists() && candidate.canExecute()) {
                    return candidate.absolutePath
                }
            }
        }

        return "adb" // Fallback to raw command
    }

    fun isAdbAvailable(): Boolean {
        return try {
            val adb = findAdbPath() ?: return false
            val proc = ProcessBuilder(adb, "version").redirectErrorStream(true).start()
            proc.waitFor(2, TimeUnit.SECONDS) && proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun setupUsbLink(
        forwardPort: Int = DEFAULT_FORWARD_PORT,
        reversePort: Int = DEFAULT_REVERSE_PORT,
        targetPort: Int = DEFAULT_NODE_PORT
    ): Result<Pair<Int, Int>> {
        val adb = findAdbPath() ?: return Result.failure(IllegalStateException("ADB executable not found"))

        return try {
            NodeLog.i("nodeconnect/discovery", "Setting up ADB USB link: forward $forwardPort->$targetPort, reverse $reversePort->$targetPort")

            // 1. adb forward tcp:<forwardPort> tcp:<targetPort>
            val forwardProc = ProcessBuilder(adb, "forward", "tcp:$forwardPort", "tcp:$targetPort")
                .redirectErrorStream(true)
                .start()
            val forwardOut = forwardProc.inputStream.bufferedReader().readText().trim()
            forwardProc.waitFor(5, TimeUnit.SECONDS)

            if (forwardProc.exitValue() != 0) {
                NodeLog.w("nodeconnect/discovery", "adb forward failed: $forwardOut")
                return Result.failure(IllegalStateException("adb forward failed: $forwardOut"))
            }

            // 2. adb reverse tcp:<reversePort> tcp:<targetPort>
            val reverseProc = ProcessBuilder(adb, "reverse", "tcp:$reversePort", "tcp:$targetPort")
                .redirectErrorStream(true)
                .start()
            val reverseOut = reverseProc.inputStream.bufferedReader().readText().trim()
            reverseProc.waitFor(5, TimeUnit.SECONDS)

            if (reverseProc.exitValue() != 0) {
                NodeLog.w("nodeconnect/discovery", "adb reverse warning: $reverseOut")
            }

            NodeLog.i("nodeconnect/discovery", "ADB USB link active: forward $forwardPort -> $targetPort, reverse $reversePort -> $targetPort")
            Result.success(Pair(forwardPort, reversePort))
        } catch (e: Exception) {
            NodeLog.e("nodeconnect/discovery", "Error establishing ADB USB link: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun removeUsbLink(forwardPort: Int = DEFAULT_FORWARD_PORT, reversePort: Int = DEFAULT_REVERSE_PORT) {
        val adb = findAdbPath() ?: return
        try {
            ProcessBuilder(adb, "forward", "--remove", "tcp:$forwardPort").start().waitFor(2, TimeUnit.SECONDS)
            ProcessBuilder(adb, "reverse", "--remove", "tcp:$reversePort").start().waitFor(2, TimeUnit.SECONDS)
            NodeLog.i("nodeconnect/discovery", "Removed ADB USB link forwards")
        } catch (e: Exception) {
            NodeLog.w("nodeconnect/discovery", "Error removing ADB forwards: ${e.message}")
        }
    }
}
