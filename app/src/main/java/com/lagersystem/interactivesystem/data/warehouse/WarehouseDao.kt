package com.lagersystem.interactivesystem.data.warehouse

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouses")
    suspend fun getAllWarehousesSync(): List<Warehouse>

    @Query("SELECT * FROM warehouses")
    fun getAllWarehouses(): Flow<List<Warehouse>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehouse(warehouse: Warehouse): Long

    @Update
    suspend fun updateWarehouse(warehouse: Warehouse): Int

    @Delete
    suspend fun deleteWarehouse(warehouse: Warehouse): Int

    @Query("DELETE FROM warehouses")
    suspend fun deleteAll()
}
