package com.lagersystem.interactivesystem.di

import android.content.Context
import androidx.room.Room
import com.lagersystem.interactivesystem.data.database.AppDatabase
import com.lagersystem.interactivesystem.data.item.ItemDao
import com.lagersystem.interactivesystem.data.log.LogDao
import com.lagersystem.interactivesystem.data.user.UserDao
import com.lagersystem.interactivesystem.data.warehouse.WarehouseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "inventory_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWarehouseDao(db: AppDatabase): WarehouseDao = db.warehouseDao()

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()
}
