package com.lagersystem.interactivesystem

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lagersystem.interactivesystem.scanner.ScannerManager
import com.lagersystem.interactivesystem.data.PreferenceManager
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.ui.login.LoginScreen
import com.lagersystem.interactivesystem.ui.login.LoginViewModel
import com.lagersystem.interactivesystem.ui.logs.LogScreen
import com.lagersystem.interactivesystem.ui.packinglist.PackingListScreen
import com.lagersystem.interactivesystem.ui.scanner.ItemQrCodeDialog
import com.lagersystem.interactivesystem.ui.scanner.ItemTagActionDialog
import com.lagersystem.interactivesystem.ui.scanner.NfcTagWriteDialog
import com.lagersystem.interactivesystem.ui.scanner.QRScannerDialog
import com.lagersystem.interactivesystem.ui.setup.SetupScreen
import com.lagersystem.interactivesystem.ui.settings.SettingsScreen
import com.lagersystem.interactivesystem.ui.sync.SyncDecisionDialog
import com.lagersystem.interactivesystem.ui.theme.InteractiveSystemTheme
import com.lagersystem.interactivesystem.ui.theme.resolveDarkTheme
import com.lagersystem.interactivesystem.ui.warehouse.LendDialog
import com.lagersystem.interactivesystem.ui.warehouse.WarehouseAdaptiveScreen
import com.lagersystem.interactivesystem.ui.warehouse.WarehouseViewModel
import com.lagersystem.interactivesystem.sync.ReturnReminderWorker
import com.lagersystem.interactivesystem.util.ConnectivityObserver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var scannerManager: ScannerManager
    
    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var repository: InventoryRepository

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        handleIntent(intent)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                preferenceManager.setCurrentUserId(null)
            }
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isSetupCompletedState = preferenceManager.isSetupCompleted.collectAsState(initial = null)
            val currentUserId by preferenceManager.currentUserId.collectAsStateWithLifecycle(initialValue = null)
            val themePreference by preferenceManager.themePreference(currentUserId)
                .collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val users by repository.getAllUsers().collectAsStateWithLifecycle(initialValue = emptyList())
            
            val isSetupCompleted = isSetupCompletedState.value
            
            LaunchedEffect(isSetupCompleted, users) {
                if (isSetupCompleted == true && users.isEmpty()) {
                    preferenceManager.setSetupCompleted(false)
                }
            }

            val status by connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Available)
            val isOffline = status != ConnectivityObserver.Status.Available

            InteractiveSystemTheme(darkTheme = resolveDarkTheme(themePreference, isSystemInDarkTheme())) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
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
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Offline-Modus: Keine Internetverbindung",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                        if (isSetupCompleted == null) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (!isSetupCompleted) {
                            SetupScreen(
                                viewModel = hiltViewModel(),
                                windowWidthSizeClass = windowSizeClass.widthSizeClass
                            )
                        } else if (users.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else {
                            if (currentUserId == null) {
                                LoginScreen(viewModel = hiltViewModel())
                            } else {
                                val currentUser = users.find { it.id == currentUserId }
                                val isAdmin = currentUser?.role == UserRole.ADMIN
                                
                                MainApp(
                                    windowSizeClass, scannerManager, preferenceManager, repository,
                                    isAdmin = isAdmin,
                                    connectivityObserver = connectivityObserver,
                                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (rawMsgs != null && rawMsgs.isNotEmpty()) {
                val msg = rawMsgs[0] as android.nfc.NdefMessage
                val record = msg.records.firstOrNull()
                val payload = if (
                    record?.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)
                ) {
                    val recordPayload = record.payload
                    if (recordPayload.isEmpty()) {
                        ""
                    } else {
                        val languageLength = recordPayload.first().toInt() and 0x3F
                        val contentOffset = languageLength + 1
                        if (contentOffset <= recordPayload.size) {
                            String(
                                recordPayload,
                                contentOffset,
                                recordPayload.size - contentOffset,
                                Charsets.UTF_8
                            )
                        } else {
                            ""
                        }
                    }
                } else {
                    record?.payload?.toString(Charsets.UTF_8).orEmpty()
                }
                lifecycleScope.launch {
                    scannerManager.handleNfcTag(payload)
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            intent.data?.let { uri ->
                lifecycleScope.launch {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val json = inputStream?.bufferedReader()?.use { it.readText() }
                        if (json != null) {
                            repository.importIsdb(json)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp(
    windowSizeClass: androidx.compose.material3.windowsizeclass.WindowSizeClass,
    scannerManager: ScannerManager,
    preferenceManager: PreferenceManager,
    repository: InventoryRepository,
    isAdmin: Boolean,
    connectivityObserver: ConnectivityObserver,
    onOpenNfcSettings: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLendDialogItem by remember { mutableStateOf<Item?>(null) }
    var lendDialogStatus by remember { mutableStateOf(com.lagersystem.interactivesystem.ui.warehouse.ItemLogStatus()) }
    var highlightedItemInvNum by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var showNfcInfo by remember { mutableStateOf(false) }
    var showNfcSettingsDialog by remember { mutableStateOf(false) }
    var showNoNfcDialog by remember { mutableStateOf(false) }
    var showNoCameraDialog by remember { mutableStateOf(false) }
    var itemForTagging by remember { mutableStateOf<Item?>(null) }
    var qrCodePayload by remember { mutableStateOf<String?>(null) }
    var nfcWritePayload by remember { mutableStateOf<String?>(null) }
    
    val warehouseViewModel: WarehouseViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val syncStatus by repository.syncStatus.collectAsStateWithLifecycle()
    val currentUserId by preferenceManager.currentUserId.collectAsStateWithLifecycle(initialValue = "Admin")
    val nfcDisabled by preferenceManager.nfcDisabledPermanent.collectAsStateWithLifecycle(initialValue = false)
    val cameraDisabled by preferenceManager.cameraDisabledPermanent.collectAsStateWithLifecycle(initialValue = false)
    val itemLogStatuses by warehouseViewModel.itemLogStatuses.collectAsStateWithLifecycle()
    val openStatusCount = itemLogStatuses.values.count { it.missing > 0 }

    LaunchedEffect(Unit) {
        repository.checkSync(isFirstCheckAfterSetup = true)
        scannerManager.scanEvents.collectLatest { result ->
            if (result is ScannerManager.ScanResult.Success) {
                val item = warehouseViewModel.getItem(result.inventoryNumber)
                if (item != null) {
                    showLendDialogItem = item
                    lendDialogStatus = warehouseViewModel.getItemLogStatusSync(item.inventoryNumber)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            val status by connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Available)
            val isOffline = status != ConnectivityObserver.Status.Available
            AnimatedVisibility(
                visible = isOffline && currentUserId != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Offline-Erinnerung ($currentUserId): Du bist offline. Bitte vergiss nicht, ausgeliehene Artikel rechtzeitig zurückzugeben!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { 
                        selectedTab = 0 
                        highlightedItemInvNum = null
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Lager") },
                    label = { Text("Lager") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (openStatusCount > 0) {
                                    Badge {
                                        Text(if (openStatusCount > 99) "99+" else openStatusCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = if (openStatusCount > 0) {
                                    "Logs, $openStatusCount offene Statusmeldungen"
                                } else {
                                    "Logs"
                                }
                            )
                        }
                    },
                    label = { Text("Logs") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Einstellungen") },
                    label = { Text("Einstellungen") }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "tabTransition"
        ) { tab ->
            when (tab) {
                0 -> WarehouseAdaptiveScreen(
                    viewModel = warehouseViewModel,
                    windowWidthSizeClass = windowSizeClass.widthSizeClass,
                    highlightedInventoryNumber = highlightedItemInvNum,
                    isAdmin = isAdmin,
                    onCameraClick = {
                        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
                                        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                        if (!hasCamera) {
                            showNoCameraDialog = true
                        } else {
                            showScanner = true
                        }
                    },
                    onNfcClick = {
                        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                        if (nfcAdapter == null) {
                            showNoNfcDialog = true
                        } else if (!nfcAdapter.isEnabled) {
                            showNfcSettingsDialog = true
                        } else {
                            showNfcInfo = true
                        }
                    },
                    onItemClick = { invNum ->
                        scope.launch {
                            val item = warehouseViewModel.getItem(invNum)
                            if (item != null) {
                                lendDialogStatus = warehouseViewModel.getItemLogStatusSync(item.inventoryNumber)
                                showLendDialogItem = item
                            }
                        }
                    },
                    onItemSaved = { itemForTagging = it },
                    nfcDisabled = nfcDisabled,
                    cameraDisabled = cameraDisabled,
                    currentUserId = currentUserId.orEmpty()
                )
                1 -> LogScreen(
                    viewModel = hiltViewModel(),
                    warehouseViewModel,
                    currentUserId = currentUserId.orEmpty(),
                    windowWidthSizeClass = windowSizeClass.widthSizeClass
                )
                2 -> SettingsScreen(
                    preferenceManager = preferenceManager, 
                    repository = repository,
                    connectivityObserver = connectivityObserver
                )
            }
        }
    }

    itemForTagging?.let { item ->
        ItemTagActionDialog(
            inventoryNumber = item.inventoryNumber,
            onShowQr = {
                qrCodePayload = it
                itemForTagging = null
            },
            onWriteNfc = {
                nfcWritePayload = it
                itemForTagging = null
            },
            onDismiss = { itemForTagging = null },
            nfcDisabled = nfcDisabled
        )
    }

    qrCodePayload?.let { payload ->
        ItemQrCodeDialog(
            payload = payload,
            onDismiss = { qrCodePayload = null }
        )
    }

    nfcWritePayload?.let { payload ->
        NfcTagWriteDialog(
            payload = payload,
            onDismiss = { nfcWritePayload = null }
        )
    }

    if (showScanner) {
        QRScannerDialog(
            onCodeScanned = { code ->
                scope.launch { scannerManager.handleQrCode(code) }
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showNfcInfo) {
        AlertDialog(
            onDismissRequest = { showNfcInfo = false },
            title = { Text("NFC Suche") },
            text = { Text("Bitte halten Sie ein Item an die Rückseite des Geräts, um es zu suchen.") },
            confirmButton = { TextButton(onClick = { showNfcInfo = false }) { Text("OK") } }
        )
    }

    if (showNfcSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showNfcSettingsDialog = false },
            title = { Text("NFC deaktiviert") },
            text = { Text("NFC ist deaktiviert. Möchten Sie es in den Einstellungen aktivieren?") },
            confirmButton = {
                Button(onClick = {
                    showNfcSettingsDialog = false
                    onOpenNfcSettings()
                }) { Text("Einstellungen") }
            },
            dismissButton = {
                TextButton(onClick = { showNfcSettingsDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showNoNfcDialog) {
        AlertDialog(
            onDismissRequest = { showNoNfcDialog = false },
            title = { Text("NFC nicht verfügbar") },
            text = { Text("Für dieses Gerät ist kein NFC-Chip vorhanden oder der Zugriff wurde deaktiviert. Du kannst den Zugriff in den App-Eigenschaften überprüfen.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        showNoNfcDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }) { Text("App-Eigenschaften") }
                    TextButton(onClick = {
                        showNoNfcDialog = false
                        scope.launch { preferenceManager.setNfcDisabledPermanent(true) }
                    }) { Text("Ausblenden") }
                }
            }
        )
    }

    if (showNoCameraDialog) {
        AlertDialog(
            onDismissRequest = { showNoCameraDialog = false },
            title = { Text("Kamera nicht verfügbar") },
            text = { Text("Für dieses Gerät ist keine Kamera vorhanden oder der Zugriff wurde deaktiviert. Du kannst den Zugriff in den App-Eigenschaften überprüfen.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        showNoCameraDialog = false
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }) { Text("App-Eigenschaften") }
                    TextButton(onClick = {
                        showNoCameraDialog = false
                        scope.launch { preferenceManager.setCameraDisabledPermanent(true) }
                    }) { Text("Ausblenden") }
                }
            }
        )
    }

    if (showLendDialogItem != null) {
        LendDialog(
            item = showLendDialogItem!!,
            borrowedCount = lendDialogStatus.borrowed,
            missingCount = lendDialogStatus.missing,
            onConfirm = { amount, action ->
                scope.launch {
                    val userId = currentUserId ?: "Admin"
                    when (action) {
                        com.lagersystem.interactivesystem.data.log.ActionType.LEND -> {
                            val inventoryNumber = showLendDialogItem!!.inventoryNumber
                            val loanLogId = warehouseViewModel.lendItem(inventoryNumber, amount, userId)
                            if (loanLogId >= 0L) {
                                ReturnReminderWorker.schedule(context, userId, inventoryNumber, loanLogId)
                            }
                        }
                        com.lagersystem.interactivesystem.data.log.ActionType.RETURN ->
                            warehouseViewModel.returnItem(showLendDialogItem!!.inventoryNumber, amount, userId)
                        com.lagersystem.interactivesystem.data.log.ActionType.MISSING ->
                            warehouseViewModel.reportMissing(showLendDialogItem!!.inventoryNumber, amount, userId)
                        com.lagersystem.interactivesystem.data.log.ActionType.FOUND ->
                            warehouseViewModel.reportFound(showLendDialogItem!!.inventoryNumber, amount, userId)
                        else -> Unit
                    }
                    showLendDialogItem = null
                }
            },
            onDismiss = { showLendDialogItem = null }
        )
    }

    when (val status = syncStatus) {
        is InventoryRepository.SyncStatus.Conflict -> {
            SyncDecisionDialog(
                onMerge = { scope.launch { repository.performMerge(status.cloudData, status.etag) } },
                onDownload = { scope.launch { repository.performOverwriteFromCloud(status.cloudData, status.etag) } },
                onDismiss = { /* Optionally handle dismissal */ }
            )
        }
        else -> {}
    }
}
