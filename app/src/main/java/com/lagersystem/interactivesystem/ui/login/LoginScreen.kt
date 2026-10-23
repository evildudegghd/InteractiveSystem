package com.lagersystem.interactivesystem.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.util.SecurityUtils
import com.lagersystem.interactivesystem.util.TotpUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val scope = rememberCoroutineScope()
    val users by viewModel.users.collectAsStateWithLifecycle()
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp), 
        contentAlignment = Alignment.Center
    ) {
        if (isLoggingIn) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Synchronisation wird vorbereitet...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Wer arbeitet gerade?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Bitte wählen Sie einen Benutzer aus",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))

                if (selectedUser == null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(users) { user ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .clickable {
                                        if (user.role == UserRole.USER && user.passwordHash.isNullOrBlank() && !user.isTemporaryPassword) {
                                            scope.launch {
                                                isLoggingIn = true
                                                delay(500)
                                                viewModel.login(user)
                                            }
                                        } else {
                                            selectedUser = user
                                        }
                                    },
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                            ) {
                                ListItem(
                                    headlineContent = { 
                                        Text(
                                            user.name, 
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ) 
                                    },
                                    supportingContent = { 
                                        Text(
                                            if (user.role == UserRole.ADMIN) "Administrator" else "Benutzer",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        ) 
                                    },
                                    leadingContent = {
                                        Surface(
                                            shape = CircleShape,
                                            color = if(user.role == UserRole.ADMIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (user.passwordHash != null) Icons.Default.Lock else Icons.Default.Person,
                                                    contentDescription = null,
                                                    tint = if(user.role == UserRole.ADMIN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp),
                        shape = RoundedCornerShape(28.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Anmeldung", 
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                selectedUser!!.name, 
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(24.dp))
                            
                            TextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it; errorMessage = null },
                                label = { Text("Passwort") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                isError = errorMessage != null,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent
                                )
                            )

                            if (selectedUser?.role == UserRole.ADMIN) {
                                var showResetDialog by remember { mutableStateOf(false) }
                                
                                Text(
                                    text = "Passwort vergessen?",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .clickable { showResetDialog = true }
                                )

                                if (showResetDialog) {
                                    AdminResetDialog(
                                        admin = selectedUser!!,
                                        onDismiss = { showResetDialog = false },
                                        onReset = { code, newPass ->
                                            scope.launch {
                                                if (viewModel.resetAdminPasswordWithTotp(selectedUser!!, code, newPass)) {
                                                    showResetDialog = false
                                                    errorMessage = "Passwort erfolgreich zurückgesetzt. Bitte anmelden."
                                                    passwordInput = ""
                                                } else {
                                                    errorMessage = "2FA-Code ungültig"
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            AnimatedVisibility(visible = errorMessage != null) {
                                Text(
                                    errorMessage ?: "", 
                                    color = MaterialTheme.colorScheme.error, 
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Spacer(Modifier.height(32.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.login(
                                                selectedUser!!, 
                                                passwordInput
                                            )
                                            when(result) {
                                                LoginViewModel.LoginResult.Success -> {
                                                    isLoggingIn = true
                                                    delay(500)
                                                }
                                                LoginViewModel.LoginResult.RequiresTotp -> {
                                                    // This result should no longer occur for login
                                                    errorMessage = "Interne Fehlkonfiguration (2FA)"
                                                }
                                                LoginViewModel.LoginResult.RequiresPasswordChange -> {
                                                    showPasswordChangeDialog = true
                                                }
                                                is LoginViewModel.LoginResult.Error -> {
                                                    errorMessage = result.message
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) { Text("Anmelden") }
                                
                                TextButton(
                                    onClick = { 
                                        selectedUser = null
                                        passwordInput = ""
                                        errorMessage = null 
                                    },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    Text("Abbrechen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasswordChangeDialog && selectedUser != null) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var changeError by remember { mutableStateOf<String?>(null) }
        
        // Validation: Must be a valid password AND not the same as the temporary one
        val isDifferent = newPass != passwordInput
        val isValid = if (selectedUser!!.role == UserRole.ADMIN) {
            SecurityUtils.isValidPassword(newPass)
        } else {
            newPass.length >= 4
        }

        AlertDialog(
            onDismissRequest = { showPasswordChangeDialog = false },
            title = { Text("Passwort ändern") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ihr Passwort ist temporär und muss geändert werden.")
                    TextField(
                        value = newPass,
                        onValueChange = { 
                            newPass = it
                            changeError = if (it == passwordInput && it.isNotEmpty()) "Das neue Passwort darf nicht dem temporären entsprechen" else null
                        },
                        label = { Text("Neues Passwort") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        isError = (newPass.isNotEmpty() && !isValid) || !isDifferent,
                        supportingText = {
                            if (newPass.isNotEmpty() && !isValid) {
                                Text(if (selectedUser!!.role == UserRole.ADMIN) "Min. 8 Zeichen, Groß/Klein, Zahl & Sonderzeichen" else "Mindestens 4 Zeichen")
                            } else if (!isDifferent && newPass.isNotEmpty()) {
                                Text("Darf nicht das temporäre Passwort sein", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    TextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Passwort bestätigen") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        isError = confirmPass.isNotEmpty() && newPass != confirmPass
                    )
                    if (changeError != null) {
                        Text(changeError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = isValid && newPass == confirmPass && isDifferent,
                    onClick = {
                        scope.launch {
                            viewModel.updatePasswordAndCompleteLogin(selectedUser!!, newPass)
                            showPasswordChangeDialog = false
                            
                            // Reset login state to force manual login with new password
                            selectedUser = null
                            passwordInput = ""
                            errorMessage = "Passwort geändert. Bitte loggen Sie sich neu ein."
                        }
                    }
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPasswordChangeDialog = false
                    passwordInput = ""
                }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun AdminResetDialog(
    admin: User,
    onDismiss: () -> Unit,
    onReset: (String, String) -> Unit
) {
    var totpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var codeValid by remember { mutableStateOf<Boolean?>(null) }
    val isPassValid = SecurityUtils.isValidPassword(newPassword)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Passwort zurücksetzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Geben Sie den 2FA-Code aus Ihrer Authenticator-App und ein neues Passwort ein.")
                
                TextField(
                    value = totpCode,
                    onValueChange = { 
                        if(it.length <= 6) {
                            totpCode = it
                            if (it.length == 6) {
                                codeValid = TotpUtils.verifyCode(admin.totpSecret ?: "", it)
                            } else {
                                codeValid = null
                            }
                        }
                    },
                    label = { Text("6-stelliger 2FA-Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = codeValid == false,
                    supportingText = {
                        if (codeValid == false) Text("Ungültiger 2FA-Code")
                        else if (codeValid == true) Text("Code verifiziert", color = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (codeValid == true) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
                
                TextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Neues Passwort") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = newPassword.isNotEmpty() && !isPassValid,
                    supportingText = {
                        if (newPassword.isNotEmpty() && !isPassValid) {
                            Text("Min. 8 Zeichen, Groß/Klein, Zahl & Sonderzeichen")
                        }
                    }
                )
                
                TextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Passwort bestätigen") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                )
            }
        },
        confirmButton = {
            Button(
                enabled = codeValid == true && isPassValid && newPassword == confirmPassword,
                onClick = { onReset(totpCode, newPassword) }
            ) {
                Text("Passwort zurücksetzen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
