package com.arflix.tv.updater

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** How often a foregrounded app may hit the GitHub releases API on its own. */
internal const val APP_UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000L

/** A failed check (offline TV, GitHub hiccup) may retry before the full interval. */
internal const val APP_UPDATE_CHECK_RETRY_MS = 15 * 60 * 1000L

internal fun isUpdateCheckDue(
    lastCheckAtMs: Long,
    nowMs: Long,
    minIntervalMs: Long = APP_UPDATE_CHECK_INTERVAL_MS
): Boolean = lastCheckAtMs <= 0L || nowMs < lastCheckAtMs || nowMs - lastCheckAtMs >= minIntervalMs

/**
 * One place for the app's unattended update checks.
 *
 * The check used to live only in Home: it ran once per process, 10 s after Home was first
 * composed, so a TV left in the background never noticed a release published while it was
 * running. This throttles a check on every foreground and publishes the result through
 * [UpdateStatusManager], which Home observes to open the popup.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val repository: AppUpdateRepository,
    private val preferences: UpdatePreferences,
    private val statusManager: UpdateStatusManager
) {
    suspend fun checkIfDue(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!repository.supportsSelfUpdate()) return false
        // Never replace the status of an update that is being downloaded or installed.
        when (statusManager.status.value) {
            is UpdateStatus.Checking,
            is UpdateStatus.Downloading,
            is UpdateStatus.ReadyToInstall,
            is UpdateStatus.Installing -> return false
            else -> Unit
        }
        if (!isUpdateCheckDue(preferences.lastCheckAtMs.first(), nowMs)) return false
        return checkNow(nowMs).getOrDefault(false)
    }

    /**
     * Checks now without the throttle. Failures stay silent so background checks never toast
     * over playback; the stored timestamp keeps a failed check from retrying on every resume.
     */
    suspend fun checkNow(nowMs: Long = System.currentTimeMillis()): Result<Boolean> {
        if (!repository.supportsSelfUpdate()) return Result.success(false)
        preferences.setLastCheckAtMs(nowMs)
        val result = repository.getLatestUpdate()
        if (result.isFailure) {
            // Do not wait a full hour after a transient failure (network came up a minute
            // after the TV woke); retry on a later foreground instead.
            preferences.setLastCheckAtMs(nowMs - (APP_UPDATE_CHECK_INTERVAL_MS - APP_UPDATE_CHECK_RETRY_MS))
            return result.map { false }
        }
        return result.map { update ->
            val newer = VersionUtils.isRemoteNewer(update.tag, repository.getInstalledVersionName())
            if (newer) {
                statusManager.updateStatus(UpdateStatus.UpdateAvailable(update))
            } else if (statusManager.status.value is UpdateStatus.UpdateAvailable) {
                statusManager.reset()
            }
            newer
        }
    }
}
