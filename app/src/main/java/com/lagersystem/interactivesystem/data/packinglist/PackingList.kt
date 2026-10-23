package com.lagersystem.interactivesystem.data.packinglist

import java.util.UUID

data class PackingListItem(
    val inventoryNumber: String,
    val name: String,
    val model: String,
    val quantity: Int
)

data class PackingList(
    val id: String = UUID.randomUUID().toString(),
    val warehouseId: Long,
    val title: String,
    val createdBy: String,
    val items: List<PackingListItem>,
    val timestamp: Long = System.currentTimeMillis()
)
