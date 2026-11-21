package com.arflix.tv.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arflix.tv.data.repository.CloudSyncRepository
import com.arflix.tv.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cloudSyncRepository: CloudSyncRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing background cloud sync recovery")

        return try {
            if (authRepository.getCurrentUserIdForSync().isNullOrBlank() ||
                !cloudSyncRepository.hasPendingLocalChanges()
            ) return Result.success()

            val result = cloudSyncRepository.pushToCloud()
            if (result.isSuccess) {
                Log.i(TAG, "Background cloud sync recovery succeeded")
                Result.success()
            } else {
                Log.w(TAG, "Background cloud sync recovery failed, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error in background cloud sync: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CloudSyncWorker"
        private const val WORK_NAME = "CloudSyncRecoveryWork"

        fun enqueueRecovery(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
