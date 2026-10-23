package com.lagersystem.interactivesystem.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpSyncService @Inject constructor() : CloudSyncService {
    private var host: String = ""
    private var port: Int = 21
    private var user: String = ""
    private var pass: String = ""

    override suspend fun configure(url: String, user: String, pass: String) {
        val cleanUrl = url.removePrefix("ftp://").removePrefix("ftps://")
        val parts = cleanUrl.split(":", limit = 2)
        if (parts.size > 1) {
            this.host = parts[0]
            this.port = parts[1].substringBefore("/").toIntOrNull() ?: 21
        } else {
            this.host = parts[0].substringBefore("/")
            this.port = 21
        }
        this.user = user
        this.pass = pass
    }

    override suspend fun getFileWithMeta(fileName: String): CloudResponse = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(host, port)
            if (client.login(user, pass)) {
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                val outputStream = ByteArrayOutputStream()
                if (client.retrieveFile(fileName, outputStream)) {
                    val content = outputStream.toString("UTF-8")
                    val file = client.mlistFile(fileName)
                    val etag = file?.timestamp?.timeInMillis?.toString()
                    CloudResponse(content, etag)
                } else {
                    val code = client.replyCode
                    if (code == 550) CloudResponse(null, null, isNotFound = true)
                    else CloudResponse(null, null, isError = true)
                }
            } else throw IOException("AUTH_FAILED")
        } catch (e: IOException) { 
            if (e.message == "AUTH_FAILED") throw e
            CloudResponse(null, null, isError = true)
        } finally { if (client.isConnected) client.disconnect() }
    }

    override suspend fun uploadFile(fileName: String, content: String, ifMatchEtag: String?): Boolean = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(host, port)
            if (client.login(user, pass)) {
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                val inputStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
                client.storeFile(fileName, inputStream)
            } else false
        } catch (e: IOException) { false } finally { if (client.isConnected) client.disconnect() }
    }

    override suspend fun listFiles(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(host, port)
            if (client.login(user, pass)) {
                client.enterLocalPassiveMode()
                client.changeWorkingDirectory(currentPath)
                client.listFiles().filter { it.isFile }.map { it.name }
            } else throw Exception("AUTH_FAILED")
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            emptyList() 
        } finally { if (client.isConnected) client.disconnect() }
    }

    override suspend fun listFolders(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(host, port)
            if (client.login(user, pass)) {
                client.enterLocalPassiveMode()
                client.changeWorkingDirectory(currentPath)
                client.listDirectories().map { it.name }
            } else throw Exception("AUTH_FAILED")
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            emptyList() 
        } finally { if (client.isConnected) client.disconnect() }
    }

    override suspend fun createFolder(folderName: String, currentPath: String): Boolean = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(host, port)
            if (client.login(user, pass)) {
                client.enterLocalPassiveMode()
                client.changeWorkingDirectory(currentPath)
                client.makeDirectory(folderName)
            } else false
        } catch (e: IOException) { false } finally { if (client.isConnected) client.disconnect() }
    }

    override suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val client = FTPClient()
        try {
            client.connect(host, port)
            if (client.login(user, pass)) {
                client.enterLocalPassiveMode()
                client.deleteFile(fileName)
            } else false
        } catch (e: IOException) { false } finally { if (client.isConnected) client.disconnect() }
    }
}
