package com.happycola233.bilitools.update

import android.content.Context

enum class GitHubRoutePurpose(val prefsKey: String) {
    ReleaseApi("release_api"),
    ReleaseAsset("release_asset"),
}

data class GitHubResolvedRoute(
    val routeId: String,
    val label: String,
    val url: String,
)

private data class GitHubRouteDefinition(
    val id: String,
    val label: String,
    val prefix: String? = null,
    val releaseApiPriority: Int? = null,
    val smallAssetPriority: Int? = null,
    val largeAssetPriority: Int? = null,
) {
    fun supports(purpose: GitHubRoutePurpose, assetSizeBytes: Long): Boolean {
        return priorityFor(purpose, assetSizeBytes) != null
    }

    fun priorityFor(purpose: GitHubRoutePurpose, assetSizeBytes: Long): Int? {
        return when (purpose) {
            GitHubRoutePurpose.ReleaseApi -> releaseApiPriority
            GitHubRoutePurpose.ReleaseAsset -> {
                if (GitHubRoutePlanner.isLargeAsset(assetSizeBytes)) {
                    largeAssetPriority
                } else {
                    smallAssetPriority
                }
            }
        }
    }

    fun resolve(url: String): String {
        return prefix?.plus(url) ?: url
    }
}

internal object GitHubRoutePlanner {
    internal const val ROUTE_GITHUB = "github"
    internal const val ROUTE_GH_PROXY = "gh-proxy.com"
    internal const val ROUTE_GHPROXY_NET = "ghproxy.net"
    internal const val LARGE_ASSET_THRESHOLD_BYTES = 1024L * 1024L * 1024L

    private val routes = listOf(
        GitHubRouteDefinition(
            id = ROUTE_GITHUB,
            label = "GitHub",
            prefix = null,
            releaseApiPriority = 1,
            smallAssetPriority = 2,
            largeAssetPriority = 2,
        ),
        GitHubRouteDefinition(
            id = ROUTE_GH_PROXY,
            label = "gh-proxy.com",
            prefix = "https://gh-proxy.com/",
            releaseApiPriority = 0,
            smallAssetPriority = 0,
            largeAssetPriority = 0,
        ),
        GitHubRouteDefinition(
            id = ROUTE_GHPROXY_NET,
            label = "ghproxy.net",
            prefix = "https://ghproxy.net/",
            releaseApiPriority = null,
            smallAssetPriority = 1,
            largeAssetPriority = 1,
        ),
    )

    val routeIds: Set<String> = routes.mapTo(linkedSetOf()) { it.id }

    fun normalizeGitHubUrl(url: String): String {
        var normalized = url.trim()
        while (true) {
            val unwrapped = routes.firstNotNullOfOrNull { route ->
                val prefix = route.prefix ?: return@firstNotNullOfOrNull null
                if (!normalized.startsWith(prefix, ignoreCase = true)) {
                    return@firstNotNullOfOrNull null
                }
                normalized.substring(prefix.length).trim()
                    .takeIf {
                        it.startsWith("https://", ignoreCase = true) ||
                            it.startsWith("http://", ignoreCase = true)
                    }
            } ?: break
            normalized = unwrapped
        }
        return normalized
    }

    fun isLargeAsset(assetSizeBytes: Long): Boolean {
        return assetSizeBytes >= LARGE_ASSET_THRESHOLD_BYTES
    }

    fun releaseApiCandidates(
        url: String,
        preferredRouteId: String? = null,
        failedRouteIds: Set<String> = emptySet(),
    ): List<GitHubResolvedRoute> {
        return buildCandidates(
            purpose = GitHubRoutePurpose.ReleaseApi,
            url = url,
            assetSizeBytes = -1L,
            preferredRouteId = preferredRouteId,
            failedRouteIds = failedRouteIds,
        )
    }

    fun releaseAssetCandidates(
        url: String,
        assetSizeBytes: Long,
        preferredRouteId: String? = null,
        failedRouteIds: Set<String> = emptySet(),
    ): List<GitHubResolvedRoute> {
        return buildCandidates(
            purpose = GitHubRoutePurpose.ReleaseAsset,
            url = url,
            assetSizeBytes = assetSizeBytes,
            preferredRouteId = preferredRouteId,
            failedRouteIds = failedRouteIds,
        )
    }

