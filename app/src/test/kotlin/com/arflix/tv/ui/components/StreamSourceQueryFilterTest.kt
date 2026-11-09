package com.arflix.tv.ui.components

import com.arflix.tv.data.model.StreamBehaviorHints
import com.arflix.tv.data.model.StreamSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamSourceQueryFilterTest {

    private fun torrent(
        filename: String? = null,
        description: String? = null,
        addonTitle: String? = null,
        quality: String = "1080p",
        size: String = "4 GB"
    ) = StreamSource(
        source = "Torrentio",
        addonName = "Torrentio",
        addonId = "torrentio",
        quality = quality,
        size = size,
        description = description,
        addonTitle = addonTitle,
        behaviorHints = filename?.let { StreamBehaviorHints(filename = it) },
    )

    @Test
    fun emptyQueryKeepsEverySource() {
        val sources = listOf(
            torrent(filename = "Movie.2024.1080p.WEB-DL.LATINO.mkv"),
            torrent(filename = "Movie.2024.1080p.WEB-DL.mkv"),
        )

        assertThat(sources.filter { sourceMatchesQuery(it, "") }).hasSize(2)
        assertThat(sources.filter { sourceMatchesQuery(it, "   ") }).hasSize(2)
    }

    @Test
    fun queryMatchesInsideTheReleaseFilenameIgnoringCase() {
        val latino = torrent(filename = "Movie.2024.1080p.WEB-DL.LATINO.mkv")
        val plain = torrent(filename = "Movie.2024.1080p.WEB-DL.mkv")

        assertThat(sourceMatchesQuery(latino, "lat")).isTrue()
        assertThat(sourceMatchesQuery(latino, "LAT")).isTrue()
        assertThat(sourceMatchesQuery(plain, "lat")).isFalse()
    }

    @Test
    fun queryAlsoReachesAddonLabelsAndDescriptions() {
        val described = torrent(
            filename = "Movie.2024.1080p.mkv",
            description = "Audio: Español Latino",
        )
        val titled = torrent(addonTitle = "Movie.2024.LAT.DUAL")

        assertThat(sourceMatchesQuery(described, "latino")).isTrue()
        assertThat(sourceMatchesQuery(titled, "lat")).isTrue()
    }

    @Test
    fun severalTokensMustAllMatch() {
        val match = torrent(filename = "Movie.2024.1080p.LATINO.mkv", quality = "1080p")
        val wrongQuality = torrent(filename = "Movie.2024.720p.LATINO.mkv", quality = "720p")

        assertThat(sourceMatchesQuery(match, "lat 1080")).isTrue()
        assertThat(sourceMatchesQuery(wrongQuality, "lat 1080")).isFalse()
        assertThat(sourceMatchesQuery(match, "lat 2160")).isFalse()
    }

    @Test
    fun queryCanStillNarrowByQualityAndSize() {
        val source = torrent(filename = "Movie.2024.mkv", quality = "4K", size = "20 GB")

        assertThat(sourceMatchesQuery(source, "4k")).isTrue()
        assertThat(sourceMatchesQuery(source, "20 gb")).isTrue()
        assertThat(sourceMatchesQuery(source, "torrentio")).isTrue()
    }

    @Test
    fun unmatchedQueryIsReportedAsNoSources() {
        val source = torrent(filename = "Movie.2024.1080p.mkv")

        assertThat(sourceMatchesQuery(source, "lat")).isFalse()
    }
}
