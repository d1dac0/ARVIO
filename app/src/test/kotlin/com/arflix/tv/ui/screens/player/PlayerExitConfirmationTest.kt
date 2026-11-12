package com.arflix.tv.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerExitConfirmationTest {
    @Test
    fun `a fresh press is not an exit`() {
        assertThat(shouldExitPlayerOnBack(lastBackAtMs = 0L, nowMs = 10_000L)).isFalse()
    }

    @Test
    fun `second press inside the window exits`() {
        assertThat(shouldExitPlayerOnBack(lastBackAtMs = 10_000L, nowMs = 10_001L)).isTrue()
        assertThat(
            shouldExitPlayerOnBack(
                lastBackAtMs = 10_000L,
                nowMs = 10_000L + PLAYER_EXIT_CONFIRM_WINDOW_MS
            )
        ).isTrue()
    }

    @Test
    fun `press after the window only re-arms`() {
        assertThat(
            shouldExitPlayerOnBack(
                lastBackAtMs = 10_000L,
                nowMs = 10_000L + PLAYER_EXIT_CONFIRM_WINDOW_MS + 1L
            )
        ).isFalse()
    }

    @Test
    fun `a clock that jumps backwards does not exit`() {
        assertThat(shouldExitPlayerOnBack(lastBackAtMs = 10_000L, nowMs = 9_000L)).isFalse()
    }
}
