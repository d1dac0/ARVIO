package com.arflix.tv.ui.screens.player

/** How long the "Press Back again to exit" confirmation stays armed. */
internal const val PLAYER_EXIT_CONFIRM_WINDOW_MS = 2_000L

/**
 * On API 33+ a Back press can reach both the key handler and the back dispatcher. Two
 * callbacks for one physical press must not count as the user's two presses.
 */
internal const val PLAYER_BACK_DUPLICATE_WINDOW_MS = 300L

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

/** True for the second delivery of the same physical Back press. */
internal fun isDuplicateBackEvent(
    lastHandledAtMs: Long,
    nowMs: Long,
    windowMs: Long = PLAYER_BACK_DUPLICATE_WINDOW_MS
): Boolean =
    lastHandledAtMs > 0L && nowMs >= lastHandledAtMs && nowMs - lastHandledAtMs <= windowMs
