package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.util.Constants

/**
 * Trakt API app credentials, resolved at runtime.
 *
 * Official release builds bake their own Trakt API app into BuildConfig. Fork or
 * contributor builds have none, so Connect would otherwise be dead. Settings lets the user
 * paste the Client ID of their own Trakt API app. Current Trakt apps do not issue a Client
 * Secret (device auth and refresh accept its omission); the optional secret is only kept
 * for older apps that still have one. This object holds the values for the process and
 * falls back to the build values when nothing was saved.
 *
 * Callers must read [clientId] / [clientSecret] on demand: the values are loaded in
 * Application.attachBaseContext before Hilt injects the repositories, but a getter keeps
 * that ordering from mattering.
 */
object TraktCredentials {
    private const val PREFS_NAME = "trakt_api_credentials"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_SECRET = "client_secret"

    @Volatile
    private var storedClientId: String? = null

    @Volatile
    private var storedClientSecret: String? = null

    /** Build credentials first only when nothing user-supplied exists. */
    val clientId: String
        get() = storedClientId ?: Constants.TRAKT_CLIENT_ID

    val clientSecret: String
        get() = storedClientSecret ?: Constants.TRAKT_CLIENT_SECRET

    /** Trakt's current developer apps issue only a Client ID; the secret is optional. */
    val isConfigured: Boolean
        get() = clientId.isNotBlank()

    /** True when the Client ID came from Settings instead of the APK's BuildConfig. */
    val isUserSupplied: Boolean
        get() = storedClientId != null

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_CLIENT_ID, null)
        val secret = prefs.getString(KEY_CLIENT_SECRET, null)
        if (isUsableTraktClientId(id)) {
            storedClientId = id!!.trim()
            storedClientSecret = secret?.trim()?.takeIf { isUsableTraktClientSecret(it) }
        }
    }

    fun save(context: Context, clientId: String, clientSecret: String): Boolean {
        val id = clientId.trim()
        val secret = clientSecret.trim()
        if (!isUsableTraktCredentials(id, secret)) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, id)
            .apply {
                if (secret.isBlank()) remove(KEY_CLIENT_SECRET) else putString(KEY_CLIENT_SECRET, secret)
            }
            .apply()
        storedClientId = id
        storedClientSecret = secret.takeIf { it.isNotBlank() }
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        storedClientId = null
        storedClientSecret = null
    }
}

internal fun isUsableTraktCredentials(clientId: String?, clientSecret: String?): Boolean =
    isUsableTraktClientId(clientId) && isUsableTraktClientSecret(clientSecret)

internal fun isUsableTraktClientId(clientId: String?): Boolean {
    val id = clientId?.trim().orEmpty()
    // Trakt's own values are long hex strings; a short or placeholder id only produces a
    // confusing OAuth 400 later, so reject it at the entry point.
    return id.length >= 16 && !id.startsWith("your-", ignoreCase = true)
}

internal fun isUsableTraktClientSecret(clientSecret: String?): Boolean {
    val secret = clientSecret?.trim().orEmpty()
    // Current Trakt apps have no secret. Older apps do, and when one is supplied it must be
    // complete: a half-pasted value would break token exchange in a way the UI cannot explain.
    return secret.isEmpty() ||
        (secret.length >= 16 && !secret.startsWith("your-", ignoreCase = true))
}

/** Reaches the sync status when a stored session exists but the API app was never configured. */
internal const val TRAKT_CREDENTIALS_MISSING_MESSAGE =
    "Trakt API credentials are missing. Open Settings → Accounts → Trakt.tv and connect again."
