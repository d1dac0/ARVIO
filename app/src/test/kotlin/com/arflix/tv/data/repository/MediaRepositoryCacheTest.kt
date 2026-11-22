package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbCastMember
import com.arflix.tv.data.api.TmdbCreditsResponse
import com.arflix.tv.data.api.TmdbListResponse
import com.arflix.tv.data.api.TmdbMediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.Executors

class MediaRepositoryCacheTest {
    @Test
    fun concurrentCastAndSimilarRequestsRetainEntriesUntilMemoryTrim() = runBlocking {
        val api = mockk<TmdbApi>()
        coEvery { api.getCredits("movie", any(), any(), any()) } coAnswers {
            TmdbCreditsResponse(cast = listOf(TmdbCastMember(id = secondArg(), name = "Actor")))
        }
        coEvery { api.getRecommendations("movie", any(), any(), any()) } coAnswers {
            TmdbListResponse(results = listOf(TmdbMediaItem(id = secondArg(), title = "Similar movie")))
        }
        val repository = MediaRepository(mockk(relaxed = true), api, mockk(), mockk(), mockk(), mockk(), mockk())

        Executors.newFixedThreadPool(8).asCoroutineDispatcher().use { threads ->
            repeat(2) {
                (1..16).map { id ->
                    async(threads) {
                        assertThat(repository.getCast(MediaType.MOVIE, id).single().id).isEqualTo(id)
                        assertThat(repository.getSimilar(MediaType.MOVIE, id).single().id).isEqualTo(id)
                    }
                }.awaitAll()
            }
        }
        coVerify(exactly = 16) { api.getCredits("movie", any(), any(), any()) }
        coVerify(exactly = 16) { api.getRecommendations("movie", any(), any(), any()) }

        repository.trimMemory()
        repository.getCast(MediaType.MOVIE, 1)
        repository.getSimilar(MediaType.MOVIE, 1)
        coVerify(exactly = 2) { api.getCredits("movie", 1, any(), any()) }
        coVerify(exactly = 2) { api.getRecommendations("movie", 1, any(), any()) }
    }
}
