package com.lagersystem.interactivesystem.util

import java.security.MessageDigest

object SecurityUtils {
    
    private const val SALT = "InteractiveLagerSystems!2026"

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedPassword = password + SALT
        val bytes = digest.digest(saltedPassword.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValidPassword(password: String): Boolean {
        // At least 8 chars, one uppercase, one lowercase, one number, one special char
        // Using a more inclusive set of special characters
        val regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&.=#/-])[A-Za-z\\d@$!%*?&.=#/-]{8,}$".toRegex()
        return password.matches(regex)
    }
}
