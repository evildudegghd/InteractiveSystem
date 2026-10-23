package com.lagersystem.interactivesystem.sync

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class WebDavService @Inject constructor(
    private val okHttpClient: OkHttpClient
) : CloudSyncService {
    private var baseUrl: String = ""
    private var credentials: String = ""

    override suspend fun configure(url: String, user: String, pass: String) {
        var cleanUrl = url.trim()
        
        // Clean scheme and strictly enforce HTTPS for security
        if (cleanUrl.startsWith("http://", ignoreCase = true)) {
            cleanUrl = "https://" + cleanUrl.substring(7)
        } else if (cleanUrl.startsWith("webdav://", ignoreCase = true) || cleanUrl.startsWith("webdavs://", ignoreCase = true)) {
            cleanUrl = "https://" + cleanUrl.substring(if (cleanUrl.startsWith("webdavs://", ignoreCase = true)) 10 else 9)
        }
        
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            cleanUrl = "https://$cleanUrl"
        }
        
        baseUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        credentials = Credentials.basic(user, pass)
    }

    override suspend fun getFileWithMeta(fileName: String): CloudResponse = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext CloudResponse(null, null, isError = true)

        val request = Request.Builder()
            .url(baseUrl + fileName)
            .header("Authorization", credentials)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> throw Exception("AUTH_FAILED")
                    response.code == 404 -> CloudResponse(null, null, isNotFound = true)
                    response.isSuccessful -> CloudResponse(response.body?.string(), response.header("ETag"))
                    else -> CloudResponse(null, null, isError = true)
                }
            }
        } catch (e: Exception) {
            if (e.message == "AUTH_FAILED") throw e
            CloudResponse(null, null, isError = true)
        }
    }

    override suspend fun uploadFile(fileName: String, content: String, ifMatchEtag: String?): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext false
        
        val requestBuilder = Request.Builder()
            .url(baseUrl + fileName)
            .header("Authorization", credentials)
            .put(content.toRequestBody("application/json".toMediaType()))
        
        ifMatchEtag?.let {
            requestBuilder.header("If-Match", it)
        }

        val request = requestBuilder.build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun listFiles(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext emptyList()
        val fullUrl = if (baseUrl.endsWith("/") && currentPath.startsWith("/")) baseUrl + currentPath.substring(1) else baseUrl + currentPath
        val propfindBody = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><D:propfind xmlns:D=\"DAV:\"><D:prop><D:resourcetype/><D:displayname/></D:prop></D:propfind>"
        val request = Request.Builder().url(fullUrl).header("Authorization", credentials).header("Depth", "1").method("PROPFIND", propfindBody.toRequestBody("text/xml".toMediaType())).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw Exception("AUTH_FAILED")
                if (response.isSuccessful) parseWebDavItems(response.body?.string() ?: "", currentPath, isFolder = false)
                else emptyList()
            }
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            emptyList() 
        }
    }

    override suspend fun listFolders(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext emptyList()
        val fullUrl = if (baseUrl.endsWith("/") && currentPath.startsWith("/")) baseUrl + currentPath.substring(1) else baseUrl + currentPath
        val propfindBody = "<?xml version=\"1.0\" encoding=\"utf-8\" ?><D:propfind xmlns:D=\"DAV:\"><D:prop><D:resourcetype/><D:displayname/></D:prop></D:propfind>"
        val request = Request.Builder().url(fullUrl).header("Authorization", credentials).header("Depth", "1").method("PROPFIND", propfindBody.toRequestBody("text/xml".toMediaType())).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw Exception("AUTH_FAILED")
                if (response.isSuccessful) parseWebDavItems(response.body?.string() ?: "", currentPath, isFolder = true)
                else emptyList()
            }
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            emptyList() 
        }
    }

    override suspend fun createFolder(folderName: String, currentPath: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext false
        val folderPath = if (currentPath.endsWith("/")) "$currentPath$folderName" else "$currentPath/$folderName"
        val fullUrl = if (baseUrl.endsWith("/") && folderPath.startsWith("/")) baseUrl + folderPath.substring(1) else baseUrl + folderPath
        
        val request = Request.Builder()
            .url(if (fullUrl.endsWith("/")) fullUrl else "$fullUrl/")
            .header("Authorization", credentials)
            .method("MKCOL", null)
            .build()
        
        try {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    override suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isEmpty()) return@withContext false
        val request = Request.Builder().url(baseUrl + fileName).header("Authorization", credentials).delete().build()
        try {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    private fun parseWebDavItems(xml: String, currentPath: String, isFolder: Boolean): List<String> {
        val items = mutableListOf<String>()
        // Improved regex to be more specific to the resourcetype tag
        val responseRegex = "<[^>]*?:?response>.*?</[^>]*?:?response>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val collectionRegex = "<[^>]*?:?collection\\s*/?>".toRegex()
        val hrefRegex = "<[^>]*?:?href>(.*?)</[^>]*?:?href>".toRegex()
        val displayNameRegex = "<[^>]*?:?displayname>(.*?)</[^>]*?:?displayname>".toRegex()

        val normalizedCurrentPath = currentPath.trim('/').lowercase()

        responseRegex.findAll(xml).forEach { match ->
            val block = match.value
            
            // Only look for collection tag inside the resourcetype property to be sure
            val resourceTypeBlock = "<[^>]*?:?resourcetype>.*?</[^>]*?:?resourcetype>".toRegex(RegexOption.DOT_MATCHES_ALL).find(block)?.value ?: ""
            val hasCollectionTag = collectionRegex.containsMatchIn(resourceTypeBlock)
            
            val href = hrefRegex.find(block)?.groupValues?.get(1) ?: ""
            val displayName = displayNameRegex.find(block)?.groupValues?.get(1) ?: ""
            
            val decodedHref = try {
                java.net.URLDecoder.decode(href, "UTF-8").trim('/')
            } catch (e: Exception) {
                href.trim('/')
            }
            
            val name = if (displayName.isNotEmpty()) displayName else decodedHref.substringAfterLast("/")
            val normalizedItemPath = decodedHref.lowercase()

            // Filter logic:
            // 1. If we want folders, only take collections
            // 2. If we want files, only take non-collections
            // 3. ALWAYS exclude the current directory (the folder we are currently listing)
            val isCurrentDir = normalizedItemPath == normalizedCurrentPath || 
                              (normalizedCurrentPath.isEmpty() && normalizedItemPath.isEmpty())

            if (!isCurrentDir) {
                if (isFolder && hasCollectionTag) {
                    items.add(name)
                } else if (!isFolder && !hasCollectionTag) {
                    items.add(name)
                }
            }
        }
        return items.distinct().filter { it.isNotEmpty() }
    }

    suspend fun downloadFile(fileName: String): String? {
        return getFileWithMeta(fileName)?.content
    }
}
