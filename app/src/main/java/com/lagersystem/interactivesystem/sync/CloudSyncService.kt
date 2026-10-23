package com.lagersystem.interactivesystem.sync

interface CloudSyncService {
    suspend fun configure(url: String, user: String, pass: String)
    suspend fun getFileWithMeta(fileName: String): CloudResponse
    suspend fun uploadFile(fileName: String, content: String, ifMatchEtag: String? = null): Boolean
    suspend fun listFolders(currentPath: String): List<String>
    suspend fun listFiles(currentPath: String): List<String>
    suspend fun createFolder(folderName: String, currentPath: String): Boolean
    suspend fun deleteFile(fileName: String): Boolean
}

data class CloudResponse(
    val content: String?, 
    val etag: String?,
    val isNotFound: Boolean = false,
    val isError: Boolean = false
)
