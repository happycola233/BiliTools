package com.happycola233.bilitools.update

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

internal const val UPDATE_DIRECTORY_NAME = "updates"

internal fun updatePackageDirectory(context: Context): File = File(context.filesDir, UPDATE_DIRECTORY_NAME)

internal fun updatePackageFileName(versionName: String): String {
    val safeVersion = versionName
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "latest" }
    return "app-release-$safeVersion.apk"
}

internal class UpdatePackageCleanupManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun cleanupAfterAppUpdateIfNeeded() {
        val packageInfo = currentPackageInfo()
        val currentFingerprint = versionFingerprint(packageInfo)
        val lastFingerprint = prefs.getString(KEY_LAST_VERSION_FINGERPRINT, null)
        if (lastFingerprint == currentFingerprint) return

        val shouldCleanup = !lastFingerprint.isNullOrBlank() ||
            hasInstalledPackageArtifact(packageInfo.versionName?.trim().orEmpty())
        if (shouldCleanup) {
            val deletedCount = deleteDownloadedPackages()
            if (deletedCount > 0) {
                Log.i(TAG, "[update] cleared $deletedCount downloaded APK(s) after app upgrade")
            }
        }

        prefs.edit().putString(KEY_LAST_VERSION_FINGERPRINT, currentFingerprint).apply()
    }

    fun deleteDownloadedPackages(): Int {
        val directory = updatePackageDirectory(appContext)
        val apkFiles = directory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
            .orEmpty()
        var deletedCount = 0

        apkFiles.forEach { apkFile ->
            if (runCatching { apkFile.delete() }.getOrDefault(false)) {
                deletedCount += 1
            }
        }

        if (directory.exists() && directory.listFiles().isNullOrEmpty()) {
            runCatching { directory.delete() }
        }

        return deletedCount
    }

    @Suppress("DEPRECATION")
    private fun currentPackageInfo() =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0)

    private fun versionFingerprint(packageInfo: android.content.pm.PackageInfo): String {
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val versionName = packageInfo.versionName?.trim().orEmpty()
        return "$versionCode:$versionName"
    }

    private fun hasInstalledPackageArtifact(versionName: String): Boolean {
        if (versionName.isBlank()) return false
        val expectedFile = File(updatePackageDirectory(appContext), updatePackageFileName(versionName))
        return expectedFile.exists()
    }

    companion object {
        private const val TAG = "UpdatePkgCleanup"
        private const val PREFS_NAME = "update_package_cleanup"
        private const val KEY_LAST_VERSION_FINGERPRINT = "last_version_fingerprint"
    }
}
