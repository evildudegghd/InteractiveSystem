package com.lagersystem.interactivesystem.data.user

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

enum class UserRole {
    ADMIN, USER
}

@Entity(
    tableName = "users",
    indices = [Index("name")]
)
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val role: UserRole,
    val passwordHash: String? = null,
    val isTemporaryPassword: Boolean = false,
    val totpSecret: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)
