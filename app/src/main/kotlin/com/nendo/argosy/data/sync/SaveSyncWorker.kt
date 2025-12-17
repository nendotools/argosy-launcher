package com.nendo.argosy.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import com.nendo.argosy.util.Logger
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.usecase.save.CheckNewSavesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SaveSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkNewSavesUseCase: CheckNewSavesUseCase,
    private val saveSyncRepository: SaveSyncRepository,
    private val romMRepository: RomMRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SaveSyncWorker"
        private const val WORK_NAME = "save_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SaveSyncWorker>(
                6, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Logger.info(TAG, "Save sync scheduled every 6 hours")
        }

        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SaveSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Logger.info(TAG, "Save sync triggered manually")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val isConnected = romMRepository.connectionState.value is RomMRepository.ConnectionState.Connected
        if (!isConnected) {
            Logger.info(TAG, "RomM not connected, skipping save sync")
            return Result.success()
        }

        Logger.info(TAG, "Running save sync check")

        return try {
            val checkResult = checkNewSavesUseCase()
            Logger.info(TAG, "Found ${checkResult.newSavesCount} new saves from ${checkResult.platformsChecked} platforms")

            val uploaded = saveSyncRepository.processPendingUploads()
            Logger.info(TAG, "Processed $uploaded pending uploads")

            Result.success()
        } catch (e: Exception) {
            Logger.error(TAG, "Save sync failed", e)
            Result.retry()
        }
    }
}
