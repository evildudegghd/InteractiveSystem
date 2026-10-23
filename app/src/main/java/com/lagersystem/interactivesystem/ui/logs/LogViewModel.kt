package com.lagersystem.interactivesystem.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.data.log.LogEntry
import com.lagersystem.interactivesystem.data.warehouse.Warehouse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogUiModel(
    val logEntry: LogEntry,
    val itemName: String,
    val warehouseName: String
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _selectedWarehouse = MutableStateFlow<Warehouse?>(null)
    val selectedWarehouse = _selectedWarehouse.asStateFlow()

    fun setSelectedWarehouse(warehouse: Warehouse?) {
        _selectedWarehouse.value = warehouse
    }

    private val _logUiModels = MutableStateFlow<List<LogUiModel>>(emptyList())
    val logUiModels: StateFlow<List<LogUiModel>> = _logUiModels.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllLogs(),
                flow { emit(repository.getAllItemsSync()) },
                flow { emit(repository.getAllWarehousesSync()) },
                _selectedWarehouse
            ) { logs, items, warehouses, selectedWh ->
                val itemMap = items.associateBy { it.inventoryNumber }
                val warehouseMap = warehouses.associateBy { it.id }

                logs.mapNotNull { log ->
                    val item = itemMap[log.itemId]
                    val warehouse = item?.warehouseId?.let { warehouseMap[it] }

                    if (selectedWh != null && item?.warehouseId != selectedWh.id) {
                        return@mapNotNull null
                    }

                    LogUiModel(
                        logEntry = log,
                        itemName = item?.name ?: log.itemId,
                        warehouseName = warehouse?.name ?: "Unbekanntes Lager"
                    )
                }
            }.collect {
                _logUiModels.value = it
            }
        }
    }
}
