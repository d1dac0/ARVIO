package com.arflix.tv.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TraktCredentialsTest {
    @Test
    fun `a client id alone is usable for current Trakt apps`() {
        assertThat(isUsableTraktCredentials("a".repeat(64), "")).isTrue()
        assertThat(isUsableTraktCredentials("a".repeat(64), null)).isTrue()
    }

    @Test
    fun `an older app with a secret is still usable`() {
        assertThat(
            isUsableTraktCredentials(
                "a".repeat(64),
                "b".repeat(64)
            )
        ).isTrue()
    }

    @Test
    fun `placeholders are rejected`() {
        assertThat(isUsableTraktCredentials("your-trakt-client-id", "your-trakt-client-secret")).isFalse()
    }

    @Test
    fun `missing or truncated client ids are rejected`() {
        assertThat(isUsableTraktCredentials("", "b".repeat(64))).isFalse()
        assertThat(isUsableTraktCredentials("abc", "def")).isFalse()
        assertThat(isUsableTraktCredentials(null, null)).isFalse()
    }

    @Test
    fun `a partially pasted secret is rejected instead of silently dropped`() {
        assertThat(isUsableTraktCredentials("a".repeat(64), "short")).isFalse()
    }
}
