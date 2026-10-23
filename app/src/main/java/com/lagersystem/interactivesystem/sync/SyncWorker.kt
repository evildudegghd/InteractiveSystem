package com.lagersystem.interactivesystem.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: InventoryRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Get state BEFORE sync
            val oldLogs = repository.getAllLogs().first()
            val oldItemCount = repository.searchItems("").first().size
            
            // Perform sync
            repository.checkSync(isBackgroundSync = true) 
            
            // Get state AFTER sync
            val newLogs = repository.getAllLogs().first()
            val newItemCount = repository.searchItems("").first().size
            
            // Global Notification Logic: Notify on ANY external change detected
            if (newLogs.size > oldLogs.size) {
                val latestLog = newLogs.first()
                NotificationHelper.showNotification(
                    applicationContext, 
                    "Lager Aktualisierung", 
                    "Neue Aktivität registriert: ${latestLog.action.name} von ${latestLog.userId}"
                )
            } else if (newItemCount != oldItemCount) {
                NotificationHelper.showNotification(
                    applicationContext,
                    "Bestand synchronisiert",
                    "Es wurden Änderungen am Artikelstamm aus der Cloud übernommen."
                )
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val SYNC_WORK_NAME = "PeriodicInventorySync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Minimum interval for PeriodicWork is 15 minutes
            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE, // Update existing work with new frequency
                syncRequest
            )
        }
    }
}
