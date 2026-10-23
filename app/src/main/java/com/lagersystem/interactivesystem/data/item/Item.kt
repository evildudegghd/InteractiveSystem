package com.lagersystem.interactivesystem.data.item

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "items",
    indices = [
        Index("warehouseId"),
        Index("inventoryNumber"),
        Index("nfcTag"),
        Index("qrCode")
    ]
)
data class Item(
    @PrimaryKey val inventoryNumber: String,
    val name: String,
    val model: String = "",
    val stock: Int,
    val maxStock: Int = stock,
    val warehouseId: Long,
    val row: Int,
    val column: Int,
    val nfcTag: String? = null,
    val qrCode: String? = null,
    val type: String = "Allgemein",
    val attributes: String = "", // Comma separated features
    val location: String = "",
    val serialNumbers: String = "",
    val iconName: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val hasConflict: Boolean = false,
    val status: String = "Vorhanden"
)
