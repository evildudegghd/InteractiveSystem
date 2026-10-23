package com.lagersystem.interactivesystem.data.warehouse

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "warehouses",
    indices = [Index("name")]
)
data class Warehouse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val height: Int,
    val length: Int,
    val lastModified: Long = System.currentTimeMillis()
)
