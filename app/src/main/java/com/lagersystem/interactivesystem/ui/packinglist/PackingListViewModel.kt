package com.lagersystem.interactivesystem.ui.packinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagersystem.interactivesystem.data.packinglist.PackingList
import com.lagersystem.interactivesystem.data.packinglist.PackingListItem
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PackingListViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _currentWarehouseId = MutableStateFlow<Long>(-1L)

    val packingLists = _currentWarehouseId.flatMapLatest { warehouseId ->
        if (warehouseId == -1L) flowOf(emptyList())
        else repository.getPackingLists(warehouseId)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allItems = flow { emit(repository.getAllItemsSync()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setWarehouseId(warehouseId: Long) {
        _currentWarehouseId.value = warehouseId
    }

    fun createPackingList(warehouseId: Long, title: String, createdBy: String, items: List<PackingListItem>) {
        viewModelScope.launch {
            repository.savePackingList(
                warehouseId,
                PackingList(warehouseId = warehouseId, title = title, createdBy = createdBy, items = items)
            )
        }
    }

    fun deletePackingList(warehouseId: Long, id: String) {
        viewModelScope.launch {
            repository.deletePackingList(warehouseId, id)
        }
    }
}
