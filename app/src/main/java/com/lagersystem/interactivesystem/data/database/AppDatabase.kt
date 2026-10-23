package com.lagersystem.interactivesystem.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.item.ItemDao
import com.lagersystem.interactivesystem.data.log.LogDao
import com.lagersystem.interactivesystem.data.log.LogEntry
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.user.UserDao
import com.lagersystem.interactivesystem.data.warehouse.Warehouse
import com.lagersystem.interactivesystem.data.warehouse.WarehouseDao

@Database(entities = [Warehouse::class, Item::class, User::class, LogEntry::class], version = 12, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun warehouseDao(): WarehouseDao
    abstract fun itemDao(): ItemDao
    abstract fun userDao(): UserDao
    abstract fun logDao(): LogDao
}
