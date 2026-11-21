package com.arflix.tv.data.repository

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.util.settingsDataStore
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class IptvPlaylistPersistenceTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var profiles: ProfileManager
    private fun repository(client: OkHttpClient = OkHttpClient()) = IptvRepository(RuntimeEnvironment.getApplication(), client, profiles, CloudSyncInvalidationBus())
    private fun playlists() = (1..10).map {
        IptvPlaylistEntry("list_$it", "Playlist $it", "https://example.invalid/$it.m3u", importVod = it % 2 == 0)
    }

    @Before fun setup() {
        val state = MutableStateFlow(emptyPreferences())
        val lock = Mutex()
        val store = object : DataStore<Preferences> {
            override val data = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                lock.withLock { transform(state.value).toPreferences().also { state.value = it } }
        }
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        every { any<Context>().settingsDataStore } returns store
        profiles = mockk()
        every { profiles.activeProfileId } returns MutableStateFlow("main")
        every { profiles.getProfileIdSync() } returns "main"
        every { profiles.profileStringKey(any()) } answers { stringPreferencesKey("main:${firstArg<String>()}") }
        every { profiles.profileStringKeyFor(any(), any()) } answers { stringPreferencesKey("${firstArg<String>()}:${secondArg<String>()}") }
    }
    @After fun cleanup() { unmockkStatic("com.arflix.tv.util.DataStoresKt") }

    @Test fun startupKeepsOnlyTheChannelWindowAfterPersistingLargePlaylist() = runBlocking {
        val repo = repository()
        repo.savePlaylists(playlists().take(1))
        val config = repo.observeConfig().first()
        val channels = List(12_000) { index ->
            IptvChannel(
                id = "list_1:channel-$index",
                name = "Channel $index",
                streamUrl = "https://streams.example.invalid/$index",
                group = "News",
            )
        }

        repo.storeStartupChannels("main", config, channels, emptyMap())

        assertThat(repo.getMemoryCachedSnapshot()?.channels).hasSize(240)
        assertThat(repo.getMemoryCachedSnapshot()?.grouped?.get("News")).hasSize(240)
        assertThat(repo.pagedChannelStoreCount()).isEqualTo(channels.size)
    }

    @Test fun guideAndPlaylistFetchesLeaveTheSharedHttpCacheUntouched() = runBlocking {
        MockWebServer().use { server ->
            Cache(temp.newFolder(), 1024 * 1024L).use { cache ->
                val client = OkHttpClient.Builder().cache(cache).build()
                server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=3600").setBody("cached response"))
                client.newCall(Request.Builder().url(server.url("/small")).build()).execute().use {
                    assertThat(it.body!!.string()).isEqualTo("cached response")
                }
                val cacheBytes = cache.size()
                assertThat(cacheBytes).isGreaterThan(0L)

                val playlistUrl = server.url("/playlist.m3u")
                val guideUrl = server.url("/guide.xml")
                server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=3600").setBody(
                    "#EXTM3U\n#EXTINF:-1 tvg-id=\"news\" group-title=\"News\",News\nhttps://streams.example.invalid/news"
                ))
                server.enqueue(MockResponse().setHeader("Cache-Control", "max-age=3600").setBody(
                    """<tv><channel id="news"><display-name>News</display-name></channel>
                    <programme channel="news" start="20200101000000 +0000" stop="20990101000000 +0000">
                    <title>News Bulletin</title></programme></tv>"""
                ))
                val repo = repository(client)
                repo.savePlaylists(listOf(IptvPlaylistEntry("local", "Local", playlistUrl.toString(), epgUrl = guideUrl.toString())))

                val snapshot = repo.loadSnapshot(forcePlaylistReload = true, forceEpgReload = true, allowNetworkEpgFetch = true)

                assertThat(snapshot.channels).hasSize(1)
                assertThat(server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)?.path).isEqualTo("/small")
                assertThat(server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)?.path).isEqualTo("/playlist.m3u")
                assertThat(server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS)?.path).isEqualTo("/guide.xml")
                assertThat(cache.size()).isEqualTo(cacheBytes)
            }
        }
    }

    @Test fun tenPlaylistsSurviveRecreationAndCloudRoundTrip() = runBlocking {
        repository().savePlaylists(playlists())
        val reloaded = repository()
        assertEquals(playlists(), reloaded.observeConfig().first().playlists)
        val cloud = reloaded.exportCloudConfigForProfile("main")
        reloaded.importCloudConfigForProfile("other", cloud)
        assertEquals(playlists(), repository().exportCloudConfigForProfile("other").playlists)
    }

    @Test fun oldDuplicateIdsAreRepairedOnCloudImportWithoutLosingAPlaylist() = runBlocking {
        val input = playlists().take(3).mapIndexed { index, item -> if (index == 2) item.copy(id = "list_2") else item }
        val repo = repository()
        repo.importCloudConfigForProfile("main", IptvCloudProfileState(playlists = input))
        val saved = repository().observeConfig().first().playlists
        assertEquals(3, saved.map { it.id }.distinct().size)
        assertEquals(input.map { it.m3uUrl }, saved.map { it.m3uUrl })
    }

    @Test fun eleventhPlaylistIsRejectedWithoutSilentlyChangingSavedLists() = runBlocking {
        val repo = repository()
        repo.savePlaylists(playlists())
        val result = runCatching { repo.savePlaylists(playlists() + playlists()[0].copy(id = "eleventh")) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(playlists(), repository().observeConfig().first().playlists)
    }

    @Test fun tenPlaylistsLoadSeparateChannelSetsAndTenPortalsSurviveCloudRestore() = runBlocking {
        val requested = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requested.add(request.url.encodedPath)
            val n = request.url.pathSegments.last().substringBefore('.')
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("#EXTM3U\n#EXTINF:-1 group-title=\"News\",Channel $n\nhttps://streams.example.invalid/$n.m3u8".toResponseBody()).build()
        }.build()
        val repo = repository(client)
        repo.savePlaylists(playlists())
        val snapshot = repo.loadSnapshot(forcePlaylistReload = true, allowBroadShortEpg = false)
        assertEquals(10, requested.size)
        assertEquals(playlists().map { it.id }, snapshot.channels.map { it.id.substringBefore(':') })
        assertEquals(10, snapshot.channels.map { it.id }.distinct().size)
        val portals = (1..10).map { StalkerPortalEntry("stalker$it", "Portal $it", "https://portal$it.invalid", "00:1A:79:00:00:${it.toString().padStart(2, '0')}") }
        repo.importCloudConfigForProfile("other", repo.exportCloudConfigForProfile("main").copy(stalkerPortals = portals))
        assertEquals(portals, repository().exportCloudConfigForProfile("other").stalkerPortals)
    }
}
