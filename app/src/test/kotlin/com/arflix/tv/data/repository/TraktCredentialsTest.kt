package com.arflix.tv.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TraktCredentialsTest {
    @Test
    fun `a complete pair is usable`() {
        assertThat(
            isUsableTraktCredentialPair(
                "a".repeat(64),
                "b".repeat(64)
            )
        ).isTrue()
    }

    @Test
    fun `placeholders are rejected`() {
        assertThat(isUsableTraktCredentialPair("your-trakt-client-id", "your-trakt-client-secret")).isFalse()
    }

    @Test
    fun `missing or truncated values are rejected`() {
        assertThat(isUsableTraktCredentialPair("", "b".repeat(64))).isFalse()
        assertThat(isUsableTraktCredentialPair("a".repeat(64), "   ")).isFalse()
        assertThat(isUsableTraktCredentialPair("abc", "def")).isFalse()
        assertThat(isUsableTraktCredentialPair(null, null)).isFalse()
    }
}