    fun resolveReleasePageUrl(
        url: String,
        preferredRouteId: String? = null,
    ): String {
        val normalized = normalizeGitHubUrl(url)
        val route = routes.firstOrNull { it.id == preferredRouteId }
        return route?.resolve(normalized) ?: normalized
    }

    private fun buildCandidates(
        purpose: GitHubRoutePurpose,
        url: String,
        assetSizeBytes: Long,
        preferredRouteId: String?,
        failedRouteIds: Set<String>,
    ): List<GitHubResolvedRoute> {
        val normalizedUrl = normalizeGitHubUrl(url)
        return routes
            .filter { it.supports(purpose, assetSizeBytes) }
            .sortedWith(
                compareBy<GitHubRouteDefinition> { if (it.id in failedRouteIds) 1 else 0 }
                    .thenBy { if (it.id == preferredRouteId) 0 else 1 }
                    .thenBy { it.priorityFor(purpose, assetSizeBytes) ?: Int.MAX_VALUE },
            )
            .map { route ->
                GitHubResolvedRoute(
                    routeId = route.id,
                    label = route.label,
                    url = route.resolve(normalizedUrl),
                )
            }
    }
}

class GitHubRouteManager(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun normalizeGitHubUrl(url: String): String = GitHubRoutePlanner.normalizeGitHubUrl(url)

    fun releaseApiCandidates(url: String): List<GitHubResolvedRoute> {
        return GitHubRoutePlanner.releaseApiCandidates(
            url = url,
            preferredRouteId = preferredRouteId(GitHubRoutePurpose.ReleaseApi),
            failedRouteIds = failedRouteIds(GitHubRoutePurpose.ReleaseApi),
        )
    }

    fun releaseAssetCandidates(url: String, assetSizeBytes: Long): List<GitHubResolvedRoute> {
        return GitHubRoutePlanner.releaseAssetCandidates(
            url = url,
            assetSizeBytes = assetSizeBytes,
            preferredRouteId = preferredRouteId(GitHubRoutePurpose.ReleaseAsset),
            failedRouteIds = failedRouteIds(GitHubRoutePurpose.ReleaseAsset),
        )
    }

    fun resolveReleasePageUrl(url: String): String {
        return GitHubRoutePlanner.resolveReleasePageUrl(
            url = url,
            preferredRouteId = preferredRouteId(GitHubRoutePurpose.ReleaseApi),
        )
    }

    fun markSuccess(purpose: GitHubRoutePurpose, routeId: String) {
        prefs.edit()
            .putString(preferredKey(purpose), routeId)
            .remove(failureKey(purpose, routeId))
            .apply()
    }

    fun markFailure(purpose: GitHubRoutePurpose, routeId: String) {
        prefs.edit()
            .putLong(failureKey(purpose, routeId), System.currentTimeMillis())
            .apply()
    }

    private fun preferredRouteId(purpose: GitHubRoutePurpose): String? {
        return prefs.getString(preferredKey(purpose), null)
            ?.takeIf { it in GitHubRoutePlanner.routeIds }
    }

    private fun failedRouteIds(purpose: GitHubRoutePurpose): Set<String> {
        val now = System.currentTimeMillis()
        return GitHubRoutePlanner.routeIds.filterTo(linkedSetOf()) { routeId ->
            val failedAt = prefs.getLong(failureKey(purpose, routeId), 0L)
            failedAt > 0L && now - failedAt < FAILURE_COOLDOWN_MS
        }
    }

    private fun preferredKey(purpose: GitHubRoutePurpose): String {
        return "${purpose.prefsKey}_preferred"
    }

    private fun failureKey(purpose: GitHubRoutePurpose, routeId: String): String {
        return "${purpose.prefsKey}_failed_$routeId"
    }

    companion object {
        private const val PREFS_NAME = "github_route_cache"
        private const val FAILURE_COOLDOWN_MS = 6L * 60L * 60L * 1000L
    }
}
