package com.lagersystem.interactivesystem.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagersystem.interactivesystem.data.PreferenceManager
import com.lagersystem.interactivesystem.data.user.User
import com.lagersystem.interactivesystem.data.user.UserRole
import com.lagersystem.interactivesystem.data.repository.InventoryRepository
import com.lagersystem.interactivesystem.util.SecurityUtils
import com.lagersystem.interactivesystem.util.TotpUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val users = repository.getAllUsers().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val adminPassword = preferenceManager.adminPassword.stateIn(viewModelScope, SharingStarted.Lazily, null)

    suspend fun login(user: User, passwordAttempt: String? = null, totpCode: String? = null): LoginResult {
        // Step 1: Password Check
        if (user.role == UserRole.ADMIN) {
            val hashedAttempt = passwordAttempt?.let { SecurityUtils.hashPassword(it) }
            val storedHash = preferenceManager.adminPassword.first()
            if (hashedAttempt != storedHash) {
                return LoginResult.Error("Falsches Passwort")
            }
        } else {
            // Non-admin password check (if they have one)
            if (!user.passwordHash.isNullOrBlank()) {
                val hashedAttempt = passwordAttempt?.let { SecurityUtils.hashPassword(it) }
                if (hashedAttempt != user.passwordHash) {
                    return LoginResult.Error("Falsches Passwort")
                }
            }
        }

        // Step 3: Temporary Password Check
        if (user.isTemporaryPassword) {
            return LoginResult.RequiresPasswordChange
        }

        preferenceManager.setCurrentUserId(user.id)
        return LoginResult.Success
    }

    suspend fun updatePasswordAndCompleteLogin(user: User, newPassword: String) {
        val newHash = SecurityUtils.hashPassword(newPassword)
        val updatedUser = user.copy(passwordHash = newHash, isTemporaryPassword = false)
        repository.insertUser(updatedUser)
        
        if (user.role == UserRole.ADMIN) {
            preferenceManager.setAdminPassword(newHash)
        }
        
        preferenceManager.setCurrentUserId(user.id)
    }

    suspend fun resetAdminPasswordWithTotp(admin: User, totpCode: String, newPassword: String): Boolean {
        if (admin.role != UserRole.ADMIN || admin.totpSecret == null) return false
        
        if (TotpUtils.verifyCode(admin.totpSecret, totpCode)) {
            val newHash = SecurityUtils.hashPassword(newPassword)
            val updatedAdmin = admin.copy(passwordHash = newHash, isTemporaryPassword = false)
            repository.insertUser(updatedAdmin)
            preferenceManager.setAdminPassword(newHash)
            return true
        }
        return false
    }

    sealed class LoginResult {
        object Success : LoginResult()
        object RequiresTotp : LoginResult()
        object RequiresPasswordChange : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
}
