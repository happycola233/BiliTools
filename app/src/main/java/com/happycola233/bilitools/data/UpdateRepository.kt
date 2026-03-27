package com.happycola233.bilitools.data

import android.content.Context
import com.happycola233.bilitools.core.AppLog as Log
import com.happycola233.bilitools.core.createHttpDiagnosticLoggingInterceptor
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.happycola233.bilitools.update.GitHubRouteManager
import com.happycola233.bilitools.update.GitHubRoutePurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.max

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String?,
    val bodyMarkdown: String,
    val htmlUrl: String,
    val apkAsset: ReleaseAssetInfo?,
)

data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val currentVersion: String,
        val release: ReleaseInfo,
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
    ) : UpdateCheckResult

    data class Failed(
        val currentVersion: String,
        val errorMessage: String,
    ) : UpdateCheckResult
}

class UpdateRepository(
    context: Context,
    private val gitHubRouteManager: GitHubRouteManager,
    private val settingsRepository: SettingsRepository,
) {
    private val appContext = context.applicationContext

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                createHttpDiagnosticLoggingInterceptor(
                    tag = TAG,
                    settingsRepository = settingsRepository,
                ),
            )
            .build()
    }
    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    private val latestAdapter by lazy { moshi.adapter(GitHubReleaseResponse::class.java) }

    fun currentVersionName(): String {
        @Suppress("DEPRECATION")
        val versionName = runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
        }.getOrNull()
        return versionName?.takeIf { it.isNotBlank() } ?: "0"
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = normalizeVersion(currentVersionName())
        runCatching {
            val latest = fetchLatestRelease()
            if (compareVersions(currentVersion, latest.versionName) < 0) {
                UpdateCheckResult.UpdateAvailable(currentVersion, latest)
            } else {
                UpdateCheckResult.UpToDate(currentVersion, latest.versionName)
            }
        }.getOrElse { error ->
            UpdateCheckResult.Failed(
                currentVersion = currentVersion,
                errorMessage = error.message ?: "Unknown error",
            )
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo {
        val routes = gitHubRouteManager.releaseApiCandidates(LATEST_RELEASE_API)
        var lastError: Throwable? = null

        for (route in routes) {
            val request = Request.Builder()
                .url(route.url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "BiliTools-Android")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val payload = response.body?.string().orEmpty()
                    val parsed = latestAdapter.fromJson(payload)
                        ?: throw IOException("Empty release response")

                    val tagName = parsed.tagName?.trim().orEmpty()
                    val htmlUrl = gitHubRouteManager.normalizeGitHubUrl(
                        parsed.htmlUrl?.trim().orEmpty(),
                    )
                    if (tagName.isBlank() || htmlUrl.isBlank()) {
                        throw IOException("Invalid release response")
                    }

                    val release = ReleaseInfo(
                        tagName = tagName,
                        versionName = normalizeVersion(tagName),
                        title = parsed.name?.trim()?.takeIf { it.isNotBlank() },
                        bodyMarkdown = parsed.body.orEmpty(),
                        htmlUrl = htmlUrl,
                        apkAsset = parsed.assets
                            .orEmpty()
                            .firstOrNull { asset ->
                                asset.name?.trim().equals(APK_ASSET_NAME, ignoreCase = false) &&
                                    !asset.browserDownloadUrl.isNullOrBlank()
                            }
                            ?.let { asset ->
                                ReleaseAssetInfo(
                                    name = asset.name!!.trim(),
                                    downloadUrl = gitHubRouteManager.normalizeGitHubUrl(
                                        asset.browserDownloadUrl!!.trim(),
                                    ),
                                    sizeBytes = asset.size ?: -1L,
                                )
                            },
                    )
                    gitHubRouteManager.markSuccess(GitHubRoutePurpose.ReleaseApi, route.routeId)
                    Log.i(TAG, "[update] selected release API route=${route.routeId}")
                    return release
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                lastError = error
                gitHubRouteManager.markFailure(GitHubRoutePurpose.ReleaseApi, route.routeId)
                Log.w(
                    TAG,
                    "[update] release API route failed, route=${route.routeId}, error=${error.message}",
                )
            }
        }

        throw IOException("No available GitHub update route", lastError)
    }

    private fun compareVersions(current: String, latest: String): Int {
        val currentParts = parseVersionParts(current)
        val latestParts = parseVersionParts(latest)
        if (currentParts != null && latestParts != null) {
            val maxSize = max(currentParts.size, latestParts.size)
            for (index in 0 until maxSize) {
                val left = currentParts.getOrElse(index) { 0 }
                val right = latestParts.getOrElse(index) { 0 }
                if (left != right) {
                    return left.compareTo(right)
                }
            }
            return 0
        }
        return current.compareTo(latest)
    }

    private fun parseVersionParts(version: String): List<Int>? {
        val normalized = normalizeVersion(version)
        if (normalized.isBlank()) return null
        val rawParts = normalized.split('.')
        if (rawParts.isEmpty()) return null
        val parsed = mutableListOf<Int>()
        for (part in rawParts) {
            val number = part.trim().takeWhile { it.isDigit() }
            val value = number.toIntOrNull() ?: return null
            parsed += value
        }
        return parsed
    }

    private fun normalizeVersion(value: String): String {
        return value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
    }

    companion object {
        private const val TAG = "UpdateRepository"
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/happycola233/BiliTools/releases/latest"
        private const val APK_ASSET_NAME = "app-release.apk"
    }
}

private data class GitHubReleaseResponse(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "body") val body: String?,
    @Json(name = "html_url") val htmlUrl: String?,
    @Json(name = "assets") val assets: List<GitHubReleaseAssetResponse>?,
)

private data class GitHubReleaseAssetResponse(
    @Json(name = "name") val name: String?,
    @Json(name = "browser_download_url") val browserDownloadUrl: String?,
    @Json(name = "size") val size: Long?,
)
