package com.lagersystem.interactivesystem.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.lagersystem.interactivesystem.data.PreferenceManager
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.data.model.SyncData
import com.lagersystem.interactivesystem.util.ConnectivityObserver
import com.lagersystem.interactivesystem.util.SecurityUtils
import com.lagersystem.interactivesystem.util.TotpUtils
import com.lagersystem.interactivesystem.sync.ReturnReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(
    preferenceManager: PreferenceManager,
    repository: InventoryRepository,
    connectivityObserver: ConnectivityObserver
) {
    val scope = rememberCoroutineScope()
    val url by preferenceManager.webDavUrl.collectAsStateWithLifecycle(initialValue = "")
    val cloudProto by preferenceManager.cloudProtocol.collectAsStateWithLifecycle(initialValue = "WEBDAV")
    val user by preferenceManager.webDavUser.collectAsStateWithLifecycle(initialValue = "")
    val pass by preferenceManager.webDavPass.collectAsStateWithLifecycle(initialValue = "")
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    val currentUserId by preferenceManager.currentUserId.collectAsStateWithLifecycle(initialValue = null)
    val users by repository.getAllUsers().collectAsStateWithLifecycle(initialValue = emptyList())
    val currentUser = users.find { it.id == currentUserId }
    val isAdmin = currentUser?.role == UserRole.ADMIN
    val context = LocalContext.current
    val themePreference by preferenceManager.themePreference(currentUserId)
        .collectAsStateWithLifecycle(initialValue = "SYSTEM")
    var remindersEnabled by remember(currentUser?.id) { mutableStateOf(false) }

    var expandedSection by remember { mutableStateOf<String?>(null) }
    var showRollbackDialog by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<String>>(emptyList()) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    var showOwnPasswordDialog by remember { mutableStateOf(false) }

    var showFolderPicker by remember { mutableStateOf(false) }
    var currentRemotePath by remember { mutableStateOf("/") }
    var remoteFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var isBrowsing by remember { mutableStateOf(false) }

    val connectivityStatus by connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Available)
    val isOffline = connectivityStatus != ConnectivityObserver.Status.Available

    LaunchedEffect(currentUser?.id) {
        val userId = currentUser?.id ?: return@LaunchedEffect
        remindersEnabled = preferenceManager.returnRemindersEnabled(userId).first()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val userId = currentUser?.id ?: return@rememberLauncherForActivityResult
        scope.launch {
            if (granted) {
                remindersEnabled = true
                preferenceManager.setReturnRemindersEnabled(userId, true)
                ReturnReminderWorker.rescheduleOutstanding(
                    context,
                    userId,
                    repository.getAllLogsSync()
                )
                snackbarHostState.showSnackbar("Rückgabeerinnerungen aktiviert.")
            } else {
                remindersEnabled = false
                snackbarHostState.showSnackbar("Ohne Benachrichtigungsberechtigung können keine Erinnerungen angezeigt werden.")
            }
        }
    }

    LaunchedEffect(isOffline) {
        if (isOffline) {
            showFolderPicker = false
        }
    }

    var backupToPreview by remember { mutableStateOf<String?>(null) }
    var previewData by remember { mutableStateOf<SyncData?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = isOffline,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).statusBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Offline-Modus: Keine Internetverbindung",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            item {
                Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
            }

            item {
                SettingsAccordion(
                    title = "Persönliche Einstellungen",
                    expanded = expandedSection == "personal",
                    onClick = { expandedSection = if (expandedSection == "personal") null else "personal" }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Darstellung", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SYSTEM" to "System", "LIGHT" to "Hell", "DARK" to "Dunkel").forEach { (value, label) ->
                                FilterChip(
                                    selected = themePreference == value,
                                    onClick = {
                                        currentUserId?.let { id ->
                                            scope.launch { preferenceManager.setThemePreference(id, value) }
                                        }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rückgabeerinnerung", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Push-Mitteilung 24 Stunden nach der Ausleihe, falls das Gerät noch offen ist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = remindersEnabled,
                                onCheckedChange = { enabled ->
                                    val userId = currentUser?.id ?: return@Switch
                                    if (!enabled) {
                                        remindersEnabled = false
                                        scope.launch {
                                            preferenceManager.setReturnRemindersEnabled(userId, false)
                                        }
                                        ReturnReminderWorker.cancelForUser(context, userId)
                                    } else if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        remindersEnabled = true
                                        scope.launch {
                                            preferenceManager.setReturnRemindersEnabled(userId, true)
                                            ReturnReminderWorker.rescheduleOutstanding(
                                                context,
                                                userId,
                                                repository.getAllLogsSync()
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Section: Benutzerverwaltung
            item {
                SettingsAccordion(
                    title = "Benutzerverwaltung",
                    expanded = expandedSection == "user",
                    onClick = { expandedSection = if (expandedSection == "user") null else "user" }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Angemeldet als: ${currentUser?.name}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        
                        users.forEach { user ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = isAdmin) { userToEdit = user },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                ListItem(
                                    headlineContent = { Text(user.name) },
                                    supportingContent = { Text(if(user.role == UserRole.ADMIN) "Admin" else "User") },
                                    leadingContent = {
                                        if (user.passwordHash != null) Icon(Icons.Default.Lock, null)
                                        else Icon(Icons.Default.Person, null)
                                    }
                                )
                            }
                        }

                        if (!isAdmin) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showOwnPasswordDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Lock, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Eigenes Passwort ändern")
                            }
                        }
                        
                        Button(
                            onClick = { scope.launch { preferenceManager.setCurrentUserId(null) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Abmelden / Benutzer wechseln")
                        }
                    }
                }
            }

            // Section: Cloud (Admin only)
            if (isAdmin) {
                item {
                    SettingsAccordion(
                        title = "Cloud & Synchronisierung",
                        expanded = expandedSection == "cloud",
                        onClick = { expandedSection = if (expandedSection == "cloud") null else "cloud" }
                    ) {
                        var urlInput by remember(url) { 
                            mutableStateOf(
                                if (url != null && url!!.contains("://")) url!!.substringAfter("://").substringBefore(":") else url ?: ""
                            ) 
                        }
                        var portInput by remember(url) { 
                            mutableStateOf(
                                if (url != null && url!!.contains(":") && url!!.substringAfterLast(":").all { it.isDigit() }) 
                                    url!!.substringAfterLast(":") 
                                else when(cloudProto) { "FTP" -> "21"; "SFTP" -> "22"; else -> "" }
                            ) 
                        }
                        var userInput by remember(user) { mutableStateOf(user ?: "") }
                        var passInput by remember(pass) { mutableStateOf(pass ?: "") }
                        var protocol by remember(cloudProto) { mutableStateOf(cloudProto ?: "WEBDAV") }
                        var protocolExpanded by remember { mutableStateOf(false) }
                        var settingsError by remember { mutableStateOf<String?>(null) }

                        val protocols = listOf("WEBDAV", "FTP", "SFTP", "SMB")

                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box {
                                OutlinedButton(onClick = { protocolExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                    Text("Protokoll: $protocol")
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = protocolExpanded, onDismissRequest = { protocolExpanded = false }) {
                                    protocols.forEach { proto ->
                                        DropdownMenuItem(text = { Text(proto) }, onClick = { 
                                            protocol = proto
                                            protocolExpanded = false 
                                            portInput = when(proto) {
                                                "FTP" -> "21"
                                                "SFTP" -> "22"
                                                else -> ""
                                            }
                                        })
                                    }
                                }
                            }

                            TextField(value = urlInput, onValueChange = { urlInput = it }, label = { Text("Server Host (z.B. dav.myserver.de)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            
                            if (protocol == "FTP" || protocol == "SFTP") {
                                TextField(value = portInput, onValueChange = { portInput = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            }

                            TextField(value = userInput, onValueChange = { userInput = it }, label = { Text("Benutzername") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            TextField(value = passInput, onValueChange = { passInput = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            
                            if (settingsError != null) {
                                Text(settingsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Button(onClick = { 
                                scope.launch { 
                                    try {
                                        settingsError = null
                                        val cleanHost = urlInput.trim()
                                            .removePrefix("http://").removePrefix("https://")
                                            .removePrefix("webdav://").removePrefix("webdavs://")
                                            .removePrefix("ftp://").removePrefix("sftp://")
                                            .removePrefix("smb://")
                                            .substringBefore("/")

                                        val scheme = when(protocol) {
                                            "FTP" -> "ftp"
                                            "SFTP" -> "sftp"
                                            "SMB" -> "smb"
                                            else -> "https"
                                        }
                                        val fullUrl = "$scheme://$cleanHost${if(portInput.isNotEmpty()) ":$portInput" else ""}"

                                        preferenceManager.saveWebDavSettings(fullUrl, userInput, passInput, protocol)
                                        isBrowsing = true
                                        showFolderPicker = true
                                        remoteFolders = repository.getFolders("/")
                                        currentRemotePath = "/"
                                        isBrowsing = false
                                        snackbarHostState.showSnackbar("Zugangsdaten gespeichert. Bitte Pfad wählen.")
                                    } catch (e: Exception) {
                                        isBrowsing = false
                                        settingsError = "Verbindung fehlgeschlagen: ${e.message}"
                                    }
                                } 
                            }, shape = RoundedCornerShape(16.dp)) {
                                Text("Speichern & Pfad wählen")
                            }
                        }
                    }
                }

                item {
                    SettingsAccordion(
                        title = "System & Wartung",
                        expanded = expandedSection == "system",
                        onClick = { expandedSection = if (expandedSection == "system") null else "system" }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Button(
                                onClick = { 
                                    scope.launch {
                                        backups = repository.listBackups().sortedDescending()
                                        showRollbackDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.History, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Rollback (Backups verwalten)")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFolderPicker) {
        var showNewFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speicherpfad wählen")
                        Text(currentRemotePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Row {
                        IconButton(onClick = {
                            scope.launch {
                                isBrowsing = true
                                try {
                                    remoteFolders = repository.getFolders(currentRemotePath)
                                    snackbarHostState.showSnackbar("Ordner aktualisiert")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Fehler beim Laden: ${e.message}")
                                }
                                isBrowsing = false
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Neu laden")
                        }
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, "Neuer Ordner")
                        }
                    }
                }
            },
            text = {
                Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                    if (isBrowsing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn {
                            if (currentRemotePath != "/") {
                                item {
                                    ListItem(
                                        headlineContent = { Text("..") },
                                        leadingContent = { Icon(Icons.Default.ArrowUpward, null) },
                                        modifier = Modifier.clickable {
                                            currentRemotePath = currentRemotePath.substringBeforeLast("/").ifEmpty { "/" }
                                            scope.launch {
                                                isBrowsing = true
                                                try {
                                                    remoteFolders = repository.getFolders(currentRemotePath)
                                                } catch (e: Exception) {}
                                                isBrowsing = false
                                            }
                                        }
                                    )
                                }
                            }
                            items(remoteFolders) { folder ->
                                ListItem(
                                    headlineContent = { Text(folder) },
                                    leadingContent = { Icon(Icons.Default.Folder, null) },
                                    modifier = Modifier.clickable {
                                        currentRemotePath = if(currentRemotePath.endsWith("/")) "$currentRemotePath$folder" else "$currentRemotePath/$folder"
                                        scope.launch {
                                            isBrowsing = true
                                            try {
                                                remoteFolders = repository.getFolders(currentRemotePath)
                                            } catch (e: Exception) {}
                                            isBrowsing = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.updateCloudPath(currentRemotePath)
                        showFolderPicker = false
                        snackbarHostState.showSnackbar("Pfad aktualisiert: $currentRemotePath")
                        repository.checkSync() 
                    }
                }) { Text("Pfad wählen") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderPicker = false }) { Text("Abbrechen") }
            }
        )

        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Neuen Ordner erstellen") },
                text = {
                    TextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Ordnername") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        enabled = newFolderName.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isBrowsing = true
                                val success = repository.createFolder(newFolderName, currentRemotePath)
                                if (success) {
                                    remoteFolders = repository.getFolders(currentRemotePath)
                                    snackbarHostState.showSnackbar("Ordner erstellt")
                                } else {
                                    snackbarHostState.showSnackbar("Fehler beim Erstellen des Ordners")
                                }
                                isBrowsing = false
                                newFolderName = ""
                                showNewFolderDialog = false
                            }
                        }
                    ) { Text("Erstellen") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text("Abbrechen") }
                }
            )
        }
    }

    if (showRollbackDialog) {
        AlertDialog(
            onDismissRequest = { showRollbackDialog = false },
            title = { Text("Backup wiederherstellen (Rollback)") },
            text = {
                Box(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                    if (backups.isEmpty()) {
                        Text("Keine Backups gefunden.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn {
                            items(backups) { backupName ->
                                ListItem(
                                    headlineContent = { Text(backupName) },
                                    leadingContent = { Icon(Icons.Default.History, null) },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            try {
                                                val result = repository.performRollback(backupName)
                                                if (result != null) {
                                                    snackbarHostState.showSnackbar("Backup $backupName erfolgreich wiederhergestellt")
                                                } else {
                                                    snackbarHostState.showSnackbar("Fehler beim Wiederherstellen des Backups")
                                                }
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("Fehler: ${e.message}")
                                            }
                                            showRollbackDialog = false
                                            repository.checkSync()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRollbackDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showOwnPasswordDialog && currentUser != null) {
        OwnPasswordChangeDialog(
            user = currentUser,
            repository = repository,
            onDismiss = { showOwnPasswordDialog = false },
            onPasswordChanged = {
                showOwnPasswordDialog = false
                scope.launch { snackbarHostState.showSnackbar("Passwort erfolgreich geändert.") }
            }
        )
    }

    if (userToEdit != null) {
        var editName by remember { mutableStateOf(userToEdit!!.name) }
        var editPass by remember { mutableStateOf("") }
        var isTemp by remember { mutableStateOf(userToEdit!!.isTemporaryPassword) }
        val isTargetAdmin = userToEdit!!.role == UserRole.ADMIN
        var editTotpSecret by remember { mutableStateOf(userToEdit!!.totpSecret) }

        AlertDialog(
            onDismissRequest = { userToEdit = null },
            title = { Text("${userToEdit!!.name} bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, shape = RoundedCornerShape(16.dp), singleLine = true)
                    
                    val passValid = if (isTargetAdmin) {
                        editPass.isEmpty() || SecurityUtils.isValidPassword(editPass)
                    } else {
                        editPass.isEmpty() || editPass.length >= 4
                    }
                    
                    TextField(
                        value = editPass, 
                        onValueChange = { editPass = it }, 
                        label = { Text(if (isTargetAdmin) "Neues Admin-Passwort" else "Neues Passwort (min. 4 Zeichen)") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        isError = !passValid,
                        supportingText = { 
                            if (!passValid) {
                                Text(if (isTargetAdmin) "Min. 8 Zeichen, Groß/Klein, Zahl & Sonderzeichen" else "Mindestens 4 Zeichen erforderlich")
                            } else {
                                Text(if (isTargetAdmin) "Leer lassen, um Passwort beizubehalten" else "Leer lassen, um Passwort zu ENTFERNEN")
                            }
                        }
                    )
                    
                    if (!isTargetAdmin) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { isTemp = !isTemp }
                        ) {
                            Checkbox(checked = isTemp, onCheckedChange = { isTemp = it })
                            Spacer(Modifier.width(8.dp))
                            Text("Passwort ist temporär (User muss es ändern)")
                        }
                    }

                    if (isTargetAdmin) {
                        Spacer(Modifier.height(8.dp))
                        Text("Zwei-Faktor-Authentifizierung (2FA)", style = MaterialTheme.typography.titleSmall)
                        if (editTotpSecret != null) {
                            Text("2FA ist aktiv.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            val qrBitmap = remember(editTotpSecret) {
                                val uri = TotpUtils.generateOtpAuthUri(editTotpSecret!!, editName)
                                try { TotpUtils.generateQrCode(uri) } catch (e: Exception) { null }
                            }
                            qrBitmap?.let {
                                Card(
                                    modifier = Modifier.size(150.dp).align(Alignment.CenterHorizontally),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "2FA QR Code",
                                        modifier = Modifier.fillMaxSize().padding(8.dp)
                                    )
                                }
                            }
                            Text("Geheimschlüssel: $editTotpSecret", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("2FA ist nicht eingerichtet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = { editTotpSecret = TotpUtils.generateSecret() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (editTotpSecret == null) "2FA einrichten" else "2FA neu generieren")
                        }
                    }
                }
            },
            confirmButton = {
                val finalPassValid = if (isTargetAdmin) {
                    editPass.isEmpty() || SecurityUtils.isValidPassword(editPass)
                } else {
                    editPass.isEmpty() || editPass.length >= 4
                }
                
                Button(
                    enabled = editName.isNotBlank() && finalPassValid,
                    onClick = {
                        scope.launch {
                            val updatedHash = when {
                                editPass.isNotEmpty() -> SecurityUtils.hashPassword(editPass)
                                isTargetAdmin -> userToEdit!!.passwordHash
                                else -> if(userToEdit!!.passwordHash != null) userToEdit!!.passwordHash else null
                            }
                            
                            val updatedUser = userToEdit!!.copy(
                                name = editName,
                                passwordHash = updatedHash,
                                isTemporaryPassword = isTemp,
                                totpSecret = editTotpSecret
                            )
                            repository.insertUser(updatedUser)
                            if (isTargetAdmin && editPass.isNotEmpty()) {
                                preferenceManager.setAdminPassword(updatedHash!!)
                            }
                            userToEdit = null
                            snackbarHostState.showSnackbar("Benutzer & 2FA aktualisiert")
                        }
                    }
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { userToEdit = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun OwnPasswordChangeDialog(
    user: User,
    repository: InventoryRepository,
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentPassword by remember(user.id) { mutableStateOf("") }
    var newPassword by remember(user.id) { mutableStateOf("") }
    var confirmPassword by remember(user.id) { mutableStateOf("") }
    var error by remember(user.id) { mutableStateOf<String?>(null) }
    val needsCurrentPassword = user.role == UserRole.ADMIN || !user.passwordHash.isNullOrBlank()
    val newPasswordValid = if (user.role == UserRole.ADMIN) {
        SecurityUtils.isValidPassword(newPassword)
    } else {
        newPassword.length >= 4
    }
    val passwordsMatch = newPassword == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigenes Passwort ändern") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (needsCurrentPassword) {
                    TextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; error = null },
                        label = { Text("Aktuelles Passwort") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
                TextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = { Text("Neues Passwort") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = newPassword.isNotEmpty() && !newPasswordValid,
                    supportingText = {
                        Text(
                            if (user.role == UserRole.ADMIN) "Mindestens 8 Zeichen, Groß-/Kleinbuchstaben, Zahl und Sonderzeichen"
                            else "Mindestens 4 Zeichen"
                        )
                    }
                )
                TextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    label = { Text("Neues Passwort wiederholen") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = confirmPassword.isNotEmpty() && !passwordsMatch
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = newPasswordValid && passwordsMatch &&
                    (!needsCurrentPassword || currentPassword.isNotEmpty()),
                onClick = {
                    scope.launch {
                        val result = repository.changeOwnPassword(user, currentPassword, newPassword)
                        if (result == null) onPasswordChanged() else error = result
                    }
                }
            ) {
                Text("Passwort speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun SettingsAccordion(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = expanded) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    content()
                }
            }
        }
    }
}
