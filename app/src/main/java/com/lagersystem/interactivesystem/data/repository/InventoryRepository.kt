package com.lagersystem.interactivesystem.data.repository

import com.google.gson.Gson
import com.lagersystem.interactivesystem.data.PreferenceManager
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.item.ItemDao
import com.lagersystem.interactivesystem.data.log.ActionType
import com.lagersystem.interactivesystem.data.log.LogDao
import com.lagersystem.interactivesystem.data.log.LogEntry
import com.lagersystem.interactivesystem.data.model.SyncData
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.user.UserDao
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.data.warehouse.Warehouse
import com.lagersystem.interactivesystem.data.warehouse.WarehouseDao
import com.lagersystem.interactivesystem.data.packinglist.*
import com.lagersystem.interactivesystem.sync.CloudSyncProvider
import com.lagersystem.interactivesystem.sync.CloudSyncService
import com.lagersystem.interactivesystem.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val itemDao: ItemDao,
    private val warehouseDao: WarehouseDao,
    private val userDao: UserDao,
    private val logDao: LogDao,
    private val syncProvider: CloudSyncProvider,
    private val preferenceManager: PreferenceManager
) {
    private val gson = Gson()

    private fun parseSyncData(json: String?): SyncData? {
        if (json.isNullOrBlank() || json.trim().startsWith("<")) return null
        return try {
            gson.fromJson(json, SyncData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Checking : SyncStatus()
        data class Conflict(val cloudData: SyncData, val etag: String) : SyncStatus()
        object Success : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus = _syncStatus.asStateFlow()

    val recentWarehouseIds: Flow<List<Long>> = preferenceManager.recentWarehouseIds

    suspend fun addRecentWarehouse(warehouseId: Long) {
        preferenceManager.addRecentWarehouse(warehouseId)
    }

    suspend fun checkSync(isFirstCheckAfterSetup: Boolean = false, isBackgroundSync: Boolean = false) = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.Checking
        val url = preferenceManager.webDavUrl.first() ?: return@withContext
        val user = preferenceManager.webDavUser.first() ?: return@withContext
        val pass = preferenceManager.webDavPass.first() ?: return@withContext
        val lastEtag = preferenceManager.lastSyncEtag.first()
        val service = syncProvider.getService()
        service.configure(url, user, pass)
        
        val response = service.getFileWithMeta("inventory.isdb")
        
        if (response.isError) {
            _syncStatus.value = SyncStatus.Error("Verbindung zum Server fehlgeschlagen.")
            return@withContext
        }

        if (response.isNotFound) {
            val legacy = service.getFileWithMeta("inventory_sync.json")
            if (!legacy.isNotFound && !legacy.isError && legacy.content != null) {
                // If we just migrated or have already seen this legacy file, don't trigger conflict
                if (legacy.etag == lastEtag || isFirstCheckAfterSetup || isBackgroundSync) {
                    if ((isFirstCheckAfterSetup || isBackgroundSync) && legacy.etag != null) {
                        preferenceManager.saveLastSyncEtag(legacy.etag)
                    }
                    _syncStatus.value = SyncStatus.Success
                    return@withContext
                }
                val cloudData = parseSyncData(legacy.content) ?: return@withContext
                _syncStatus.value = SyncStatus.Conflict(cloudData, legacy.etag ?: "")
                return@withContext
            }
            // Truly nothing found, upload local state
            uploadLocalState(null)
            return@withContext
        }

        val remoteEtag = response.etag?.trim()?.removeSurrounding("\"")
        if (remoteEtag != lastEtag) {
            val cloudData = parseSyncData(response.content)
            if (cloudData != null) {
                if (isFirstCheckAfterSetup || isBackgroundSync) {
                    performOverwriteFromCloud(cloudData, remoteEtag ?: "")
                } else {
                    _syncStatus.value = SyncStatus.Conflict(cloudData, remoteEtag ?: "")
                }
            }
        } else {
            uploadLocalState(lastEtag)
        }
    }

    suspend fun forceDownloadMigration(): SyncData? = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.Checking
        val url = preferenceManager.webDavUrl.first() ?: return@withContext null
        val user = preferenceManager.webDavUser.first() ?: return@withContext null
        val pass = preferenceManager.webDavPass.first() ?: return@withContext null
        val service = syncProvider.getService()
        service.configure(url, user, pass)
        
        val response = service.getFileWithMeta("inventory.isdb")
        val finalResponse = if (response.isNotFound || response.isError) service.getFileWithMeta("inventory_sync.json") else response
        
        if (finalResponse.content != null) {
            val cloudData = parseSyncData(finalResponse.content)
            if (cloudData != null) {
                performOverwriteFromCloud(cloudData, finalResponse.etag ?: "")
                cloudData
            } else {
                _syncStatus.value = SyncStatus.Error("Konnte heruntergeladene Daten nicht parsen.")
                null
            }
        } else {
            if (finalResponse.isError) {
                _syncStatus.value = SyncStatus.Error("Fehler beim Herunterladen der Daten.")
            } else {
                _syncStatus.value = SyncStatus.Error("Keine Migrationsdaten auf dem Server gefunden.")
            }
            null
        }
    }

    suspend fun performMerge(cloudData: SyncData, etag: String) = withContext(Dispatchers.IO) {
        mergeData(cloudData)
        uploadLocalState(etag)
    }

    suspend fun uploadSetupData(): Boolean = withContext(Dispatchers.IO) {
        val url = preferenceManager.webDavUrl.first()
        val user = preferenceManager.webDavUser.first()
        val pass = preferenceManager.webDavPass.first()
        if (url.isNullOrBlank() || user == null || pass == null) {
            _syncStatus.value = SyncStatus.Error("Cloud-Zugangsdaten fehlen.")
            return@withContext false
        }

        val service = syncProvider.getService()
        service.configure(url, user, pass)
        uploadLocalState(null)
        val meta = service.getFileWithMeta("inventory.isdb")
        if (!meta.isError && meta.etag != null) {
            preferenceManager.saveLastSyncEtag(meta.etag)
        }
        _syncStatus.value == SyncStatus.Success
    }

    suspend fun performOverwriteFromCloud(cloudData: SyncData, etag: String): SyncData = withContext(Dispatchers.IO) {
        val localItems = itemDao.getAllItemsSync()
        val localWarehouses = warehouseDao.getAllWarehousesSync()
        val localUsers = userDao.getAllUsersSync()
        
        val isSame = localItems == cloudData.items &&
                     localWarehouses == cloudData.warehouses &&
                     localUsers == cloudData.users

        if (!isSame) {
            itemDao.deleteAll()
            warehouseDao.deleteAll()
            userDao.deleteAll()
            logDao.deleteAll()
            cloudData.items.forEach { itemDao.insertItem(it.copy(serialNumbers = it.serialNumbers.orEmpty())) }
            cloudData.warehouses.forEach { warehouseDao.insertWarehouse(it) }
            cloudData.users.forEach { 
                userDao.insertUser(it)
                // Sync Admin password to PreferenceManager during migration
                if (it.role == UserRole.ADMIN && it.passwordHash != null) {
                    preferenceManager.setAdminPassword(it.passwordHash)
                }
            }
            cloudData.logs.forEach { logDao.insertLog(it) }
        }
        preferenceManager.saveLastSyncEtag(etag.trim().removeSurrounding("\""))
        _syncStatus.value = SyncStatus.Success
        cloudData
    }

    suspend fun listBackups(): List<String> = withContext(Dispatchers.IO) {
        val service = syncProvider.getService()
        val url = preferenceManager.webDavUrl.first() ?: return@withContext emptyList()
        val user = preferenceManager.webDavUser.first() ?: return@withContext emptyList()
        val pass = preferenceManager.webDavPass.first() ?: return@withContext emptyList()
        service.configure(url, user, pass)
        service.listFiles("").filter { it.startsWith("inventory_") && it.endsWith(".isdb") }
    }

    suspend fun getBackupData(fileName: String): SyncData? = withContext(Dispatchers.IO) {
        val service = syncProvider.getService()
        val url = preferenceManager.webDavUrl.first() ?: return@withContext null
        val user = preferenceManager.webDavUser.first() ?: return@withContext null
        val pass = preferenceManager.webDavPass.first() ?: return@withContext null
        service.configure(url, user, pass)
        val response = service.getFileWithMeta(fileName)
        response.content?.let { parseSyncData(it) }
    }

    suspend fun performRollback(fileName: String): SyncData? = withContext(Dispatchers.IO) {
        _syncStatus.value =SyncStatus.Checking
        val url = preferenceManager.webDavUrl.first() ?: return@withContext null
        val user = preferenceManager.webDavUser.first() ?: return@withContext null
        val pass = preferenceManager.webDavPass.first() ?: return@withContext null
        val service = syncProvider.getService()
        service.configure(url, user, pass)
        val response = service.getFileWithMeta(fileName)
        if (response.content != null) {
            val cloudData = parseSyncData(response.content)
            if (cloudData != null) {
                performOverwriteFromCloud(cloudData, response.etag ?: "")
                // Overwrite inventory.isdb so this backup becomes the main active file on the server
                service.uploadFile("inventory.isdb", response.content, null)
                cloudData
            } else {
                _syncStatus.value = SyncStatus.Error("Backup konnte nicht geparst werden.")
                null
            }
        } else {
            _syncStatus.value = SyncStatus.Error("Backup $fileName nicht gefunden oder Fehler.")
            null
        }
    }

    private suspend fun uploadLocalState(ifMatchEtag: String?) {
        val localData = SyncData(
            items = itemDao.getAllItemsSync(),
            warehouses = warehouseDao.getAllWarehousesSync(),
            users = userDao.getAllUsersSync(),
            logs = logDao.getAllLogsSync()
        )
        val json = gson.toJson(localData)
        val service = syncProvider.getService()
        
        val remote = service.getFileWithMeta("inventory.isdb")
        val remoteData = parseSyncData(remote.content)
        
        val isDifferent = remoteData == null || 
                          remoteData.items != localData.items ||
                          remoteData.warehouses != localData.warehouses ||
                          remoteData.users != localData.users ||
                          remoteData.logs != localData.logs

        // Only backup and upload if data has changed compared to what's on server
        if (!remote.isError && isDifferent) {
            rotateBackups(service, json)
            val success = service.uploadFile("inventory.isdb", json, ifMatchEtag)
            if (success) {
                val fresh = service.getFileWithMeta("inventory.isdb")
                preferenceManager.saveLastSyncEtag(fresh.etag)
                _syncStatus.value = SyncStatus.Success
            } else {
                _syncStatus.value = SyncStatus.Error("Upload fehlgeschlagen.")
            }
        } else if (!remote.isError) {
            preferenceManager.saveLastSyncEtag(remote.etag)
            _syncStatus.value = SyncStatus.Success
        } else {
            _syncStatus.value = SyncStatus.Error("Konnte Server-Status für Upload nicht prüfen.")
        }
    }

    private suspend fun rotateBackups(service: CloudSyncService, currentJson: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupName = "inventory_$timestamp.isdb"
        
        service.uploadFile(backupName, currentJson, null)
        val allFiles = service.listFiles("").filter { it.startsWith("inventory_") && it.endsWith(".isdb") }
        if (allFiles.size > 5) {
            val sortedFiles = allFiles.sortedDescending()
            val toDelete = sortedFiles.drop(5)
            toDelete.forEach { service.deleteFile(it) }
        }
    }

    suspend fun getItemByInventoryNumber(inventoryNumber: String) = itemDao.getItemByInventoryNumber(inventoryNumber)
    suspend fun getAllItemsSync() = itemDao.getAllItemsSync()
    fun getPackingLists(warehouseId: Long) = preferenceManager.packingLists(warehouseId, gson)
    suspend fun savePackingList(warehouseId: Long, packingList: PackingList) = preferenceManager.savePackingList(warehouseId, packingList, gson)
    suspend fun deletePackingList(warehouseId: Long, id: String) = preferenceManager.deletePackingList(warehouseId, id, gson)
    fun searchItems(query: String) = itemDao.searchItems(query)
    fun getItemsByWarehouse(warehouseId: Long) = itemDao.getItemsByWarehouse(warehouseId)
    fun getAllWarehouses() = warehouseDao.getAllWarehouses()
    suspend fun getAllWarehousesSync() = warehouseDao.getAllWarehousesSync()
    fun getAllUsers() = userDao.getAllUsers()
    fun getAllLogs() = logDao.getAllLogs()
    suspend fun getAllLogsSync() = logDao.getAllLogsSync()

    suspend fun getFolders(path: String): List<String> = withContext(Dispatchers.IO) {
        val service = syncProvider.getService()
        val url = preferenceManager.webDavUrl.first() ?: return@withContext emptyList()
        val user = preferenceManager.webDavUser.first() ?: return@withContext emptyList()
        val pass = preferenceManager.webDavPass.first() ?: return@withContext emptyList()
        service.configure(url, user, pass)
        service.listFolders(path)
    }

    suspend fun createFolder(folderName: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val service = syncProvider.getService()
        val url = preferenceManager.webDavUrl.first() ?: return@withContext false
        val user = preferenceManager.webDavUser.first() ?: return@withContext false
        val pass = preferenceManager.webDavPass.first() ?: return@withContext false
        service.configure(url, user, pass)
        service.createFolder(folderName, path)
    }

    suspend fun updateCloudPath(newPath: String) = withContext(Dispatchers.IO) {
        val url = preferenceManager.webDavUrl.first() ?: ""
        val user = preferenceManager.webDavUser.first() ?: ""
        val pass = preferenceManager.webDavPass.first() ?: ""
        val protocol = preferenceManager.cloudProtocol.first() ?: "WEBDAV"
        
        // Clean scheme first to avoid "webdav://" leaks
        var cleanUrl = url
        if (url.startsWith("webdav://", ignoreCase = true)) cleanUrl = "http://" + url.substring(9)
        else if (url.startsWith("webdavs://", ignoreCase = true)) cleanUrl = "https://" + url.substring(10)
        
        val scheme = cleanUrl.substringBefore("://", "https")
        val rest = cleanUrl.substringAfter("://", "")
        val hostPart = rest.substringBefore("/")
        
        val newFullUrl = "$scheme://$hostPart$newPath"
        
        preferenceManager.saveWebDavSettings(newFullUrl, user, pass, protocol)
    }

    suspend fun insertUser(user: User) = userDao.insertUser(user)

    suspend fun changeOwnPassword(user: User, currentPassword: String, newPassword: String): String? {
        val currentHash = if (user.role == UserRole.ADMIN) {
            preferenceManager.adminPassword.first()
        } else {
            user.passwordHash
        }
        if (!currentHash.isNullOrBlank() && SecurityUtils.hashPassword(currentPassword) != currentHash) {
            return "Das aktuelle Passwort ist nicht korrekt."
        }
        val valid = if (user.role == UserRole.ADMIN) {
            SecurityUtils.isValidPassword(newPassword)
        } else {
            newPassword.length >= 4
        }
        if (!valid) {
            return if (user.role == UserRole.ADMIN) {
                "Das Passwort benötigt mindestens 8 Zeichen sowie Groß-/Kleinbuchstaben, Zahl und Sonderzeichen."
            } else {
                "Das Passwort muss mindestens 4 Zeichen lang sein."
            }
        }

        val newHash = SecurityUtils.hashPassword(newPassword)
        insertUser(user.copy(
            passwordHash = newHash,
            isTemporaryPassword = false,
            lastModified = System.currentTimeMillis()
        ))
        if (user.role == UserRole.ADMIN) preferenceManager.setAdminPassword(newHash)
        return null
    }

    suspend fun renameUser(oldUser: User, newName: String, newPasswordHash: String?, isTemporary: Boolean = false) = withContext(Dispatchers.IO) {
        val newId = newName.lowercase().replace(" ", "_")
        val newUser = oldUser.copy(
            id = newId, 
            name = newName, 
            passwordHash = newPasswordHash,
            isTemporaryPassword = isTemporary
        )
        userDao.insertUser(newUser)
        if (oldUser.id != newId) {
            logDao.updateUserId(oldUser.id, newId)
            userDao.deleteUser(oldUser)
            val current = preferenceManager.currentUserId.first()
            if (current == oldUser.id) {
                preferenceManager.setCurrentUserId(newId)
            }
        }
    }

    suspend fun insertItem(item: Item) = itemDao.insertItem(item)

    suspend fun renameItem(oldItem: Item, newItem: Item) = withContext(Dispatchers.IO) {
        if (oldItem.inventoryNumber != newItem.inventoryNumber) {
            itemDao.insertItem(newItem)
            logDao.updateItemId(oldItem.inventoryNumber, newItem.inventoryNumber)
            itemDao.deleteItem(oldItem)
        } else {
            itemDao.insertItem(newItem)
        }
    }

    suspend fun performDeleteItem(item: Item) = itemDao.deleteItem(item)
    suspend fun deleteWarehouse(warehouse: Warehouse) = warehouseDao.deleteWarehouse(warehouse)
    suspend fun createWarehouse(name: String, height: Int, length: Int): Long {
        return warehouseDao.insertWarehouse(Warehouse(name = name, height = height, length = length))
    }

    suspend fun importIsdb(json: String) = withContext(Dispatchers.IO) {
        val data = parseSyncData(json) ?: return@withContext
        data.warehouses.forEach { warehouseDao.insertWarehouse(it) }
        data.items.forEach { itemDao.insertItem(it.copy(serialNumbers = it.serialNumbers.orEmpty())) }
    }

    suspend fun updateItemStock(inventoryNumber: String, amount: Int, userId: String, isReturn: Boolean): Long {
        val item = itemDao.getItemByInventoryNumber(inventoryNumber) ?: return -1L
        val newStock = if (isReturn) {
            minOf(item.maxStock, item.stock + amount)
        } else {
            maxOf(0, item.stock - amount)
        }
        val updatedItem = item.copy(stock = newStock, lastModified = System.currentTimeMillis())
        itemDao.updateItem(updatedItem)
        val action = if (isReturn) ActionType.RETURN else ActionType.LEND
        val timestamp = System.currentTimeMillis()
        return logDao.insertLog(
            LogEntry(timestamp = timestamp, userId = userId, itemId = inventoryNumber, action = action, amount = amount)
        )
    }

    suspend fun reportItemStatus(inventoryNumber: String, amount: Int, userId: String, isFound: Boolean) {
        if (amount <= 0) return
        val item = itemDao.getItemByInventoryNumber(inventoryNumber) ?: return
        val logs = logDao.getAllLogsSync().filter { it.itemId == inventoryNumber }
        val missing = maxOf(
            0,
            logs.filter {
                it.action == ActionType.MISSING || it.action == ActionType.LOST_WHILE_LENT
            }.sumOf { it.amount } -
                logs.filter { it.action == ActionType.FOUND }.sumOf { it.amount }
        )

        if (isFound) {
            if (amount > missing || item.stock + amount > item.maxStock) return
            itemDao.updateItem(item.copy(stock = item.stock + amount, lastModified = System.currentTimeMillis()))
            logDao.insertLog(LogEntry(userId = userId, itemId = inventoryNumber, action = ActionType.FOUND, amount = amount))
            return
        }

        val borrowed = maxOf(
            0,
            logs.filter { it.action == ActionType.LEND }.sumOf { it.amount } -
                logs.filter {
                    it.action == ActionType.RETURN || it.action == ActionType.LOST_WHILE_LENT
                }.sumOf { it.amount }
        )
        if (amount > item.stock + borrowed) return

        val missingFromStock = minOf(amount, item.stock)
        val missingFromLoans = amount - missingFromStock
        if (missingFromStock > 0) {
            itemDao.updateItem(item.copy(stock = item.stock - missingFromStock, lastModified = System.currentTimeMillis()))
            logDao.insertLog(
                LogEntry(userId = userId, itemId = inventoryNumber, action = ActionType.MISSING, amount = missingFromStock)
            )
        }
        if (missingFromLoans > 0) {
            logDao.insertLog(
                LogEntry(userId = userId, itemId = inventoryNumber, action = ActionType.LOST_WHILE_LENT, amount = missingFromLoans)
            )
        }
    }

    private suspend fun mergeData(cloudData: SyncData) {
        cloudData.items.forEach { cloudItem ->
            val localItem = itemDao.getItemByInventoryNumber(cloudItem.inventoryNumber)
            if (localItem == null || cloudItem.lastModified > localItem.lastModified) {
                itemDao.insertItem(cloudItem.copy(serialNumbers = cloudItem.serialNumbers.orEmpty()))
            }
        }
        cloudData.warehouses.forEach { warehouseDao.insertWarehouse(it) }
        cloudData.users.forEach { userDao.insertUser(it) }
        cloudData.logs.forEach { logDao.insertLog(it) }
    }
}
