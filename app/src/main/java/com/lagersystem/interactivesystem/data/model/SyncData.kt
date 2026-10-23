package com.lagersystem.interactivesystem.data.model

import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.log.LogEntry
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.warehouse.Warehouse

data class SyncData(
    val items: List<Item>,
    val warehouses: List<Warehouse>,
    val users: List<User>,
    val logs: List<LogEntry>,
    val syncTimestamp: Long = System.currentTimeMillis()
)
