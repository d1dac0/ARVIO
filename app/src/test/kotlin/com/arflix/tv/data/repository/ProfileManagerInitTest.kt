package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.data.model.Profile
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * A WorkManager wake (TraktSyncWorker, CloudSyncWorker) starts the process without an
 * activity, so nothing on the activity path seeds the cached profile id. Every
 * getProfileIdSync()-scoped key must resolve for the active profile anyway.
 */
class ProfileManagerInitTest {
    private fun managerWithActiveProfile(active: Profile?): Pair<ProfileManager, ProfileRepository> {
        val repository = mockk<ProfileRepository>()
        // Constructor-time flow properties; never collected in these tests.
        every { repository.activeProfileId } returns flowOf(active?.id)
        every { repository.activeProfile } returns flowOf(active)
        coEvery { repository.getActiveProfile() } returns active
        return ProfileManager(mockk(relaxed = true), repository) to repository
    }

    @Test
    fun aWorkerWakeSeesTheActiveProfileWithoutAnActivity() = runBlocking {
        val (manager, repository) = managerWithActiveProfile(Profile(id = "kids", name = "Kids"))
        // Before anything seeds it, the cached id is the default - the background-wake hazard.
        assertThat(manager.getProfileIdSync()).isEqualTo("default")

        manager.ensureInitialized()

        assertThat(manager.getProfileIdSync()).isEqualTo("kids")
        assertThat(manager.currentProfileName.value).isEqualTo("kids")
        coVerify(exactly = 1) { repository.getActiveProfile() }
    }

    @Test
    fun ensureInitializedDoesNotUndoAProfileSwitch() = runBlocking {
        val (manager, repository) = managerWithActiveProfile(Profile(id = "kids", name = "Kids"))
        manager.ensureInitialized()

        // The UI switched profiles (setCurrentProfileId); a later ensureInitialized
        // must keep the switch instead of re-reading the store.
        manager.setCurrentProfileId("grownups")
        manager.ensureInitialized()

        assertThat(manager.getProfileIdSync()).isEqualTo("grownups")
        coVerify(exactly = 1) { repository.getActiveProfile() }
    }

    @Test
    fun concurrentEnsureCallsShareOneInitialization() = runBlocking {
        val (manager, repository) = managerWithActiveProfile(Profile(id = "kids", name = "Kids"))
        // TraktSyncWorker and CloudSyncWorker can wake the process in parallel.
        val jobs = List(4) { launch(Dispatchers.IO) { manager.ensureInitialized() } }
        jobs.forEach { it.join() }

        assertThat(manager.getProfileIdSync()).isEqualTo("kids")
        coVerify(exactly = 1) { repository.getActiveProfile() }
    }

    @Test
    fun missingActiveProfileFallsBackToTheDefaultProfile() = runBlocking {
        val (manager, repository) = managerWithActiveProfile(null)

        manager.ensureInitialized()

        assertThat(manager.getProfileIdSync()).isEqualTo("default")
        coVerify(exactly = 1) { repository.getActiveProfile() }
    }
}
