package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopRatedByYearUiTest {
    @Test
    fun yearCandidateMovesWithoutApplyingRepositorySelection() {
        assertThat(moveTopRatedYearCandidate(2026, -1, 2026)).isEqualTo(2025)
        assertThat(moveTopRatedYearCandidate(2025, 1, 2026)).isEqualTo(2026)
        assertThat(moveTopRatedYearCandidate(2026, 1, 2026)).isEqualTo(2026)
        assertThat(moveTopRatedYearCandidate(1890, -1, 2026)).isEqualTo(1890)
    }


    @Test
    fun topRatedYearTabs_staysInsideSupportedRange() {
        assertThat(topRatedYearTabs(selectedYear = 2026, currentYear = 2026))
            .containsExactly(2025, 2026).inOrder()
        assertThat(topRatedYearTabs(selectedYear = 1890, currentYear = 2026))
            .containsExactly(1890, 1891).inOrder()
    }

    @Test
    fun withTopRatedByYearRow_placesOneStableRowAfterTrending() {
        val categories = listOf(
            Category("continue_watching", "Continue Watching", emptyList()),
            Category("trending_movies", "Trending Movies", emptyList()),
            Category("trending_tv", "Trending Shows", emptyList()),
            Category("genres", "Genres", emptyList())
        )
        val movie = MediaItem(id = 42, title = "The Answer", year = "2026")

        val first = withTopRatedByYearRow(categories, listOf(movie))
        val replaced = withTopRatedByYearRow(first, listOf(movie.copy(title = "Updated")))

        assertThat(replaced.map { it.id }).containsExactly(
            "continue_watching",
            "trending_movies",
            "trending_tv",
            TOP_RATED_BY_YEAR_CATEGORY_ID,
            "genres"
        ).inOrder()
        assertThat(replaced.count { it.id == TOP_RATED_BY_YEAR_CATEGORY_ID }).isEqualTo(1)
        assertThat(replaced.first { it.id == TOP_RATED_BY_YEAR_CATEGORY_ID }.items.single().title)
            .isEqualTo("Updated")
    }

    @Test
    fun withWatchedBadges_marksTopRatedByYearCards() {
        val state = HomeUiState(
            topRatedByYearItems = listOf(MediaItem(id = 42, title = "Watched", year = "2026"))
        )

        val marked = state.withWatchedBadges(watchedMovies = setOf(42), startedShows = emptySet())

        assertThat(marked.topRatedByYearItems.single().isWatched).isTrue()
    }
}
