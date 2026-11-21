package com.arflix.tv.data.repository

import android.app.Application
import com.arflix.tv.data.api.*
import com.arflix.tv.data.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class NativeAddonSearchTest {
    private fun repository(api: StreamApi): StreamRepository {
        val profileManager = mockk<ProfileManager>(relaxed = true)
        // personalAddons combines streamDataStore.data with profileManager.activeProfileId.
        // On a relaxed mock that property is a mock Flow whose collect() completes without
        // ever emitting, so combine() can never produce its first value and anything that
        // reads installedAddons.first() (as searchNativeAddonCatalogs does) hangs forever -
        // the outer test guard times out and the test reports a flaky timeout. Stub it with
        // a real emitting flow.
        every { profileManager.activeProfileId } returns MutableStateFlow("")
        return StreamRepository(
            RuntimeEnvironment.getApplication(), api,
            mockk(relaxed = true), profileManager, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test fun nativeSearchRetainsFastResultsAndEpisodePlaybackUsesExactVideoId() = runBlocking {
        val api = mockk<StreamApi>()
        val repository = repository(api)
        val manifest = AddonManifest("provider", "Provider", "1", resources = listOf(
            AddonResource("meta", listOf("series"), listOf("provider:")),
            AddonResource("stream", listOf("series"), listOf("provider:"))), catalogs = listOf(
            AddonCatalog("series", "fast", extra = listOf(AddonCatalogExtra("search"))),
            AddonCatalog("series", "slow", extra = listOf(AddonCatalogExtra("search")))))
        val addon = Addon("provider", "Provider", "1", "", true, type = AddonType.COMMUNITY,
            url = "https://provider.test/manifest.json", manifest = manifest)
        repository.replaceSharedAddonsFromCloud(listOf(addon))
        coEvery { api.getAddonCatalog(match { it.contains("/fast/") }) } returns
            StremioCatalogResponse(metas = listOf(StremioMetaPreview(id = "provider:1", name = "Result")))
        coEvery { api.getAddonCatalog(match { it.contains("/slow/") }) } coAnswers { awaitCancellation() }
        // The outer budget is only a hang guard; the search itself must return in roughly
        // the 1s per-catalog budget the slow catalog is cut by.
        val searchStart = System.currentTimeMillis()
        val pages = withTimeout(60_000) { repository.searchNativeAddonCatalogs("result", timeoutMs = 1_000) }
        val searchElapsedMs = System.currentTimeMillis() - searchStart
        assertTrue(
            "searchNativeAddonCatalogs(timeoutMs=1_000) took ${searchElapsedMs}ms; expected ~1s (outer hang guard: 60s)",
            searchElapsedMs < 15_000)
        assertEquals(listOf("fast"), pages.map { it.catalogId })
        assertEquals("provider:1", pages.single().metas.single().id)
        assertTrue(repository.addonServesOwnMeta(addon.id, "series", "provider:show"))
        coEvery { api.getAddonMeta(any()) } returns StremioMetaResponse(StremioMetaPreview(
            id = "provider:show", videos = listOf(StremioMetaVideo(
                id = "provider:episode/abc?part=2", season = 2, episode = 3))))
        coEvery { api.getAddonStreams(any()) } returns StremioStreamResponse(listOf(
            StremioStream(url = "https://provider.test/video.m3u8")))
        repository.getAddonMeta(addon.id, "series", "provider:show")
        val streams = repository.resolveAddonStreams(addon, MediaType.TV, "provider:show", season = 2, episode = 3)
        assertFalse(streams.isEmpty())
        coVerify(exactly = 1) { api.getAddonMeta(any()) }
        coVerify(exactly = 1) {
            api.getAddonStreams("https://provider.test/stream/series/provider%3Aepisode%2Fabc%3Fpart%3D2.json")
        }
        coVerify(exactly = 0) { api.getAddonStreams(match { it.contains("show:2:3") }) }
    }
}
