package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.data.model.MediaType
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The audio and subtitle tracks a profile last picked for one movie or episode.
 *
 * Separate from [RecentPlayedSource] on purpose: source identity is about the torrent/addon,
 * while track choice is about the title. Keeping them apart lets a title still restore its
 * language choices when the remembered source is gone from the new list.
 *
 * Device-local and profile-scoped. Current Trakt/TorBox apps re-fetch sources; this record
 * is what makes a resume feel like the same viewing session.
 */
data class PlaybackTrackMemory(
    val audioLanguage: String? = null,
    val audioLabel: String? = null,
    val audioIndex: Int? = null,
    val subtitleKey: String? = null,
    val subtitleLang: String? = null,
    val subtitleLabel: String? = null,
    val subtitleUrl: String? = null,
    val subtitlesOff: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    val hasAudio: Boolean
        get() = audioIndex != null || !audioLanguage.isNullOrBlank() || !audioLabel.isNullOrBlank()

    val hasSubtitle: Boolean
        get() = subtitlesOff || !subtitleKey.isNullOrBlank() || !subtitleUrl.isNullOrBlank() ||
            !subtitleLabel.isNullOrBlank() || !subtitleLang.isNullOrBlank()
}

@Singleton
class PlaybackTrackMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
) {
    private val gson = Gson()

    fun get(mediaType: MediaType, mediaId: Int, season: Int?, episode: Int?): PlaybackTrackMemory? {
        if (mediaId <= 0) return null
        val raw = prefs().getString(key(mediaType, mediaId, season, episode), null) ?: return null
        return runCatching { gson.fromJson(raw, PlaybackTrackMemory::class.java) }.getOrNull()
    }

    fun saveAudio(
        mediaType: MediaType,
        mediaId: Int,
        season: Int?,
        episode: Int?,
        language: String?,
        label: String?,
        index: Int?,
    ) {
        update(mediaType, mediaId, season, episode) {
            it.copy(
                audioLanguage = language?.trim()?.takeIf { it.isNotBlank() },
                audioLabel = label?.trim()?.takeIf { it.isNotBlank() },
                audioIndex = index,
            )
        }
    }

    fun saveSubtitle(
        mediaType: MediaType,
        mediaId: Int,
        season: Int?,
        episode: Int?,
        key: String?,
        language: String?,
        label: String?,
        url: String?,
    ) {
        update(mediaType, mediaId, season, episode) {
            it.copy(
                subtitleKey = key?.trim()?.takeIf { value -> value.isNotBlank() },
                subtitleLang = language?.trim()?.takeIf { it.isNotBlank() },
                subtitleLabel = label?.trim()?.takeIf { it.isNotBlank() },
                subtitleUrl = url?.trim()?.takeIf { it.isNotBlank() },
                subtitlesOff = false,
            )
        }
    }

    fun saveSubtitlesOff(mediaType: MediaType, mediaId: Int, season: Int?, episode: Int?) {
        update(mediaType, mediaId, season, episode) {
            it.copy(
                subtitleKey = null,
                subtitleLang = null,
                subtitleLabel = null,
                subtitleUrl = null,
                subtitlesOff = true,
            )
        }
    }

    private fun update(
        mediaType: MediaType,
        mediaId: Int,
        season: Int?,
        episode: Int?,
        transform: (PlaybackTrackMemory) -> PlaybackTrackMemory,
    ) {
        if (mediaId <= 0) return
        val current = get(mediaType, mediaId, season, episode) ?: PlaybackTrackMemory()
        val next = transform(current).copy(updatedAtMs = System.currentTimeMillis())
        prefs().edit().putString(key(mediaType, mediaId, season, episode), gson.toJson(next)).apply()
    }

    private fun key(mediaType: MediaType, mediaId: Int, season: Int?, episode: Int?): String {
        val profileId = profileManager.getProfileIdSync().ifBlank { "default" }
        val title = if (season == null || episode == null) {
            "${mediaType.name}:$mediaId"
        } else {
            "${mediaType.name}:$mediaId:$season:$episode"
        }
        return "$profileId|$title"
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "playback_track_memory"
    }
}
