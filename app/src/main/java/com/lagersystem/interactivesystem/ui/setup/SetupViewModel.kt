package com.lagersystem.interactivesystem.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagersystem.interactivesystem.data.PreferenceManager
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.data.model.SyncData
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.sync.CloudSyncProvider
import com.lagersystem.interactivesystem.util.ConnectivityObserver
import com.lagersystem.interactivesystem.util.SecurityUtils
import com.google.gson.Gson
import com.lagersystem.interactivesystem.util.TotpUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val repository: InventoryRepository,
    private val syncProvider: CloudSyncProvider,
    val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val gson = Gson()

    enum class SetupStep {
        INSTRUCTIONS, DATA_DECISION, ADMIN, ADMIN_TOTP, USERS, WEBDAV, WAREHOUSE, SUMMARY, FINISHING
    }

    private val _currentStep = MutableStateFlow(SetupStep.INSTRUCTIONS)
    val currentStep = _currentStep.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating = _isMigrating.asStateFlow()

    private val _isWebDavValid = MutableStateFlow<Boolean?>(null)
    val isWebDavValid = _isWebDavValid.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting = _isTesting.asStateFlow()

    data class RemoteItem(val name: String, val isFolder: Boolean)
    private val _remoteItems = MutableStateFlow<List<RemoteItem>>(emptyList())
    val remoteItems = _remoteItems.asStateFlow()

    private val _isBrowsing = MutableStateFlow(false)
    val isBrowsing = _isBrowsing.asStateFlow()

    private val _pathValidated = MutableStateFlow(false)
    val pathValidated = _pathValidated.asStateFlow()

    private val _importedUsers = MutableStateFlow<List<String>>(emptyList())
    val importedUsers = _importedUsers.asStateFlow()

    private val _importedWarehouses = MutableStateFlow<List<String>>(emptyList())
    val importedWarehouses = _importedWarehouses.asStateFlow()

    private val _adminTotpSecret = MutableStateFlow<String?>(null)
    val adminTotpSecret = _adminTotpSecret.asStateFlow()

    private val _adminTotpConfirmed = MutableStateFlow(false)
    val adminTotpConfirmed = _adminTotpConfirmed.asStateFlow()

    private val _migrationError = MutableStateFlow<String?>(null)
    val migrationError = _migrationError.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError = _setupError.asStateFlow()

    fun setMigrating(migrating: Boolean) {
        _isMigrating.value = migrating
        _migrationError.value = null
        _pathValidated.value = false
    }

    fun clearMigrationError() {
        _migrationError.value = null
    }

    fun invalidateCloudConnection() {
        _isWebDavValid.value = null
        _pathValidated.value = false
        _migrationError.value = null
    }

    fun validateMigrationPath(
        protocol: CloudProtocol,
        host: String,
        port: String,
        user: String,
        pass: String,
        path: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBrowsing.value = true
            _migrationError.value = null

            val cleanHost = host.trim()
                .removePrefix("http://").removePrefix("https://")
                .removePrefix("webdav://").removePrefix("webdavs://")
                .removePrefix("ftp://").removePrefix("sftp://")
                .removePrefix("smb://")
                .substringBefore("/")

            val scheme = when(protocol) {
                CloudProtocol.WEBDAV -> "https"
                CloudProtocol.FTP -> "ftp"
                CloudProtocol.SFTP -> "sftp"
                CloudProtocol.SMB -> "smb"
            }
            val fullUrl = "$scheme://$cleanHost${if(port.isNotEmpty()) ":$port" else ""}$path"

            try {
                val service = syncProvider.getService()
                service.configure(fullUrl, user, pass)
                
                val res1 = service.getFileWithMeta("inventory.isdb")
                val res2 = service.getFileWithMeta("inventory_sync.json")
                val exists = (!res1.isNotFound && !res1.isError) || (!res2.isNotFound && !res2.isError)
                
                if (exists) {
                    onSuccess()
                } else {
                    _migrationError.value = "Keine Datenbank (.isdb) in diesem Ordner gefunden. Bitte wählen Sie einen anderen Ordner."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _migrationError.value = "Fehler beim Zugriff auf den Ordner: ${e.message}"
            } finally {
                _isBrowsing.value = false
            }
        }
    }

    fun previousStep() {
        val current = _currentStep.value
        val prev = when (_currentStep.value) {
            SetupStep.INSTRUCTIONS -> SetupStep.INSTRUCTIONS
            SetupStep.DATA_DECISION -> SetupStep.INSTRUCTIONS
            SetupStep.ADMIN -> SetupStep.DATA_DECISION
            SetupStep.ADMIN_TOTP -> SetupStep.ADMIN
            SetupStep.USERS -> SetupStep.ADMIN_TOTP
            SetupStep.WEBDAV -> SetupStep.DATA_DECISION // Always go back to decision from sync
            SetupStep.WAREHOUSE -> SetupStep.WEBDAV
            SetupStep.SUMMARY -> if (_isMigrating.value) SetupStep.WEBDAV else SetupStep.WAREHOUSE
            SetupStep.FINISHING -> SetupStep.FINISHING
        }
        _currentStep.value = prev

        if (current == SetupStep.ADMIN_TOTP && prev == SetupStep.ADMIN) {
            _adminTotpSecret.value = null
            _adminTotpConfirmed.value = false
        }
        
        // Reset migration specific state if going back to decision
        if (prev == SetupStep.DATA_DECISION) {
            _pathValidated.value = false
            _migrationError.value = null
            _isWebDavValid.value = null
        }
    }

    fun nextStep() {
        if (_currentStep.value == SetupStep.ADMIN_TOTP && !_adminTotpConfirmed.value) {
            return
        }
        if (_currentStep.value == SetupStep.WEBDAV && !_pathValidated.value) {
            _migrationError.value = "Bitte wählen und bestätigen Sie zuerst einen Cloud-Pfad."
            return
        }

        val next = when (_currentStep.value) {
            SetupStep.INSTRUCTIONS -> SetupStep.DATA_DECISION
            SetupStep.DATA_DECISION -> if (_isMigrating.value) SetupStep.WEBDAV else SetupStep.ADMIN
            SetupStep.ADMIN -> {
                generateAdminTotpSecret()
                SetupStep.ADMIN_TOTP
            }
            SetupStep.ADMIN_TOTP -> SetupStep.USERS
            SetupStep.USERS -> SetupStep.WEBDAV
            SetupStep.WEBDAV -> if (_isMigrating.value) SetupStep.SUMMARY else SetupStep.WAREHOUSE
            SetupStep.WAREHOUSE -> SetupStep.SUMMARY
            SetupStep.SUMMARY -> SetupStep.FINISHING
            SetupStep.FINISHING -> SetupStep.FINISHING
        }
        _currentStep.value = next
    }

    fun generateAdminTotpSecret() {
        if (_adminTotpSecret.value == null) {
            _adminTotpSecret.value = TotpUtils.generateSecret()
            _adminTotpConfirmed.value = false
        }
    }

    fun verifyAdminTotpConfirmation(code: String): Boolean {
        val secret = _adminTotpSecret.value ?: return false
        val isValid = TotpUtils.verifyCode(secret, code)
        if (isValid) {
            _adminTotpConfirmed.value = true
        }
        return isValid
    }

    enum class CloudProtocol {
        WEBDAV, FTP, SFTP, SMB
    }

    data class SetupUser(
        val name: String,
        val password: String? = null
    )

    private val _userList = MutableStateFlow<List<SetupUser>>(emptyList())
    val userList = _userList.asStateFlow()

    fun addUser(name: String) {
        if (_userList.value.none { it.name == name }) {
            _userList.value += SetupUser(name)
        }
    }

    fun removeUser(user: SetupUser) {
        _userList.value -= user
    }

    fun updateUser(oldUser: SetupUser, newUser: SetupUser) {
        _userList.value = _userList.value.map { if (it == oldUser) newUser else it }
    }

    fun completeSetup(
        adminName: String,
        adminPass: String,
        users: List<SetupUser>,
        host: String,
        port: String,
        path: String,
        protocol: CloudProtocol,
        webDavUser: String,
        webDavPass: String,
        warehouseName: String,
        height: Int,
        length: Int
    ) {
        if (!_pathValidated.value) {
            _migrationError.value = "Bitte wählen und bestätigen Sie zuerst einen Cloud-Pfad."
            _currentStep.value = SetupStep.WEBDAV
            return
        }

        _currentStep.value = SetupStep.FINISHING
        viewModelScope.launch {
            // Clean host of any existing schemes
            val cleanHost = host.trim()
                .removePrefix("http://").removePrefix("https://")
                .removePrefix("webdav://").removePrefix("webdavs://")
                .removePrefix("ftp://").removePrefix("sftp://")
                .removePrefix("smb://")
                .substringBefore("/") 
            
            val scheme = when(protocol) {
                CloudProtocol.WEBDAV -> "https"
                CloudProtocol.FTP -> "ftp"
                CloudProtocol.SFTP -> "sftp"
                CloudProtocol.SMB -> "smb"
            }
            val fullUrl = "$scheme://$cleanHost${if(port.isNotEmpty()) ":$port" else ""}$path"

            // Save Cloud
            preferenceManager.saveWebDavSettings(fullUrl, webDavUser, webDavPass, protocol.name)

            if (_isMigrating.value) {
                // Force download migration data
                try {
                    val cloudData = repository.forceDownloadMigration()
                    
                    if (cloudData != null && cloudData.users.isNotEmpty()) {
                        _importedUsers.value = cloudData.users.map { it.name }
                        _importedWarehouses.value = cloudData.warehouses.map { it.name }
                        kotlinx.coroutines.delay(1000)
                        _currentStep.value = SetupStep.SUMMARY
                    } else if (cloudData == null) {
                        _migrationError.value = "Migration fehlgeschlagen: Konnte keine Daten vom Server laden."
                        _currentStep.value = SetupStep.WEBDAV
                    } else {
                        _migrationError.value = "Migration fehlgeschlagen: Die heruntergeladene Datenbank enthält keine Benutzer."
                        _currentStep.value = SetupStep.WEBDAV
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (e.message == "AUTH_FAILED") {
                        _migrationError.value = "Anmeldung fehlgeschlagen. Bitte prüfen Sie Benutzername und Passwort."
                    } else {
                        _migrationError.value = "Kritischer Fehler bei der Migration: ${e.localizedMessage ?: e.message}"
                    }
                    _currentStep.value = SetupStep.WEBDAV
                }
            } else {
                try {
                    // Save Admin with hashed password
                    val hashedPass = SecurityUtils.hashPassword(adminPass)
                    preferenceManager.setAdminPassword(hashedPass)
                    repository.insertUser(User(
                        id = "admin", 
                        name = adminName, 
                        role = UserRole.ADMIN, 
                        passwordHash = hashedPass,
                        totpSecret = _adminTotpSecret.value
                    ))
                    
                    // Save Users
                    users.forEach { setupUser ->
                        val hash = setupUser.password?.let { SecurityUtils.hashPassword(it) }
                        repository.insertUser(User(
                            id = setupUser.name.lowercase().replace(" ", "_"), 
                            name = setupUser.name, 
                            role = UserRole.USER,
                            passwordHash = hash
                        ))
                    }
                    
                    // Initial Warehouse
                    repository.createWarehouse(warehouseName, height, length)
                    
                    // Prepare summary for manual setup too
                    _importedUsers.value = listOf(adminName) + users.map { it.name }
                    _importedWarehouses.value = listOf(warehouseName)
                    
                    kotlinx.coroutines.delay(1000)
                    _currentStep.value = SetupStep.SUMMARY
                } catch (e: Exception) {
                    e.printStackTrace()
                    _migrationError.value = "Fehler beim Speichern der Daten: ${e.message}"
                    _currentStep.value = SetupStep.ADMIN
                }
            }
        }
    }

    fun finishSetup() {
        _setupError.value = null
        _currentStep.value = SetupStep.FINISHING
        viewModelScope.launch {
            try {
                if (!_isMigrating.value) {
                    if (!repository.uploadSetupData()) {
                        _setupError.value = "Datenbank konnte nicht in die Cloud hochgeladen werden. Bitte Verbindung und Zielpfad prüfen und erneut versuchen."
                        _currentStep.value = SetupStep.SUMMARY
                        return@launch
                    }
                }
                preferenceManager.setSetupCompleted(true)
            } catch (e: Exception) {
                e.printStackTrace()
                _setupError.value = "Cloud-Upload fehlgeschlagen: ${e.localizedMessage ?: e.message}"
                _currentStep.value = SetupStep.SUMMARY
            }
        }
    }

    fun testWebDav(protocol: CloudProtocol, host: String, port: String, user: String, pass: String) {
        viewModelScope.launch {
            _isTesting.value = true
            _isWebDavValid.value = null
            _pathValidated.value = false // Reset path validation on new test
            _migrationError.value = null
            
            val cleanHost = host.trim()
                .removePrefix("http://").removePrefix("https://")
                .removePrefix("webdav://").removePrefix("webdavs://")
                .removePrefix("ftp://").removePrefix("sftp://")
                .removePrefix("smb://")
                .substringBefore("/")

            val scheme = when(protocol) {
                CloudProtocol.WEBDAV -> "https"
                CloudProtocol.FTP -> "ftp"
                CloudProtocol.SFTP -> "sftp"
                CloudProtocol.SMB -> "smb"
            }
            val fullUrl = "$scheme://$cleanHost${if(port.isNotEmpty()) ":$port" else ""}"
            
            // Temporarily set protocol and url to test the right service
            preferenceManager.saveWebDavSettings(fullUrl, user, pass, protocol.name)
            
            try {
                val service = syncProvider.getService()
                service.configure(fullUrl, user, pass)
                
                kotlinx.coroutines.delay(2000)
                
                // Connection test: listing folders at root is a good "access granted" test.
                service.listFolders("/")
                _isWebDavValid.value = true 
            } catch (e: Exception) {
                e.printStackTrace()
                _isWebDavValid.value = false
                if (e.message == "AUTH_FAILED") {
                    _migrationError.value = "Anmeldung fehlgeschlagen. Bitte prüfen Sie Benutzername und Passwort."
                } else {
                    _migrationError.value = "Verbindung fehlgeschlagen: ${e.message}"
                }
            } finally {
                _isTesting.value = false
            }
        }
    }

    fun browseFolders(protocol: CloudProtocol, host: String, port: String, user: String, pass: String, currentPath: String) {
        viewModelScope.launch {
            _isBrowsing.value = true
            
            val cleanHost = host.trim()
                .removePrefix("http://").removePrefix("https://")
                .removePrefix("webdav://").removePrefix("webdavs://")
                .removePrefix("ftp://").removePrefix("sftp://")
                .removePrefix("smb://")
                .substringBefore("/")

            val scheme = when(protocol) {
                CloudProtocol.WEBDAV -> "https"
                CloudProtocol.FTP -> "ftp"
                CloudProtocol.SFTP -> "sftp"
                CloudProtocol.SMB -> "smb"
            }
            val fullUrl = "$scheme://$cleanHost${if(port.isNotEmpty()) ":$port" else ""}"
            
            try {
                val service = syncProvider.getService()
                service.configure(fullUrl, user, pass)
                val folders = service.listFolders(currentPath).map { RemoteItem(it, isFolder = true) }
                val files = service.listFiles(currentPath).map { RemoteItem(it, isFolder = false) }
                _remoteItems.value = (folders + files).sortedBy { it.name.lowercase() }
            } catch (e: Exception) {
                e.printStackTrace()
                _remoteItems.value = emptyList()
            } finally {
                _isBrowsing.value = false
            }
        }
    }

    fun createFolder(protocol: CloudProtocol, host: String, port: String, user: String, pass: String, currentPath: String, folderName: String) {
        viewModelScope.launch {
            _isBrowsing.value = true
            
            val cleanHost = host.trim()
                .removePrefix("http://").removePrefix("https://")
                .removePrefix("webdav://").removePrefix("webdavs://")
                .removePrefix("ftp://").removePrefix("sftp://")
                .removePrefix("smb://")
                .substringBefore("/")

            val scheme = when(protocol) {
                CloudProtocol.WEBDAV -> "https"
                CloudProtocol.FTP -> "ftp"
                CloudProtocol.SFTP -> "sftp"
                CloudProtocol.SMB -> "smb"
            }
            val fullUrl = "$scheme://$cleanHost${if(port.isNotEmpty()) ":$port" else ""}"
            
            try {
                val service = syncProvider.getService()
                service.configure(fullUrl, user, pass)
                val success = service.createFolder(folderName, currentPath)
                if (success) {
                    // Refresh current folder
                    val folders = service.listFolders(currentPath).map { RemoteItem(it, isFolder = true) }
                    val files = service.listFiles(currentPath).map { RemoteItem(it, isFolder = false) }
                    _remoteItems.value = (folders + files).sortedBy { it.name.lowercase() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isBrowsing.value = false
            }
        }
    }

    suspend fun checkDatabaseExists(
        protocol: CloudProtocol,
        host: String,
        port: String,
        user: String,
        pass: String,
        path: String
    ): Boolean {
        _isTesting.value = true
        _migrationError.value = null
        
        val cleanHost = host.trim()
            .removePrefix("http://").removePrefix("https://")
            .removePrefix("webdav://").removePrefix("webdavs://")
            .removePrefix("ftp://").removePrefix("sftp://")
            .removePrefix("smb://")
            .substringBefore("/")

        val scheme = when(protocol) {
            CloudProtocol.WEBDAV -> "https"
            CloudProtocol.FTP -> "ftp"
            CloudProtocol.SFTP -> "sftp"
            CloudProtocol.SMB -> "smb"
        }
        val fullUrl = "$scheme://$cleanHost${if(port.isNotEmpty()) ":$port" else ""}$path"
        
        return try {
            val service = syncProvider.getService()
            service.configure(fullUrl, user, pass)
            val primaryResponse = service.getFileWithMeta("inventory.isdb")
            val response = if (_isMigrating.value && primaryResponse.isNotFound) {
                service.getFileWithMeta("inventory_sync.json")
            } else {
                primaryResponse
            }
            if (response.isError) {
                _migrationError.value = "Fehler beim Zugriff auf den gewählten Pfad."
                return false
            }
            val exists = !response.isNotFound && response.content != null

            var validData = false
            if (_isMigrating.value) {
                if (exists) {
                    try {
                        val data = gson.fromJson(response.content, SyncData::class.java)
                        validData = data.users.isNotEmpty()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                // For new setup, any reachable path is valid
                validData = true 
            }

            _pathValidated.value = (_isMigrating.value && exists && validData) || (!_isMigrating.value && true)
            
            if (_isMigrating.value) {
                if (!exists) {
                    _migrationError.value = "Keine Datenbank im gewählten Ordner gefunden. Bitte wählen Sie einen anderen Pfad."
                } else if (!validData) {
                    _migrationError.value = "Die Datenbank in diesem Ordner enthält keine Benutzerdaten. Migration nicht möglich."
                }
                exists && validData
            } else {
                _pathValidated.value = true
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _migrationError.value = "Fehler beim Zugriff auf den Ordner: ${e.message}"
            false
        } finally {
            _isTesting.value = false
        }
    }
}
