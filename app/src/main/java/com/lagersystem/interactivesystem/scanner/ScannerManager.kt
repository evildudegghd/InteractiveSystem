package com.lagersystem.interactivesystem.scanner

import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class ScannerManager @Inject constructor(
    private val repository: InventoryRepository
) {
    private val _scanEvents = MutableSharedFlow<ScanResult>()
    val scanEvents = _scanEvents.asSharedFlow()

    sealed class ScanResult {
        data class Success(val inventoryNumber: String) : ScanResult()
        data class Error(val message: String) : ScanResult()
        object NotFound : ScanResult()
    }

    suspend fun handleQrCode(rawContent: String) {
        if (repository.getItemByInventoryNumber(rawContent) != null) {
            processInventoryNumber(rawContent)
            return
        }
        val inventoryNumber = InventoryTagPayload.decodeInventoryNumber(rawContent)
        if (inventoryNumber != null) {
            processInventoryNumber(inventoryNumber)
        } else {
            _scanEvents.emit(ScanResult.NotFound)
        }
    }

    suspend fun handleNfcTag(content: String) {
        if (repository.getItemByInventoryNumber(content) != null) {
            processInventoryNumber(content)
            return
        }
        val inventoryNumber = InventoryTagPayload.decodeInventoryNumber(content)
        if (inventoryNumber != null) {
            processInventoryNumber(inventoryNumber)
        } else {
            _scanEvents.emit(ScanResult.Error("Ungültige NFC-Daten"))
        }
    }

    suspend fun manualScan(inventoryNumber: String) {
        processInventoryNumber(inventoryNumber)
    }

    private suspend fun processInventoryNumber(inventoryNumber: String) {
        val item = repository.getItemByInventoryNumber(inventoryNumber)
        if (item != null) {
            _scanEvents.emit(ScanResult.Success(inventoryNumber))
        } else {
            _scanEvents.emit(ScanResult.NotFound)
        }
    }
}
