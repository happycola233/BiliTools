package com.happycola233.bilitools.update

import com.happycola233.bilitools.data.ReleaseAssetInfo
import java.util.Locale

internal object UpdateApkAssetSelector {
    const val LEGACY_APK_ASSET_NAME = "app-release.apk"

    private const val UNIVERSAL_ABI = "universal"
    private val recognizedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    private val splitAssetNamePattern = Regex(
        pattern = "^BiliTools-v(.+)-(arm64-v8a|armeabi-v7a|x86|x86_64|universal)\\.apk$",
        option = RegexOption.IGNORE_CASE,
    )
    private val legacyDownloadedAssetPattern = Regex(
        pattern = "^app-release-(.+)\\.apk$",
        option = RegexOption.IGNORE_CASE,
    )

    fun selectBestAsset(
        releaseVersionName: String,
        assets: List<ReleaseAssetInfo>,
        supportedAbis: List<String>,
    ): ReleaseAssetInfo? {
        val parsedAssets = assets.mapNotNull { asset ->
            parseSplitAsset(asset, releaseVersionName)
        }
        val matchedSplitAssets = linkedMapOf<String, ReleaseAssetInfo>()
        parsedAssets.forEach { parsedAsset ->
            matchedSplitAssets.putIfAbsent(parsedAsset.abi, parsedAsset.asset)
        }

        supportedAbis.asSequence()
            .map(::normalizeAbi)
            .firstNotNullOfOrNull { abi -> matchedSplitAssets[abi] }
            ?.let { return it }

        matchedSplitAssets[UNIVERSAL_ABI]?.let { return it }

        return assets.firstOrNull { asset ->
            asset.name.trim().equals(LEGACY_APK_ASSET_NAME, ignoreCase = true) &&
                asset.downloadUrl.isNotBlank()
        }
    }

    fun versionFromDownloadedPackageFileName(fileName: String): String? {
        val trimmed = fileName.trim()
        val splitMatch = splitAssetNamePattern.matchEntire(trimmed)
        if (splitMatch != null) {
            return normalizeVersion(splitMatch.groupValues[1])
        }

        val legacyMatch = legacyDownloadedAssetPattern.matchEntire(trimmed)
        if (legacyMatch != null) {
            return normalizeVersion(legacyMatch.groupValues[1])
        }

        return null
    }

    fun sanitizeDownloadedPackageFileName(fileName: String): String {
        val leafName = fileName.trim().substringAfterLast('/').substringAfterLast('\\')
        val normalized = leafName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_')
        if (normalized.isBlank()) {
            return "app-update.apk"
        }
        return if (normalized.endsWith(".apk", ignoreCase = true)) {
            normalized
        } else {
            "$normalized.apk"
        }
    }

    private fun parseSplitAsset(
        asset: ReleaseAssetInfo,
        releaseVersionName: String,
    ): ParsedSplitAsset? {
        val name = asset.name.trim()
        val match = splitAssetNamePattern.matchEntire(name) ?: return null
        val assetVersionName = normalizeVersion(match.groupValues[1])
        if (assetVersionName != normalizeVersion(releaseVersionName)) {
            return null
        }

        val abi = normalizeAbi(match.groupValues[2])
        if (abi !in recognizedAbis && abi != UNIVERSAL_ABI) {
            return null
        }

        return ParsedSplitAsset(
            asset = asset,
            abi = abi,
        )
    }

    private fun normalizeAbi(value: String): String {
        return value.trim().lowercase(Locale.US)
    }

    private fun normalizeVersion(value: String): String {
        return value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
    }

    private data class ParsedSplitAsset(
        val asset: ReleaseAssetInfo,
        val abi: String,
    )
}
