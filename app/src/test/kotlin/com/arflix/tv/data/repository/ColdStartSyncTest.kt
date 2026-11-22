package com.arflix.tv.data.repository

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.observeCloudSyncAccount
import com.arflix.tv.util.Constants
import com.arflix.tv.util.authDataStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ColdStartSyncTest {
    @Test
    fun cachedIdentityRestoresWithoutWaitingForAnExpiredTokenRefresh() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val requests = AtomicInteger()
        val endpointStalled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor {
            requests.incrementAndGet()
            endpointStalled.await(4, TimeUnit.SECONDS)
            throw IOException("The refresh endpoint did not answer")
        }.build()
        val trakt = mockk<TraktRepository>()
        coEvery { trakt.setUserId("cached") } returns Unit
        val repository = AuthRepository(
            context,
            client,
            Provider { trakt },
            Provider { error("Cloud sync is not needed to restore identity") },
            Provider { error("Launcher data is not needed to restore identity") },
            Provider { error("Trakt outbox is not needed to restore identity") },
            Provider { error("Plugins are not needed to restore identity") }
        )

        mockkObject(Constants)
        try {
            every { Constants.USE_NETLIFY_CLOUD_SYNC } returns true
            every { Constants.AUTH_REFRESH_URL } returns "https://test.invalid/auth-refresh"
            context.authDataStore.edit { prefs ->
                prefs[stringPreferencesKey("user_id")] = "cached"
                prefs[stringPreferencesKey("user_email")] = "user@example.com"
                prefs[stringPreferencesKey("access_token")] = "e30.eyJleHAiOjF9.invalid"
                prefs[stringPreferencesKey("refresh_token")] = "old-refresh-token"
            }

            repository.checkAuthState()

            assertThat(repository.authState.value).isEqualTo(
                AuthState.Authenticated("cached", "user@example.com", UserProfile("cached", "user@example.com"))
            )
            assertThat(requests.get()).isEqualTo(0)
        } finally {
            endpointStalled.countDown()
            context.authDataStore.edit { it.clear() }
            unmockkObject(Constants)
        }
    }

    @Test
    fun restoredAccountLoadsOneSnapshotEvenWhenItsProfileChanges() = runTest {
        val auth = mockk<AuthRepository>()
        val state = MutableStateFlow<AuthState>(AuthState.Loading)
        every { auth.authState } returns state
        coEvery { auth.loadAccountSyncPayload() } returns Result.success(null)
        backgroundScope.launch {
            auth.authState.observeCloudSyncAccount { userId ->
                if (userId != null) auth.loadAccountSyncPayload()
            }
        }
        runCurrent()

        state.value = AuthState.Authenticated("cached", "user@example.com", UserProfile("cached"))
        advanceTimeBy(3_000)
        state.value = AuthState.Authenticated("cached", "user@example.com", UserProfile("cached", addons = "[]"))
        advanceTimeBy(7_000)
        runCurrent()

        coVerify(exactly = 1) { auth.loadAccountSyncPayload() }
    }

    @Test
    fun fiveChannelChangesCoalesceIntoOneSnapshotSave() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val invalidations = CloudSyncInvalidationBus()
        val auth = mockk<AuthRepository>()
        val cloud = mockk<CloudSyncRepository>()
        coEvery { auth.getCurrentUserIdForSync() } returns "cached"
        coEvery { auth.saveAccountSyncPayload(any()) } returns Result.success(Unit)
        every { cloud.pushFailureCount } returns 0
        coEvery { cloud.markLocalStateDirtyNow() } returns Unit
        coEvery { cloud.pushToCloud(force = true) } coAnswers { auth.saveAccountSyncPayload("{}") }
        val coordinator = CloudSyncCoordinator(context, invalidations, cloud, auth, backgroundScope)
        coordinator.start()
        runCurrent()

        repeat(5) {
            invalidations.markDirty(CloudSyncScope.IPTV, reason = "save tv session")
            advanceTimeBy(2_000)
        }
        advanceTimeBy(28_000)
        coVerify(exactly = 0) { auth.saveAccountSyncPayload(any()) }
        runCurrent()
        coVerify(exactly = 1) { auth.saveAccountSyncPayload(any()) }
        coordinator.stop()
    }
}
