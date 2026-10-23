package com.lagersystem.interactivesystem.data.log

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries")
    suspend fun getAllLogsSync(): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntry): Long

    @Query("UPDATE log_entries SET userId = :newUserId WHERE userId = :oldUserId")
    suspend fun updateUserId(oldUserId: String, newUserId: String)

    @Query("UPDATE log_entries SET itemId = :newItemId WHERE itemId = :oldItemId")
    suspend fun updateItemId(oldItemId: String, newItemId: String)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()
}
