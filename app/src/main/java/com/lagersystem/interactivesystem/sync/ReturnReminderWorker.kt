package com.lagersystem.interactivesystem.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lagersystem.interactivesystem.data.PreferenceManager
import com.lagersystem.interactivesystem.data.log.ActionType
import com.lagersystem.interactivesystem.data.log.LogEntry
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ReturnReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: InventoryRepository,
    private val preferenceManager: PreferenceManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val inventoryNumber = inputData.getString(KEY_INVENTORY_NUMBER) ?: return Result.failure()
        val loanLogId = inputData.getLong(KEY_LOAN_LOG_ID, -1L)
        if (loanLogId < 0L) return Result.failure()
        if (!preferenceManager.returnRemindersEnabled(userId).first()) return Result.success()

        val item = repository.getItemByInventoryNumber(inventoryNumber) ?: return Result.success()
        val loanLogs = repository.getAllLogsSync()
            .filter { it.itemId == inventoryNumber && it.userId == userId }
            .sortedWith(compareBy<LogEntry> { it.timestamp }.thenBy { it.id })
        val activeLoans = mutableListOf<Pair<Long, Int>>()

        loanLogs.forEach { log ->
            when (log.action) {
                ActionType.LEND -> activeLoans += log.id to log.amount
                ActionType.RETURN, ActionType.LOST_WHILE_LENT -> {
                    var amountToClose = log.amount
                    var index = 0
                    while (amountToClose > 0 && index < activeLoans.size) {
                        val (id, count) = activeLoans[index]
                        val consumed = minOf(count, amountToClose)
                        amountToClose -= consumed
                        if (consumed == count) activeLoans.removeAt(index)
                        else {
                            activeLoans[index] = id to (count - consumed)
                            index++
                        }
                    }
                }
                else -> Unit
            }
        }

        val outstandingForLoan = activeLoans
            .firstOrNull { it.first == loanLogId }
            ?.second ?: 0
        if (outstandingForLoan > 0) {
            NotificationHelper.showReturnReminder(
                appContext,
                item.name,
                inventoryNumber,
                outstandingForLoan
            )
        }
        return Result.success()
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_INVENTORY_NUMBER = "inventory_number"
        private const val KEY_LOAN_LOG_ID = "loan_log_id"
        private const val USER_TAG_PREFIX = "return-reminder-user-"
        private const val REMINDER_DELAY_MILLIS = 24 * 60 * 60 * 1000L

        fun schedule(context: Context, userId: String, inventoryNumber: String, loanLogId: Long) {
            enqueue(context, userId, inventoryNumber, loanLogId, REMINDER_DELAY_MILLIS)
        }

        fun rescheduleOutstanding(context: Context, userId: String, logs: List<LogEntry>) {
            val loansByItem = logs
                .filter { it.userId == userId }
                .groupBy { it.itemId }
            val now = System.currentTimeMillis()

            loansByItem.forEach { (inventoryNumber, itemLogs) ->
                val activeLoans = mutableListOf<LogEntry>()
                itemLogs.sortedWith(compareBy<LogEntry> { it.timestamp }.thenBy { it.id }).forEach { log ->
                    when (log.action) {
                        ActionType.LEND -> activeLoans += log
                        ActionType.RETURN, ActionType.LOST_WHILE_LENT -> {
                            var amountToClose = log.amount
                            var index = 0
                            while (amountToClose > 0 && index < activeLoans.size) {
                                val loan = activeLoans[index]
                                val consumed = minOf(loan.amount, amountToClose)
                                amountToClose -= consumed
                                if (consumed == loan.amount) activeLoans.removeAt(index)
                                else {
                                    activeLoans[index] = loan.copy(amount = loan.amount - consumed)
                                    index++
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                activeLoans.forEach { loan ->
                    val delay = (loan.timestamp + REMINDER_DELAY_MILLIS - now).coerceAtLeast(0L)
                    enqueue(context, userId, inventoryNumber, loan.id, delay)
                }
            }
        }

        fun cancelForUser(context: Context, userId: String) {
            WorkManager.getInstance(context).cancelAllWorkByTag(tagForUser(userId))
        }

        private fun enqueue(
            context: Context,
            userId: String,
            inventoryNumber: String,
            loanLogId: Long,
            delayMillis: Long
        ) {
            val request = OneTimeWorkRequestBuilder<ReturnReminderWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_USER_ID to userId,
                        KEY_INVENTORY_NUMBER to inventoryNumber,
                        KEY_LOAN_LOG_ID to loanLogId
                    )
                )
                .addTag(tagForUser(userId))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        private fun tagForUser(userId: String) = "$USER_TAG_PREFIX$userId"
    }
}
