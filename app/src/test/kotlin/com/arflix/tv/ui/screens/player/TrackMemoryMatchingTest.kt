package com.arflix.tv.ui.screens.player

import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.data.repository.PlaybackTrackMemory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackMemoryMatchingTest {

    @Test
    fun `audio prefers the exact language and label`() {
        val tracks = listOf(
            audio(index = 0, language = "en", label = "English 5.1"),
            audio(index = 1, language = "pl", label = "Lektor PL"),
            audio(index = 2, language = "pl", label = "Dubbing PL"),
        )

        assertThat(
            findRememberedAudioTrackIndex(
                tracks,
                PlaybackTrackMemory(audioLanguage = "pl", audioLabel = "Dubbing PL")
            )
        ).isEqualTo(2)
    }

    @Test
    fun `audio matches old three-letter language tags`() {
        val tracks = listOf(audio(index = 0, language = "en"), audio(index = 1, language = "pol"))

        assertThat(
            findRememberedAudioTrackIndex(tracks, PlaybackTrackMemory(audioLanguage = "pl"))
        ).isEqualTo(1)
    }

    @Test
    fun `audio falls back to the saved index when tags are missing`() {
        val tracks = listOf(audio(index = 0, language = null), audio(index = 1, language = null))

        assertThat(
            findRememberedAudioTrackIndex(tracks, PlaybackTrackMemory(audioIndex = 1))
        ).isEqualTo(1)
    }

    @Test
    fun `subtitle matches the saved provider and id first`() {
        val subtitles = listOf(
            subtitle(id = "a", provider = "opensubtitles", label = "English"),
            subtitle(id = "b", provider = "opensubtitles", label = "English SDH"),
        )

        assertThat(
            findRememberedSubtitle(
                PlaybackTrackMemory(subtitleKey = "opensubtitles|b"),
                subtitles
            )
        ).isSameInstanceAs(subtitles[1])
    }

    @Test
    fun `subtitle falls back to label and then embedded language`() {
        val subtitles = listOf(
            subtitle(id = "a", provider = "addon", label = "Custom English", lang = "en"),
            subtitle(id = "", provider = "embedded", label = "English", lang = "eng", embedded = true),
        )

        assertThat(
            findRememberedSubtitle(PlaybackTrackMemory(subtitleLabel = "Custom English"), subtitles)
        ).isSameInstanceAs(subtitles[0])
        assertThat(
            findRememberedSubtitle(PlaybackTrackMemory(subtitleLang = "en"), subtitles)
        ).isSameInstanceAs(subtitles[1])
    }

    @Test
    fun `off and empty memory restore nothing`() {
        val subtitles = listOf(subtitle(id = "a", provider = "addon", label = "English"))

        assertThat(
            findRememberedSubtitle(PlaybackTrackMemory(subtitlesOff = true), subtitles)
        ).isNull()
        assertThat(findRememberedSubtitle(null, subtitles)).isNull()
        assertThat(findRememberedSubtitle(PlaybackTrackMemory(), subtitles)).isNull()
        assertThat(
            findRememberedAudioTrackIndex(emptyList(), PlaybackTrackMemory(audioLanguage = "en"))
        ).isNull()
    }

    private fun audio(index: Int, language: String?, label: String? = null) = AudioTrackInfo(
        index = index,
        groupIndex = 0,
        trackIndex = index,
        language = language,
        label = label,
        channelCount = 2,
        sampleRate = 48_000,
        codec = "aac",
    )

    private fun subtitle(
        id: String,
        provider: String,
        label: String,
        lang: String = "en",
        embedded: Boolean = false,
    ) = Subtitle(
        id = id,
        url = "https://subs/$id",
        lang = lang,
        label = label,
        provider = provider,
        isEmbedded = embedded,
    )
}
