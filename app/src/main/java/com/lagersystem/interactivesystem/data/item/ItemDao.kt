package com.lagersystem.interactivesystem.data.item

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    suspend fun getAllItemsSync(): List<Item>

    @Query("SELECT * FROM items WHERE inventoryNumber = :invNum")
    suspend fun getItemByInventoryNumber(invNum: String): Item?

    @Query("SELECT * FROM items WHERE warehouseId = :warehouseId")
    fun getItemsByWarehouse(warehouseId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' OR inventoryNumber LIKE '%' || :query || '%'")
    fun searchItems(query: String): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item): Int

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("DELETE FROM items")
    suspend fun deleteAll()
}
