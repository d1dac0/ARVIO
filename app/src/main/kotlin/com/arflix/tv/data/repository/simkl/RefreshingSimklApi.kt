package com.arflix.tv.data.repository.simkl

import com.arflix.tv.data.api.*
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Retries a rejected V2 token after the HTTP call has released its dispatcher slot. */
internal class RefreshingSimklApi(
    private val api: SimklApi,
    private val grants: SimklListsRepository
) : SimklApi by api {
    private suspend fun <T> retryUnauthorized(auth: String, request: suspend (String) -> T): T {
        val token = auth.removePrefix("Bearer ")
        if (!token.startsWith("simkl_at_")) return request(auth)

        val owner = grants.refreshIdentityForAccessToken(token)
            ?: return request(auth)
        suspend fun retry(): T {
            val refreshed = grants.refreshRejectedToken(token, owner)
            return request("Bearer $refreshed")
        }
        val result = try {
            request(auth)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            error.response()?.errorBody()?.close()
            return retry()
        }
        if (result !is Response<*> || result.code() != 401) return result
        result.errorBody()?.close()
        return retry()
    }

    override suspend fun scrobbleStart(
        auth: String,
        clientId: String,
        body: SimklScrobbleBody,
        minProgress: Int
    ): Response<SimklScrobbleResponse> = retryUnauthorized(auth) { header ->
        api.scrobbleStart(header, clientId, body, minProgress)
    }

    override suspend fun scrobblePause(
        auth: String,
        clientId: String,
        body: SimklScrobbleBody
    ): Response<SimklScrobbleResponse> = retryUnauthorized(auth) { header ->
        api.scrobblePause(header, clientId, body)
    }

    override suspend fun scrobbleStop(
        auth: String,
        clientId: String,
        body: SimklScrobbleBody,
        minProgress: Int
    ): Response<SimklScrobbleResponse> = retryUnauthorized(auth) { header ->
        api.scrobbleStop(header, clientId, body, minProgress)
    }

    override suspend fun getUserSettings(auth: String, clientId: String): SimklUserSettingsResponse =
        retryUnauthorized(auth) { header -> api.getUserSettings(header, clientId) }

    override suspend fun getActivities(auth: String, clientId: String): SimklActivitiesResponse =
        retryUnauthorized(auth) { header -> api.getActivities(header, clientId) }

    override suspend fun getAllItems(
        auth: String,
        clientId: String,
        type: String,
        status: String,
        dateFrom: String?,
        extended: String,
        episodeWatchedAt: String,
        includeAllEpisodes: String,
        nextWatchInfo: String
    ): JsonElement = retryUnauthorized(auth) { header ->
        api.getAllItems(header, clientId, type, status, dateFrom, extended, episodeWatchedAt, includeAllEpisodes, nextWatchInfo)
    }

    override suspend fun getAllItemIds(auth: String, clientId: String, type: String, extended: String): JsonElement =
        retryUnauthorized(auth) { header -> api.getAllItemIds(header, clientId, type, extended) }

    override suspend fun getAllItemsDelta(
        auth: String,
        clientId: String,
        dateFrom: String,
        extended: String,
        episodeWatchedAt: String,
        includeAllEpisodes: String,
        nextWatchInfo: String
    ): JsonElement = retryUnauthorized(auth) { header ->
        api.getAllItemsDelta(header, clientId, dateFrom, extended, episodeWatchedAt, includeAllEpisodes, nextWatchInfo)
    }

    override suspend fun getPlayback(auth: String, clientId: String): List<SimklPlaybackItem> =
        retryUnauthorized(auth) { header -> api.getPlayback(header, clientId) }

    override suspend fun getPlaybackDelta(auth: String, clientId: String, dateFrom: String): List<SimklPlaybackItem> =
        retryUnauthorized(auth) { header -> api.getPlaybackDelta(header, clientId, dateFrom) }

    override suspend fun getPlaybackBefore(auth: String, clientId: String, dateTo: String): List<SimklPlaybackItem> =
        retryUnauthorized(auth) { header -> api.getPlaybackBefore(header, clientId, dateTo) }

    override suspend fun deletePlayback(auth: String, clientId: String, id: Long): Response<ResponseBody> =
        retryUnauthorized(auth) { header -> api.deletePlayback(header, clientId, id) }

    override suspend fun addToHistory(
        auth: String,
        clientId: String,
        body: SimklSyncHistoryBody,
        allowRewatch: String?
    ): Response<ResponseBody> = retryUnauthorized(auth) { header ->
        api.addToHistory(header, clientId, body, allowRewatch)
    }

    override suspend fun removeFromHistory(
        auth: String,
        clientId: String,
        body: SimklSyncHistoryBody
    ): Response<ResponseBody> = retryUnauthorized(auth) { header ->
        api.removeFromHistory(header, clientId, body)
    }

    override suspend fun addToList(auth: String, clientId: String, body: SimklAddToListBody): Response<ResponseBody> =
        retryUnauthorized(auth) { header -> api.addToList(header, clientId, body) }
}
