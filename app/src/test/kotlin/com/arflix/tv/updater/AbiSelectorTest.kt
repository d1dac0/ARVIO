package com.arflix.tv.updater

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AbiSelectorTest {
    private val arm32 = asset("ARVIO-2.3.1-fork.8-sideload-armeabi-v7a-release.apk")
    private val arm64 = asset("ARVIO-2.3.1-fork.8-sideload-arm64-v8a-release.apk")
    private val universal = asset("ARVIO-2.3.1-fork.8-sideload-universal-release.apk")
    private val releaseAssets = listOf(arm64, arm32, universal)

    @Test fun choosesThe32BitForkRelease() {
        assertThat(AbiSelector.chooseBestApkAsset(releaseAssets, listOf("armeabi-v7a", "armeabi")))
            .isEqualTo(arm32)
    }

    @Test fun prefersThe64BitForkReleaseOnDevicesThatSupportBoth() {
        assertThat(AbiSelector.chooseBestApkAsset(releaseAssets, listOf("arm64-v8a", "armeabi-v7a")))
            .isEqualTo(arm64)
    }

    @Test fun prefersTheUniversalAssetWhenThereIsNoAbiMatch() {
        assertThat(AbiSelector.chooseBestApkAsset(releaseAssets, listOf("x86_64", "x86")))
            .isEqualTo(universal)
    }

    private fun asset(name: String) = GitHubAssetDto(name, "https://example.invalid/$name", null)
}
