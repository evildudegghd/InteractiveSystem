package com.lagersystem.interactivesystem.ui.setup

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagersystem.interactivesystem.util.SecurityUtils
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.util.ConnectivityObserver
import com.lagersystem.interactivesystem.util.TotpUtils
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    windowWidthSizeClass: WindowWidthSizeClass
) {
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val isMigrating by viewModel.isMigrating.collectAsStateWithLifecycle()
    val isWebDavValid by viewModel.isWebDavValid.collectAsStateWithLifecycle()
    val pathValidated by viewModel.pathValidated.collectAsStateWithLifecycle()
    val importedUsers by viewModel.importedUsers.collectAsStateWithLifecycle()
    val importedWarehouses by viewModel.importedWarehouses.collectAsStateWithLifecycle()
    val migrationError by viewModel.migrationError.collectAsStateWithLifecycle()
    val setupError by viewModel.setupError.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val userList by viewModel.userList.collectAsStateWithLifecycle()
    val remoteItems by viewModel.remoteItems.collectAsStateWithLifecycle()
    val isBrowsing by viewModel.isBrowsing.collectAsStateWithLifecycle()
    val adminTotpSecret by viewModel.adminTotpSecret.collectAsStateWithLifecycle()
    val adminTotpConfirmed by viewModel.adminTotpConfirmed.collectAsStateWithLifecycle()

    val connectivityStatus by viewModel.connectivityObserver.observe().collectAsState(initial = ConnectivityObserver.Status.Available)
    val isOffline = connectivityStatus != ConnectivityObserver.Status.Available

    var adminName by remember { mutableStateOf("Admin") }
    var adminPass by remember { mutableStateOf("") }
    val isPassValid = remember(adminPass) { SecurityUtils.isValidPassword(adminPass) }
    
    var newUserName by remember { mutableStateOf("") }
    var editingUser by remember { mutableStateOf<SetupViewModel.SetupUser?>(null) }

    var cloudProtocol by remember { mutableStateOf(SetupViewModel.CloudProtocol.WEBDAV) }
    var protocolExpanded by remember { mutableStateOf(false) }
    var cloudUrl by remember { mutableStateOf("") }
    var cloudPort by remember { mutableStateOf("") }
    var cloudUser by remember { mutableStateOf("") }
    var cloudPass by remember { mutableStateOf("") }
    var cloudPath by remember { mutableStateOf("/LagerData") }
    var showFolderPicker by remember { mutableStateOf(false) }
    var cloudConnectionChanged by remember { mutableStateOf(false) }

    LaunchedEffect(isWebDavValid) {
        if (isWebDavValid == true) cloudConnectionChanged = false
    }

    var warehouseName by remember { mutableStateOf("Hauptlager") }

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val onSetupClick = {
        if (currentStep == SetupViewModel.SetupStep.SUMMARY) {
            viewModel.finishSetup()
        } else if (currentStep == SetupViewModel.SetupStep.WAREHOUSE || (currentStep == SetupViewModel.SetupStep.WEBDAV && isMigrating)) {
            viewModel.completeSetup(
                adminName, adminPass, userList,
                cloudUrl, cloudPort, cloudPath,
                cloudProtocol,
                cloudUser, cloudPass,
                warehouseName, 1, 1
            )
        } else {
            viewModel.nextStep()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentStep != SetupViewModel.SetupStep.FINISHING) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = if (currentStep == SetupViewModel.SetupStep.INSTRUCTIONS) Arrangement.Center else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep != SetupViewModel.SetupStep.INSTRUCTIONS) {
                            OutlinedButton(
                                onClick = {
                                    if (currentStep == SetupViewModel.SetupStep.ADMIN_TOTP) {
                                        adminPass = ""
                                    }
                                    viewModel.previousStep()
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Zurück")
                            }
                        }

                        if (currentStep != SetupViewModel.SetupStep.DATA_DECISION) {
                            val isNextEnabled = when (currentStep) {
                                SetupViewModel.SetupStep.ADMIN -> adminName.isNotBlank() && isPassValid
                                SetupViewModel.SetupStep.ADMIN_TOTP -> adminTotpConfirmed
                                SetupViewModel.SetupStep.WEBDAV -> isWebDavValid == true && pathValidated
                                else -> true
                            }
                            val nextButtonText = when (currentStep) {
                                SetupViewModel.SetupStep.SUMMARY -> "Fertig"
                                SetupViewModel.SetupStep.WAREHOUSE -> "Einrichten"
                                SetupViewModel.SetupStep.WEBDAV -> if (isMigrating) "Migrieren" else "Weiter"
                                else -> "Weiter"
                            }
                            Button(
                                onClick = onSetupClick,
                                enabled = isNextEnabled,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(nextButtonText)
                                if (currentStep != SetupViewModel.SetupStep.SUMMARY) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded
        val cardModifier = if (isExpanded) Modifier.width(700.dp) else Modifier.fillMaxWidth()

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isExpanded) 24.dp else 16.dp),
                shape = if (isExpanded) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 8.dp else 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedContent(
                            targetState = currentStep,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            transitionSpec = {
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            },
                            label = "setupTransition"
                        ) { step ->
                            Column(
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                when (step) {
                                    SetupViewModel.SetupStep.FINISHING -> {
                                        Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(Modifier.height(16.dp))
                                                val finishingText = if (isMigrating) {
                                                    "Daten werden heruntergeladen..."
                                                } else {
                                                    "Daten werden in $cloudPath hochgeladen..."
                                                }
                                                Text(finishingText, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                    SetupViewModel.SetupStep.INSTRUCTIONS -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(96.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Inventory2,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(48.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(24.dp))
                                            Text(
                                                "Willkommen im Lagersystem",
                                                style = MaterialTheme.typography.headlineMedium,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "Ihr professionelles Tool zur effizienten Bestandsverwaltung und Lagerdigitalisierung.",
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(32.dp))
                                            
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(20.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                            modifier = Modifier.size(44.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                            }
                                                        }
                                                        Spacer(Modifier.width(16.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Admin & 2FA Sicherheit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                                            Spacer(Modifier.height(2.dp))
                                                            Text("Richten Sie einen geschützten Administrator-Account mit Zwei-Faktor-Authentifizierung ein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                            modifier = Modifier.size(44.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                            }
                                                        }
                                                        Spacer(Modifier.width(16.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Benutzerverwaltung", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                                            Spacer(Modifier.height(2.dp))
                                                            Text("Fügen Sie Teammitglieder hinzu und vergeben Sie Berechtigungen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                            modifier = Modifier.size(44.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                                            }
                                                        }
                                                        Spacer(Modifier.width(16.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Cloud-Synchronisation", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                                            Spacer(Modifier.height(2.dp))
                                                            Text("Verbinden Sie die App mit WebDAV, FTP, SFTP oder SMB für automatische Backups.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    SetupViewModel.SetupStep.DATA_DECISION -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Wie möchten Sie starten?", style = MaterialTheme.typography.headlineSmall)
                                            Spacer(Modifier.height(32.dp))
                                            
                                            Card(
                                                modifier = cardModifier
                                                    .clickable { 
                                                        viewModel.setMigrating(false)
                                                        viewModel.nextStep()
                                                    },
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                            ) {
                                                Column(Modifier.padding(24.dp)) {
                                                    Icon(Icons.Default.AddCircle, null, Modifier.size(48.dp))
                                                    Spacer(Modifier.height(16.dp))
                                                    Text("Neues Lager erstellen", style = MaterialTheme.typography.titleLarge)
                                                    Text("Beginnen Sie mit einer leeren Datenbank und richten Sie Admin, Benutzer und Lager manuell ein.")
                                                }
                                            }
                                            
                                            Spacer(Modifier.height(16.dp))
                                            
                                            Card(
                                                modifier = cardModifier
                                                    .clickable { 
                                                        viewModel.setMigrating(true)
                                                        viewModel.nextStep()
                                                    },
                                                shape = RoundedCornerShape(24.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                            ) {
                                                Column(Modifier.padding(24.dp)) {
                                                    Icon(Icons.Default.CloudDownload, null, Modifier.size(48.dp))
                                                    Spacer(Modifier.height(16.dp))
                                                    Text("Daten migrieren", style = MaterialTheme.typography.titleLarge)
                                                    Text("Verbinden Sie sich mit Ihrer Cloud, um vorhandene Lagerdaten, Benutzer und Einstellungen zu übernehmen.")
                                                }
                                            }
                                        }
                                    }
                        SetupViewModel.SetupStep.ADMIN -> {
                            Text("Admin-Konto einrichten", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(16.dp))
                            TextField(
                                value = adminName, 
                                onValueChange = { adminName = it }, 
                                label = { Text("Benutzername des Admins") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                            )
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = adminPass,
                                onValueChange = { adminPass = it },
                                label = { Text("Passwort des Admins") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                isError = adminPass.isNotEmpty() && !isPassValid,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if(isPassValid) onSetupClick() }),
                                supportingText = {
                                    if (adminPass.isNotEmpty() && !isPassValid) {
                                        Text("Min. 8 Zeichen, Groß-/Kleinbuchstaben, Zahl & Sonderzeichen")
                                    }
                                }
                            )
                        }
                        SetupViewModel.SetupStep.ADMIN_TOTP -> {
                            Text("Zwei-Faktor-Authentifizierung (2FA)", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(16.dp))
                            Text("Für das Admin-Konto wird eine zusätzliche Sicherheitsebene eingerichtet.")
                            Spacer(Modifier.height(24.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.VpnKey, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Geheimschlüssel", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        adminTotpSecret ?: "Wird generiert...",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    
                                    if (adminTotpSecret != null) {
                                        val qrBitmap = remember(adminTotpSecret) {
                                            val uri = TotpUtils.generateOtpAuthUri(adminTotpSecret!!, adminName)
                                            try {
                                                TotpUtils.generateQrCode(uri)
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        
                                        qrBitmap?.let {
                                            Card(
                                                modifier = Modifier.size(200.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                elevation = CardDefaults.cardElevation(4.dp)
                                            ) {
                                                Image(
                                                    bitmap = it.asImageBitmap(),
                                                    contentDescription = "2FA QR Code",
                                                    modifier = Modifier.fillMaxSize().padding(8.dp)
                                                )
                                            }
                                            Spacer(Modifier.height(16.dp))
                                        }
                                    }

                                    Text(
                                        "Scannen Sie diesen QR-Code oder geben Sie den Schlüssel manuell in Ihre Authenticator-App ein.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                    
                                    Spacer(Modifier.height(24.dp))
                                    
                                    var confirmationCode by remember { mutableStateOf("") }
                                    var codeError by remember { mutableStateOf(false) }
                                    
                                    TextField(
                                        value = confirmationCode,
                                        onValueChange = { 
                                            if (it.length <= 6) {
                                                confirmationCode = it
                                                codeError = false
                                                if (it.length == 6) {
                                                    if (!viewModel.verifyAdminTotpConfirmation(it)) {
                                                        codeError = true
                                                    }
                                                }
                                            }
                                        },
                                        label = { Text("Bestätigungscode (6 Stellen)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        isError = codeError,
                                        supportingText = {
                                            if (codeError) Text("Ungültiger Code")
                                            else if (adminTotpConfirmed) Text("Code bestätigt!", color = MaterialTheme.colorScheme.primary)
                                        },
                                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = if(adminTotpConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                                    )
                                }
                            }
                        }
                        SetupViewModel.SetupStep.USERS -> {
                            val addUser = {
                                if (newUserName.isNotBlank()) {
                                    viewModel.addUser(newUserName)
                                    newUserName = ""
                                }
                            }
                            Text("Weitere Benutzer hinzufügen", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = newUserName, 
                                    onValueChange = { newUserName = it }, 
                                    label = { Text("Benutzername") }, 
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { addUser() })
                                )
                                IconButton(onClick = addUser) { Icon(Icons.Default.Add, "Add") }
                            }
                            Spacer(Modifier.height(16.dp))
                            userList.forEach { user ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingUser = user },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    ListItem(
                                        headlineContent = { Text(user.name) },
                                        trailingContent = {
                                            IconButton(onClick = { viewModel.removeUser(user) }) {
                                                Icon(Icons.Default.Delete, "Delete")
                                            }
                                        },
                                        leadingContent = {
                                            if (user.password != null) Icon(Icons.Default.Lock, null)
                                            else Icon(Icons.Default.Person, null)
                                        }
                                    )
                                }
                            }
                        }
                        SetupViewModel.SetupStep.WEBDAV -> {
                            Text("${cloudProtocol.name}-Synchronisierung", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    OutlinedButton(onClick = { protocolExpanded = true }, shape = RoundedCornerShape(16.dp)) {
                                        Text(cloudProtocol.name)
                                        Icon(Icons.Default.ArrowDropDown, null)
                                    }
                                    DropdownMenu(expanded = protocolExpanded, onDismissRequest = { protocolExpanded = false }) {
                                        SetupViewModel.CloudProtocol.entries.forEach { proto ->
                                            DropdownMenuItem(text = { Text(proto.name) }, onClick = { 
                                                cloudProtocol = proto
                                                cloudConnectionChanged = cloudConnectionChanged || isWebDavValid == true || pathValidated
                                                viewModel.invalidateCloudConnection()
                                                protocolExpanded = false 
                                                cloudPort = when(proto) {
                                                    SetupViewModel.CloudProtocol.FTP -> "21"
                                                    SetupViewModel.CloudProtocol.SFTP -> "22"
                                                    else -> ""
                                                }
                                            })
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                TextField(
                                    value = cloudUrl, 
                                    onValueChange = {
                                        cloudUrl = it
                                        cloudConnectionChanged = cloudConnectionChanged || isWebDavValid == true || pathValidated
                                        viewModel.invalidateCloudConnection()
                                    },
                                    label = { Text("Server-Adresse") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                                )
                            }
                            if (cloudProtocol == SetupViewModel.CloudProtocol.FTP || cloudProtocol == SetupViewModel.CloudProtocol.SFTP) {
                                TextField(
                                    value = cloudPort, 
                                    onValueChange = {
                                        cloudPort = it
                                        cloudConnectionChanged = cloudConnectionChanged || isWebDavValid == true || pathValidated
                                        viewModel.invalidateCloudConnection()
                                    },
                                    label = { Text("Port") }, 
                                    modifier = Modifier.fillMaxWidth(), 
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                                )
                            }
                            TextField(
                                value = cloudUser, 
                                onValueChange = {
                                    cloudUser = it
                                    cloudConnectionChanged = cloudConnectionChanged || isWebDavValid == true || pathValidated
                                    viewModel.invalidateCloudConnection()
                                },
                                label = { Text("Benutzername") }, 
                                modifier = Modifier.fillMaxWidth(), 
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                            )
                            TextField(
                                value = cloudPass,
                                onValueChange = {
                                    cloudPass = it
                                    cloudConnectionChanged = cloudConnectionChanged || isWebDavValid == true || pathValidated
                                    viewModel.invalidateCloudConnection()
                                },
                                label = { Text("Passwort") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(imeAction = if(isMigrating) ImeAction.Done else ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                    onDone = { 
                                        if (isWebDavValid == true) {
                                            if (!pathValidated) {
                                                // Always show picker if path not yet validated
                                                viewModel.browseFolders(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath)
                                                showFolderPicker = true
                                            } else {
                                                onSetupClick()
                                            }
                                        } else {
                                            // Trigger test if not tested yet
                                            viewModel.testWebDav(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass)
                                        }
                                    }
                                )
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            val testButtonAction = {
                                if (isWebDavValid == true) {
                                    viewModel.browseFolders(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath)
                                    showFolderPicker = true
                                } else {
                                    viewModel.testWebDav(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass)
                                }
                            }

                            Button(
                                onClick = testButtonAction,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = if(isWebDavValid == true) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.buttonColors()
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Verbinden...")
                                } else if (isWebDavValid == true) {
                                    Icon(Icons.Default.FolderOpen, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pfad wählen")
                                } else {
                                    Text("Verbindung herstellen")
                                }
                            }
                            
                            if (cloudConnectionChanged) {
                                Text(
                                    "Server- oder Anmeldedaten geändert. Bitte Verbindung erneut herstellen und den Pfad bestätigen.",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            if (isWebDavValid == true) {
                                LaunchedEffect(Unit) {
                                    if (!pathValidated) {
                                        if (cloudPath == "/LagerData") { // Only reset if default
                                            cloudPath = "/"
                                        }
                                        viewModel.browseFolders(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath)
                                        showFolderPicker = true
                                    }
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Verbindung erfolgreich hergestellt!", color = MaterialTheme.colorScheme.primary)
                                    
                                    if (pathValidated) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, "Validiert", tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Pfad: $cloudPath", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            if (isWebDavValid == false) {
                                Text(
                                    text = migrationError ?: "Verbindung fehlgeschlagen.", 
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        SetupViewModel.SetupStep.WAREHOUSE -> {
                            Text("Lager erstellen", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(16.dp))
                            TextField(value = warehouseName, onValueChange = { warehouseName = it }, label = { Text("Lagername") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Hinweis: Lager werden jetzt immer als übersichtliche Liste angezeigt. Die Gitter-Funktion wurde für bessere Bedienbarkeit entfernt.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        SetupViewModel.SetupStep.SUMMARY -> {
                            Text("Einrichtung abgeschlossen!", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(16.dp))
                            Text("Folgende Daten wurden erfolgreich ${if(isMigrating) "importiert" else "erstellt"}:")
                            if (setupError != null) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    setupError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Benutzer (${importedUsers.size})", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    importedUsers.forEach { name ->
                                        Text("• $name", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Lager (${importedWarehouses.size})", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    importedWarehouses.forEach { name ->
                                        Text("• $name", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    // User Edit Dialog
    if (editingUser != null) {
        var editName by remember { mutableStateOf(editingUser!!.name) }
        var editPass by remember { mutableStateOf(editingUser!!.password ?: "") }
        val isEditPassValid = editPass.isEmpty() || editPass.length >= 4

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("${editingUser!!.name} bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = editName, 
                        onValueChange = { editName = it }, 
                        label = { Text("Name") }, 
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                    )
                    TextField(
                        value = editPass, 
                        onValueChange = { editPass = it }, 
                        label = { Text("Passwort (mind. 4 Zeichen)") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        isError = !isEditPassValid,
                        supportingText = {
                            if (!isEditPassValid) {
                                Text("Das Passwort muss mindestens 4 Zeichen lang sein.")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (editName.isNotBlank() && isEditPassValid) {
                                viewModel.updateUser(editingUser!!, SetupViewModel.SetupUser(editName, editPass.ifEmpty { null }))
                                editingUser = null
                            }
                        })
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = isEditPassValid,
                    onClick = {
                        viewModel.updateUser(editingUser!!, SetupViewModel.SetupUser(editName, editPass.ifEmpty { null }))
                        editingUser = null
                    }
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) { Text("Abbrechen") }
            }
        )
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
                        Text("Pfad auf dem Server wählen")
                        Text(
                            text = "Aktuell: $cloudPath",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, "Neuer Ordner")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (migrationError != null) {
                        Text(
                            text = migrationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
                        if (isBrowsing) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text("Verzeichnisse werden geladen...", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            LazyColumn {
                                if (cloudPath != "/" && cloudPath.isNotEmpty()) {
                                    item {
                                        ListItem(
                                            headlineContent = { Text(".. (Übergeordneter Ordner)") },
                                            leadingContent = { Icon(Icons.Default.ArrowUpward, null) },
                                            modifier = Modifier.clickable { 
                                                val parent = if (cloudPath.count { it == '/' } <= 1) "/" 
                                                             else cloudPath.substringBeforeLast("/")
                                                cloudPath = if (parent.isEmpty()) "/" else parent
                                                viewModel.browseFolders(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath)
                                            }
                                        )
                                    }
                                }
                                
                                if (remoteItems.isEmpty()) {
                                    item {
                                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                            Text("Keine Inhalte gefunden", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }

                                items(remoteItems) { item ->
                                    ListItem(
                                        headlineContent = { 
                                            Text(
                                                text = item.name,
                                                color = if (item.isFolder) MaterialTheme.colorScheme.onSurface 
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            ) 
                                        },
                                        leadingContent = { 
                                            Icon(
                                                imageVector = if (item.isFolder) Icons.Default.Folder else Icons.Default.Description, 
                                                contentDescription = null,
                                                tint = if (item.isFolder) MaterialTheme.colorScheme.primary 
                                                       else MaterialTheme.colorScheme.outline
                                            ) 
                                        },
                                        modifier = Modifier.clickable(enabled = item.isFolder) { 
                                            cloudPath = if (cloudPath.endsWith("/")) "$cloudPath${item.name}" else "$cloudPath/${item.name}"
                                            viewModel.browseFolders(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val scope = rememberCoroutineScope()
                Button(
                    onClick = { 
                        if (isMigrating) {
                            scope.launch {
                                val exists = viewModel.checkDatabaseExists(
                                    cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath
                                )
                                if (exists) {
                                    showFolderPicker = false 
                                    // Removed viewModel.nextStep() to let user confirm on main screen
                                }
                            }
                        } else {
                            scope.launch {
                                viewModel.checkDatabaseExists(
                                    cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath
                                )
                                showFolderPicker = false 
                                viewModel.nextStep()
                            }
                        }
                    },
                    enabled = !isTesting
                ) { 
                    if (isTesting && isMigrating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Prüfe...")
                    } else {
                        Text("Diesen Pfad wählen")
                    }
                }
            },
            dismissButton = {
                Row {
                    IconButton(onClick = { viewModel.browseFolders(cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath) }) {
                        Icon(Icons.Default.Refresh, "Neu laden")
                    }
                    TextButton(onClick = { showFolderPicker = false }) { Text("Abbrechen") }
                }
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
                            viewModel.createFolder(
                                cloudProtocol, cloudUrl, cloudPort, cloudUser, cloudPass, cloudPath, newFolderName
                            )
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    ) { Text("Erstellen") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text("Abbrechen") }
                }
            )
        }
    }
    }
    }
}
