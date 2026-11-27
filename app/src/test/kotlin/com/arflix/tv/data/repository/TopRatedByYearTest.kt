package com.arflix.tv.data.repository

import com.arflix.tv.data.model.MediaItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopRatedByYearTest {

    @Test
    fun defaultLimit_matchesTrendingMoviesCapacity() {
        assertThat(TOP_RATED_BY_YEAR_ITEM_LIMIT).isEqualTo(40)
    }

    @Test
    fun rankTopRatedMoviesByYear_filtersStrictYearAndMinimumImdbVotes() {
        val candidates = listOf(
            candidate(id = 1, year = 2026, rating = "8.4", votes = 10_000),
            candidate(id = 2, year = 2026, rating = "9.8", votes = 9_999),
            candidate(id = 3, year = 2025, rating = "9.9", votes = 500_000)
        )

        val result = rankTopRatedMoviesByYear(candidates, selectedYear = 2026)

        assertThat(result.map { it.id }).containsExactly(1)
        assertThat(result.single().imdbRating).isEqualTo("8.4")
    }

    @Test
    fun rankTopRatedMoviesByYear_ordersByRatingThenVotes() {
        val candidates = listOf(
            candidate(id = 1, year = 2026, rating = "8.1", votes = 900_000),
            candidate(id = 2, year = 2026, rating = "8.8", votes = 20_000),
            candidate(id = 3, year = 2026, rating = "8.8", votes = 80_000)
        )

        val result = rankTopRatedMoviesByYear(candidates, selectedYear = 2026)

        assertThat(result.map { it.id }).containsExactly(3, 2, 1).inOrder()
    }

    private fun candidate(
        id: Int,
        year: Int,
        rating: String,
        votes: Long
    ) = TopRatedMovieCandidate(
        item = MediaItem(id = id, title = "Movie $id", year = year.toString()),
        score = ImdbTitleScore(rating = rating, votes = votes)
    )
}
