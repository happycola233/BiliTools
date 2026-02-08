package com.happycola233.bilitools.data

import android.content.Context
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
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

class UpdateRepository(context: Context) {
    private val appContext = context.applicationContext

    private val client by lazy { OkHttpClient() }
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
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "BiliTools-Android")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            val parsed = latestAdapter.fromJson(payload)
                ?: throw IOException("Empty release response")

            val tagName = parsed.tagName?.trim().orEmpty()
            val htmlUrl = parsed.htmlUrl?.trim().orEmpty()
            if (tagName.isBlank() || htmlUrl.isBlank()) {
                throw IOException("Invalid release response")
            }

            return ReleaseInfo(
                tagName = tagName,
                versionName = normalizeVersion(tagName),
                title = parsed.name?.trim()?.takeIf { it.isNotBlank() },
                bodyMarkdown = parsed.body.orEmpty(),
                htmlUrl = htmlUrl,
            )
        }
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
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/happycola233/BiliTools/releases/latest"
    }
}

private data class GitHubReleaseResponse(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "body") val body: String?,
    @Json(name = "html_url") val htmlUrl: String?,
)
