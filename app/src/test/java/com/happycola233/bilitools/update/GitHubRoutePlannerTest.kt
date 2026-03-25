package com.happycola233.bilitools.update

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubRoutePlannerTest {
    @Test
    fun normalizeGitHubUrl_unwrapsKnownMirrorPrefixes() {
        val mirrored =
            "https://gh-proxy.com/https://ghproxy.net/https://github.com/happycola233/BiliTools/releases/download/v1.5/app-release.apk"

        assertEquals(
            "https://github.com/happycola233/BiliTools/releases/download/v1.5/app-release.apk",
            GitHubRoutePlanner.normalizeGitHubUrl(mirrored),
        )
    }

    @Test
    fun releaseApiCandidates_keepVerifiedRoutesOnly() {
        val routes = GitHubRoutePlanner.releaseApiCandidates(
            url = "https://api.github.com/repos/happycola233/BiliTools/releases/latest",
        ).map { it.routeId }

        assertEquals(
            listOf(
                GitHubRoutePlanner.ROUTE_GH_PROXY,
                GitHubRoutePlanner.ROUTE_GITHUB,
            ),
            routes,
        )
    }

    @Test
    fun releaseAssetCandidates_keepRemainingOrderForLargeAssets() {
        val routes = GitHubRoutePlanner.releaseAssetCandidates(
            url = "https://github.com/happycola233/BiliTools/releases/download/v1.5/app-release.apk",
            assetSizeBytes = GitHubRoutePlanner.LARGE_ASSET_THRESHOLD_BYTES,
        ).map { it.routeId }

        assertEquals(
            listOf(
                GitHubRoutePlanner.ROUTE_GH_PROXY,
                GitHubRoutePlanner.ROUTE_GHPROXY_NET,
                GitHubRoutePlanner.ROUTE_GITHUB,
            ),
            routes,
        )
    }

    @Test
    fun releaseAssetCandidates_demoteRecentlyFailedRoute() {
        val routes = GitHubRoutePlanner.releaseAssetCandidates(
            url = "https://github.com/happycola233/BiliTools/releases/download/v1.5/app-release.apk",
            assetSizeBytes = 30L * 1024L * 1024L,
            preferredRouteId = GitHubRoutePlanner.ROUTE_GH_PROXY,
            failedRouteIds = setOf(GitHubRoutePlanner.ROUTE_GH_PROXY),
        ).map { it.routeId }

        assertEquals(
            listOf(
                GitHubRoutePlanner.ROUTE_GHPROXY_NET,
                GitHubRoutePlanner.ROUTE_GITHUB,
                GitHubRoutePlanner.ROUTE_GH_PROXY,
            ),
            routes,
        )
    }

    @Test
    fun resolveReleasePageUrl_usesPreferredMirrorWhenAvailable() {
        assertEquals(
            "https://gh-proxy.com/https://github.com/happycola233/BiliTools/releases/tag/v1.5",
            GitHubRoutePlanner.resolveReleasePageUrl(
                url = "https://github.com/happycola233/BiliTools/releases/tag/v1.5",
                preferredRouteId = GitHubRoutePlanner.ROUTE_GH_PROXY,
            ),
        )
    }
}
