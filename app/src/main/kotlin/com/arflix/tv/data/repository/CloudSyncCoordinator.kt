package com.arflix.tv.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.arflix.tv.worker.CloudSyncWorker

@Singleton
class CloudSyncCoordinator internal constructor(
    private val context: Context,
    private val invalidationBus: CloudSyncInvalidationBus,
    private val cloudSyncRepository: CloudSyncRepository,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        invalidationBus: CloudSyncInvalidationBus,
        cloudSyncRepository: CloudSyncRepository,
        authRepository: AuthRepository
    ) : this(context, invalidationBus, cloudSyncRepository, authRepository, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private val lifecycleLock = Any()
    private var collectorJob: Job? = null
    private var flushJob: Job? = null

    private val started = AtomicBoolean(false)

    private suspend fun getSyncUserIdOrNull(): String? {
        return try {
            authRepository.getCurrentUserIdForSync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: retrofit2.HttpException) {
            Log.w("CloudSyncCoordinator", "HTTP error retrieving sync user ID", e)
            null
        } catch (e: java.io.IOException) {
            Log.w("CloudSyncCoordinator", "Network error retrieving sync user ID", e)
            null
        } catch (e: Exception) {
            Log.w("CloudSyncCoordinator", "Failed to retrieve sync user ID", e)
            null
        }
    }

    fun start() {
        synchronized(lifecycleLock) {
            if (!started.compareAndSet(false, true)) return
            collectorJob = scope.launch {
                invalidationBus.events.collectLatest { invalidation ->
                    val userId = getSyncUserIdOrNull()
                    if (userId.isNullOrBlank()) {
                        cloudSyncRepository.markLocalStateDirtyNow()
                        Log.i("CloudSyncCoordinator", "Kept dirty ${invalidation.scope}: auth not ready")
                        return@collectLatest
                    }
                    Log.i(
                        "CloudSyncCoordinator",
                        "Dirty ${invalidation.scope} profile=${invalidation.profileId.orEmpty()} reason=${invalidation.reason}"
                    )
                    cloudSyncRepository.markLocalStateDirtyNow()
                    scheduleFlush(invalidation)
                }
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            started.set(false)
            collectorJob?.cancel()
            flushJob?.cancel()
            collectorJob = null
            flushJob = null
        }
    }

    private fun scheduleFlush(invalidation: CloudSyncInvalidation) {
        synchronized(lifecycleLock) {
            if (!started.get()) return
            flushJob?.cancel()
            flushJob = scope.launch {
                val backoffMs = if (cloudSyncRepository.pushFailureCount > 0) {
                    // Exponential backoff: 2s, 4s, 8s, 16s... max 1 minute (for active app)
                    maxOf(
                        debounceMsFor(invalidation),
                        (2_000L * (1 shl (cloudSyncRepository.pushFailureCount - 1).coerceAtMost(5))).coerceAtMost(60_000L)
                    )
                } else {
                    debounceMsFor(invalidation)
                }
                delay(backoffMs)

                val userId = getSyncUserIdOrNull()
                if (userId.isNullOrBlank()) {
                    cloudSyncRepository.markLocalStateDirtyNow()
                    Log.w("CloudSyncCoordinator", "Deferred cloud sync for ${invalidation.scope}: auth not ready")
                    return@launch
                }
                Log.i("CloudSyncCoordinator", "Flushing cloud sync for ${invalidation.scope}")
                try {
                    cloudSyncRepository.pushToCloud(force = true).getOrThrow()
                } catch (e: CancellationException) {
                    throw e
                } catch (error: Exception) {
                    Log.w("CloudSyncCoordinator", "Cloud push failed after ${invalidation.scope}: ${error.message}")
                    cloudSyncRepository.markLocalStateDirty()
                    if (getSyncUserIdOrNull() == userId) CloudSyncWorker.enqueueRecovery(context)
                }
            }
        }
    }

    private fun debounceMsFor(invalidation: CloudSyncInvalidation): Long {
        return when (invalidation.scope) {
            CloudSyncScope.LOCAL_HISTORY -> 2_000L
            CloudSyncScope.IPTV -> if (invalidation.reason == "save tv session") 30_000L else 750L
            CloudSyncScope.PLUGINS -> 1_000L
            else -> 500L
        }
    }
}
