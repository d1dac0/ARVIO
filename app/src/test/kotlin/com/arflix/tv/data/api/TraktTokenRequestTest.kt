package com.arflix.tv.data.api

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class TraktTokenRequestTest {
    private val gson = Gson()

    @Test
    fun `device token omits a missing secret`() {
        val json = gson.toJson(TokenPollRequest(code = "device-code", clientId = "client-id"))
        assertThat(json).contains("\"client_id\":\"client-id\"")
        assertThat(json).doesNotContain("client_secret")
    }

    @Test
    fun `device token sends a secret when an older app has one`() {
        val json = gson.toJson(
            TokenPollRequest(code = "device-code", clientId = "client-id", clientSecret = "secret")
        )
        assertThat(json).contains("\"client_secret\":\"secret\"")
    }

    @Test
    fun `refresh omits a missing secret`() {
        val json = gson.toJson(RefreshTokenRequest(refreshToken = "refresh", clientId = "client-id"))
        assertThat(json).doesNotContain("client_secret")
        assertThat(json).contains("\"grant_type\":\"refresh_token\"")
    }
}
