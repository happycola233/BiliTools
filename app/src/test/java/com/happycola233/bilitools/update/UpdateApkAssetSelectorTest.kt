package com.happycola233.bilitools.update

import com.happycola233.bilitools.data.ReleaseAssetInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkAssetSelectorTest {
    @Test
    fun selectBestAsset_prefersFirstSupportedAbiMatch() {
        val selected = UpdateApkAssetSelector.selectBestAsset(
            releaseVersionName = "2.2",
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            assets = listOf(
                asset("BiliTools-v2.2-universal.apk"),
                asset("BiliTools-v2.2-armeabi-v7a.apk"),
                asset("BiliTools-v2.2-arm64-v8a.apk"),
            ),
        )

        assertEquals("BiliTools-v2.2-arm64-v8a.apk", selected?.name)
    }

    @Test
    fun selectBestAsset_fallsBackToNextCompatibleAbiBeforeUniversal() {
        val selected = UpdateApkAssetSelector.selectBestAsset(
            releaseVersionName = "2.2",
            supportedAbis = listOf("x86_64", "x86"),
            assets = listOf(
                asset("BiliTools-v2.2-universal.apk"),
                asset("BiliTools-v2.2-x86.apk"),
            ),
        )

        assertEquals("BiliTools-v2.2-x86.apk", selected?.name)
    }

    @Test
    fun selectBestAsset_ignoresAssetsFromOtherVersions() {
        val selected = UpdateApkAssetSelector.selectBestAsset(
            releaseVersionName = "2.2",
            supportedAbis = listOf("arm64-v8a"),
            assets = listOf(
                asset("BiliTools-v2.1-arm64-v8a.apk"),
                asset("BiliTools-v2.2-universal.apk"),
            ),
        )

        assertEquals("BiliTools-v2.2-universal.apk", selected?.name)
    }

    @Test
    fun selectBestAsset_fallsBackToLegacyUniversalAsset() {
        val selected = UpdateApkAssetSelector.selectBestAsset(
            releaseVersionName = "2.2",
            supportedAbis = listOf("arm64-v8a"),
            assets = listOf(
                asset("app-release.apk"),
            ),
        )

        assertEquals("app-release.apk", selected?.name)
    }

    @Test
    fun versionFromDownloadedPackageFileName_supportsSplitAndLegacyNames() {
        assertEquals(
            "2.2",
            UpdateApkAssetSelector.versionFromDownloadedPackageFileName(
                "BiliTools-v2.2-arm64-v8a.apk",
            ),
        )
        assertEquals(
            "2.2",
            UpdateApkAssetSelector.versionFromDownloadedPackageFileName("app-release-2.2.apk"),
        )
        assertNull(UpdateApkAssetSelector.versionFromDownloadedPackageFileName("random-file.apk"))
    }

    private fun asset(name: String): ReleaseAssetInfo {
        return ReleaseAssetInfo(
            name = name,
            downloadUrl = "https://example.com/$name",
            sizeBytes = 1L,
        )
    }
}
