package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.util.Constants

/**
 * Trakt API app credentials, resolved at runtime.
 *
 * Official release builds bake their own Trakt API app into BuildConfig. Fork or
 * contributor builds have none, and Trakt's device flow cannot exchange a token without a
 * client secret. Rather than leaving Connect dead, Settings lets the user paste the Client
 * ID and Client Secret of their own Trakt API app; this object holds those values for the
 * process and falls back to the build values when nothing was saved.
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

    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    /** True when the values came from Settings instead of the APK's BuildConfig. */
    val isUserSupplied: Boolean
        get() = storedClientId != null && storedClientSecret != null

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_CLIENT_ID, null)
        val secret = prefs.getString(KEY_CLIENT_SECRET, null)
        if (isUsableTraktCredentialPair(id, secret)) {
            storedClientId = id!!.trim()
            storedClientSecret = secret!!.trim()
        }
    }

    fun save(context: Context, clientId: String, clientSecret: String): Boolean {
        val id = clientId.trim()
        val secret = clientSecret.trim()
        if (!isUsableTraktCredentialPair(id, secret)) return false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, id)
            .putString(KEY_CLIENT_SECRET, secret)
            .apply()
        storedClientId = id
        storedClientSecret = secret
        return true
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        storedClientId = null
        storedClientSecret = null
    }
}

internal fun isUsableTraktCredentialPair(clientId: String?, clientSecret: String?): Boolean {
    val id = clientId?.trim().orEmpty()
    val secret = clientSecret?.trim().orEmpty()
    // Trakt's own values are long hex strings; a short or placeholder pair only produces a
    // confusing OAuth 400 later, so reject it at the entry point.
    return id.length >= 16 && secret.length >= 16 &&
        !id.startsWith("your-", ignoreCase = true) &&
        !secret.startsWith("your-", ignoreCase = true)
}
