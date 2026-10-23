package com.lagersystem.interactivesystem.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.log.ActionType
import com.lagersystem.interactivesystem.data.warehouse.Warehouse
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class ItemLogStatus(
    val borrowed: Int = 0,
    val missing: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WarehouseViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _selectedWarehouse = MutableStateFlow<Warehouse?>(null)
    val selectedWarehouse = _selectedWarehouse.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    enum class SortOption {
        INVENTORY_NUMBER, CATEGORY, BRAND
    }

    private val _sortOption = MutableStateFlow(SortOption.INVENTORY_NUMBER)
    val sortOption = _sortOption.asStateFlow()

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    val itemsInWarehouse = combine(_selectedWarehouse, _searchQuery, _sortOption) { warehouse, query, sort ->
        Triple(warehouse, query, sort)
    }.flatMapLatest { (warehouse, query, sort) ->
        if (warehouse == null) flowOf(emptyList())
        else repository.getItemsByWarehouse(warehouse.id).map { list ->
            val filtered = if (query.isBlank()) list
            else list.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.inventoryNumber.contains(query, ignoreCase = true) ||
                it.model.contains(query, ignoreCase = true)
            }
            
            when (sort) {
                SortOption.INVENTORY_NUMBER -> filtered.sortedBy { it.inventoryNumber.lowercase() }
                SortOption.CATEGORY -> filtered.sortedBy { it.type.lowercase() }
                SortOption.BRAND -> filtered.sortedBy { it.name.lowercase() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allWarehouses = repository.getAllWarehouses()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentWarehouseIds = repository.recentWarehouseIds
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val itemLogStatuses = repository.getAllLogs()
        .map { logs ->
            logs.groupBy { it.itemId }.mapValues { (_, itemLogs) ->
                ItemLogStatus(
                    borrowed = maxOf(
                        0,
                        itemLogs.filter { it.action == ActionType.LEND }.sumOf { it.amount } -
                            itemLogs.filter {
                                it.action == ActionType.RETURN || it.action == ActionType.LOST_WHILE_LENT
                            }.sumOf { it.amount }
                    ),
                    missing = maxOf(
                        0,
                        itemLogs.filter {
                            it.action == ActionType.MISSING || it.action == ActionType.LOST_WHILE_LENT
                        }.sumOf { it.amount } -
                            itemLogs.filter { it.action == ActionType.FOUND }.sumOf { it.amount }
                    )
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _warehouseSummaries = MutableStateFlow<Map<Long, String>>(emptyMap())
    val warehouseSummaries = _warehouseSummaries.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllWarehouses(),
                repository.getAllLogs()
            ) { warehouses, logs ->
                warehouses to logs
            }.collect { (warehouses, logs) ->
                val summaries = mutableMapOf<Long, String>()
                warehouses.forEach { warehouse ->
                    val items = repository.getItemsByWarehouse(warehouse.id).first()
                    var totalExisting = 0
                    var totalBorrowed = 0
                    var totalMissing = 0
                    
                    items.forEach { item ->
                        val availableStock = item.stock
                        val itemLogs = logs.filter { it.itemId == item.inventoryNumber }
                        val lent = itemLogs.filter { it.action == ActionType.LEND }.sumOf { it.amount }
                        val returned = itemLogs.filter { it.action == ActionType.RETURN }.sumOf { it.amount }
                        val lostWhileLent = itemLogs.filter { it.action == ActionType.LOST_WHILE_LENT }.sumOf { it.amount }
                        val borrowed = maxOf(0, lent - returned - lostWhileLent)
                        val missing = maxOf(
                            0,
                            itemLogs.filter {
                                it.action == ActionType.MISSING || it.action == ActionType.LOST_WHILE_LENT
                            }.sumOf { it.amount } -
                                itemLogs.filter { it.action == ActionType.FOUND }.sumOf { it.amount }
                        )
                        
                        totalExisting += (availableStock + borrowed + missing)
                        totalBorrowed += borrowed
                        totalMissing += missing
                    }
                    
                    summaries[warehouse.id] = "$totalExisting Geräte erfasst, $totalBorrowed ausgeliehen, $totalMissing verschwunden"
                }
                _warehouseSummaries.value = summaries
            }
        }
    }

    suspend fun getAllLogsSync() = repository.getAllLogsSync()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectWarehouse(warehouse: Warehouse?) {
        _selectedWarehouse.value = warehouse
        _searchQuery.value = "" // Clear search when switching warehouses
        if (warehouse != null) {
            viewModelScope.launch {
                repository.addRecentWarehouse(warehouse.id)
            }
        }
    }

    suspend fun lendItem(inventoryNumber: String, amount: Int, userId: String): Long =
        repository.updateItemStock(inventoryNumber, amount, userId, isReturn = false)

    fun returnItem(inventoryNumber: String, amount: Int, userId: String) {
        viewModelScope.launch {
            repository.updateItemStock(inventoryNumber, amount, userId, isReturn = true)
        }
    }

    fun reportMissing(inventoryNumber: String, amount: Int, userId: String) {
        viewModelScope.launch {
            repository.reportItemStatus(inventoryNumber, amount, userId, isFound = false)
        }
    }

    fun reportFound(inventoryNumber: String, amount: Int, userId: String) {
        viewModelScope.launch {
            repository.reportItemStatus(inventoryNumber, amount, userId, isFound = true)
        }
    }

    suspend fun getItemLogStatusSync(inventoryNumber: String): ItemLogStatus {
        val logs = repository.getAllLogsSync().filter { it.itemId == inventoryNumber }
        return ItemLogStatus(
            borrowed = maxOf(
                0,
                logs.filter { it.action == ActionType.LEND }.sumOf { it.amount } -
                    logs.filter {
                        it.action == ActionType.RETURN || it.action == ActionType.LOST_WHILE_LENT
                    }.sumOf { it.amount }
            ),
            missing = maxOf(
                0,
                logs.filter {
                    it.action == ActionType.MISSING || it.action == ActionType.LOST_WHILE_LENT
                }.sumOf { it.amount } -
                    logs.filter { it.action == ActionType.FOUND }.sumOf { it.amount }
            )
        )
    }

    fun upsertItem(oldItem: Item?, newItem: Item) {
        viewModelScope.launch {
            if (oldItem != null) {
                repository.renameItem(oldItem, newItem)
            } else {
                repository.insertItem(newItem)
            }
        }
    }

    fun deleteItem(item: Item) {
        viewModelScope.launch {
            repository.performDeleteItem(item)
        }
    }

    fun addWarehouse(name: String, height: Int, length: Int) {
        viewModelScope.launch {
            repository.createWarehouse(name, height, length)
        }
    }

    fun deleteWarehouse(warehouse: Warehouse) {
        viewModelScope.launch {
            repository.deleteWarehouse(warehouse)
        }
    }

    suspend fun getItemsInWarehouseSync(warehouseId: Long): List<Item> = repository.getItemsByWarehouse(warehouseId).first()

    suspend fun getItem(inventoryNumber: String) = repository.getItemByInventoryNumber(inventoryNumber)
}
