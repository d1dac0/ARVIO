package com.arflix.tv.updater

import android.os.Build
import com.google.gson.annotations.SerializedName

data class GitHubAssetDto(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val size: Long?
)

object AbiSelector {
    private val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun chooseBestApkAsset(assets: List<GitHubAssetDto>): GitHubAssetDto? =
        chooseBestApkAsset(assets, Build.SUPPORTED_ABIS?.toList().orEmpty())

    internal fun chooseBestApkAsset(
        assets: List<GitHubAssetDto>,
        supportedAbis: List<String>,
    ): GitHubAssetDto? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.size <= 1) return apkAssets.firstOrNull()

        val assetAbis = apkAssets.associateWith { asset ->
            knownAbis.firstOrNull { abi -> asset.name.contains(abi, ignoreCase = true) }
        }
        for (abi in supportedAbis) {
            val candidate = apkAssets.firstOrNull { assetAbis[it].equals(abi, ignoreCase = true) }
            if (candidate != null) return candidate
        }

        val fallbackAssets = apkAssets.filter { assetAbis[it] == null }
        return fallbackAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: fallbackAssets.firstOrNull {
                val name = it.name.lowercase()
                name.contains("all") || name.contains("release")
            }
            ?: fallbackAssets.firstOrNull()
            ?: apkAssets.first()
    }
}
