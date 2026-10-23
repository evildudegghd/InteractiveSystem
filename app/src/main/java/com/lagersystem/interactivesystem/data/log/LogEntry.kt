package com.lagersystem.interactivesystem.data.log

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ActionType {
    LEND, RETURN, MISSING, LOST_WHILE_LENT, FOUND, ADD_STOCK, REMOVE_STOCK
}

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String,
    val itemId: String,
    val action: ActionType,
    val amount: Int
)
