package com.nodeplayer.music.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Arrays

object UpdateInstaller {

    private const val TAG = "UpdateInstaller"

    sealed class PreInstallValidationResult {
        data object Valid : PreInstallValidationResult()
        data class Invalid(val reason: String) : PreInstallValidationResult()
    }

    /**
     * Performs strict security validation on the downloaded APK before calling PackageInstaller:
     * 1. Package name must match exactly.
     * 2. Signing certificate must match the installed app's certificate.
     * 3. Version must be strictly newer.
     */
    fun validateApkSecurity(context: Context, apkFile: File): PreInstallValidationResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return PreInstallValidationResult.Invalid("Update file not found or empty.")
        }

        val pm = context.packageManager
        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return PreInstallValidationResult.Invalid("Could not parse downloaded APK archive.")

        // 1. Package Name check
        if (archiveInfo.packageName != context.packageName) {
            apkFile.delete()
            return PreInstallValidationResult.Invalid(
                "Package name mismatch: expected ${context.packageName} but got ${archiveInfo.packageName}"
            )
        }

        // 2. Version check
        val currentVersion = UpdateChecker.getInstalledVersionName(context)
        val candidateVersion = archiveInfo.versionName ?: "0.0.0"
        if (!SemVer.isNewer(candidateVersion, currentVersion)) {
            apkFile.delete()
            return PreInstallValidationResult.Invalid(
                "Version is not newer: candidate v$candidateVersion <= installed v$currentVersion"
            )
        }

        // 3. Signing Certificate check
        val installedSignatures = getAppSignatures(context, context.packageName, isArchive = false, apkFile = null)
        val archiveSignatures = getAppSignatures(context, context.packageName, isArchive = true, apkFile = apkFile)

        if (installedSignatures.isEmpty() || archiveSignatures.isEmpty()) {
            apkFile.delete()
            return PreInstallValidationResult.Invalid("Could not read signing certificates from APK.")
        }

        val matches = signaturesMatch(installedSignatures, archiveSignatures)
        if (!matches) {
            apkFile.delete()
            return PreInstallValidationResult.Invalid(
                "Signing certificate mismatch! The update is signed with a different key than the installed app. Installation refused for security."
            )
        }

        return PreInstallValidationResult.Valid
    }

    private fun getAppSignatures(
        context: Context,
        packageName: String,
        isArchive: Boolean,
        apkFile: File?
    ): List<ByteArray> {
        val pm = context.packageManager
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES
                val pkgInfo: PackageInfo? = if (isArchive && apkFile != null) {
                    pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                } else {
                    pm.getPackageInfo(packageName, flags)
                }
                val signingInfo = pkgInfo?.signingInfo ?: return emptyList()
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners.map { it.toByteArray() }
                } else {
                    signingInfo.signingCertificateHistory.map { it.toByteArray() }
                }
            } else {
                @Suppress("DEPRECATION")
                val flags = PackageManager.GET_SIGNATURES
                val pkgInfo: PackageInfo? = if (isArchive && apkFile != null) {
                    pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
                } else {
                    pm.getPackageInfo(packageName, flags)
                }
                @Suppress("DEPRECATION")
                pkgInfo?.signatures?.map { it.toByteArray() } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get signatures (isArchive=$isArchive)", e)
            return emptyList()
        }
    }

    private fun signaturesMatch(installed: List<ByteArray>, archive: List<ByteArray>): Boolean {
        // Compare SHA-256 fingerprint of certificates
        val installedHashes = installed.map { sha256Fingerprint(it) }.toSet()
        val archiveHashes = archive.map { sha256Fingerprint(it) }.toSet()
        return installedHashes.intersect(archiveHashes).isNotEmpty()
    }

    private fun sha256Fingerprint(certBytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(certBytes).joinToString("") { "%02X".format(it) }
    }

    /**
     * Checks if the app has permission to request package installation.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Returns the Intent to navigate the user to enable "Install unknown apps" for Node Player.
     */
    fun getManageUnknownAppSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Initiates installation through Android's PackageInstaller session API.
     */
    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            val pm = context.packageManager
            val packageInstaller = pm.packageInstaller

            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, apkFile.length()).use { out ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }

            val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                action = UpdateInstallReceiver.ACTION_INSTALL_STATUS
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                statusIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.i(TAG, "Committed package installer session $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller session failed", e)
            Result.failure(e)
        }
    }
}
