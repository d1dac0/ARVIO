package com.arflix.tv.ui.screens.player

/** How long the "Press Back again to exit" confirmation stays armed. */
internal const val PLAYER_EXIT_CONFIRM_WINDOW_MS = 2_000L

/**
 * A Back press leaves the player only when the previous Back press was part of the same
 * confirmation window. The first press arms the exit and shows the hint; anything else
 * (another key, a panel, another Back outside the window) starts over.
 */
internal fun shouldExitPlayerOnBack(
    lastBackAtMs: Long,
    nowMs: Long,
    windowMs: Long = PLAYER_EXIT_CONFIRM_WINDOW_MS
): Boolean = lastBackAtMs > 0L && nowMs >= lastBackAtMs && nowMs - lastBackAtMs <= windowMs
