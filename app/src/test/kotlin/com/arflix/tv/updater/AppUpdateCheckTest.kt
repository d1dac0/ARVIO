package com.arflix.tv.updater

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppUpdateCheckTest {
    @Test
    fun `first check is always due`() {
        assertThat(isUpdateCheckDue(lastCheckAtMs = 0L, nowMs = 1_000L)).isTrue()
    }

    @Test
    fun `check inside the interval is skipped`() {
        assertThat(
            isUpdateCheckDue(
                lastCheckAtMs = 10_000L,
                nowMs = 10_000L + APP_UPDATE_CHECK_INTERVAL_MS - 1L
            )
        ).isFalse()
    }

    @Test
    fun `check after the interval is due`() {
        assertThat(
            isUpdateCheckDue(
                lastCheckAtMs = 10_000L,
                nowMs = 10_000L + APP_UPDATE_CHECK_INTERVAL_MS
            )
        ).isTrue()
    }

    @Test
    fun `a clock that moved backwards forces a check`() {
        assertThat(isUpdateCheckDue(lastCheckAtMs = 10_000L, nowMs = 9_000L)).isTrue()
    }
}
