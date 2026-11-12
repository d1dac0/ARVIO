package com.arflix.tv.ui.screens.player

import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.data.repository.PlaybackTrackMemory
import com.arflix.tv.util.LanguageMap

/**
 * The audio track in [tracks] that matches [memory], or null.
 *
 * The same release usually exposes the same tracks, so an exact language + label match wins.
 * A language-only match is next (labels are less stable across remuxes), then the saved
 * index only when the source still has the same track layout.
 */
internal fun findRememberedAudioTrackIndex(
    tracks: List<AudioTrackInfo>,
    memory: PlaybackTrackMemory?
): Int? {
    memory ?: return null
    if (!memory.hasAudio || tracks.isEmpty()) return null
    val language = memory.audioLanguage?.trim().orEmpty()
    val label = memory.audioLabel?.trim().orEmpty()

    fun languageMatches(track: AudioTrackInfo) =
        language.isNotBlank() && sameTrackLanguage(track.language, language)

    fun labelMatches(track: AudioTrackInfo) =
        label.isNotBlank() && track.label?.trim().equals(label, ignoreCase = true)

    val exact = tracks.indexOfFirst { languageMatches(it) && labelMatches(it) }
    if (exact >= 0) return exact

    if (language.isNotBlank()) {
        val byLanguage = tracks.indexOfFirst(::languageMatches)
        if (byLanguage >= 0) return byLanguage
    }
    if (label.isNotBlank()) {
        val byLabel = tracks.indexOfFirst(::labelMatches)
        if (byLabel >= 0) return byLabel
    }
    return memory.audioIndex?.takeIf { it in tracks.indices }
}

/** The subtitle in [subtitles] that matches the remembered pick, strongest identity first. */
internal fun findRememberedSubtitle(
    memory: PlaybackTrackMemory?,
    subtitles: List<Subtitle>
): Subtitle? {
    memory ?: return null
    if (!memory.hasSubtitle || memory.subtitlesOff) return null

    memory.subtitleKey?.takeIf { it.isNotBlank() }?.let { key ->
        subtitles.firstOrNull { SubtitleSelectionPolicy.key(it) == key }?.let { return it }
    }
    memory.subtitleUrl?.takeIf { it.isNotBlank() }?.let { url ->
        subtitles.firstOrNull { it.url == url }?.let { return it }
    }
    memory.subtitleLabel?.takeIf { it.isNotBlank() }?.let { label ->
        subtitles.firstOrNull { it.label.trim().equals(label, ignoreCase = true) }?.let { return it }
    }
    val language = memory.subtitleLang?.trim().orEmpty()
    if (language.isNotBlank()) {
        return subtitles.firstOrNull {
            it.isEmbedded && !it.isForced && sameTrackLanguage(it.lang, language)
        }
    }
    return null
}

/** ISO tags and old three-letter containers codes refer to the same language. */
internal fun sameTrackLanguage(first: String?, second: String?): Boolean {
    if (first.isNullOrBlank() || second.isNullOrBlank()) return false
    if (first.trim().equals(second.trim(), ignoreCase = true)) return true
    val firstCode = first.trim().lowercase()
    val secondCode = second.trim().lowercase()
    val firstName = LanguageMap.getLanguageName(firstCode)
    val secondName = LanguageMap.getLanguageName(secondCode)
    return !firstName.equals(firstCode.uppercase(), ignoreCase = true) &&
        !secondName.equals(secondCode.uppercase(), ignoreCase = true) &&
        firstName.equals(secondName, ignoreCase = true)
}
