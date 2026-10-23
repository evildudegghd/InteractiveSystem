package com.lagersystem.interactivesystem.sync

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbSyncService @Inject constructor() : CloudSyncService {
    private var host: String = ""
    private var shareName: String = ""
    private var user: String = ""
    private var pass: String = ""
    private var domain: String = ""

    override suspend fun configure(url: String, user: String, pass: String) {
        val cleanUrl = url.removePrefix("smb://")
        this.host = cleanUrl.substringBefore("/")
        this.shareName = cleanUrl.substringAfter("/").substringBefore("/")
        this.user = user
        this.pass = pass
        this.domain = "" 
    }

    override suspend fun getFileWithMeta(fileName: String): CloudResponse = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(host).use { connection ->
                val ac = AuthenticationContext(user, pass.toCharArray(), domain)
                val session = try {
                    connection.authenticate(ac)
                } catch (e: Exception) {
                    throw Exception("AUTH_FAILED")
                }
                val share = session.connectShare(shareName) as DiskShare
                if (share.fileExists(fileName)) {
                    val file = share.openFile(fileName, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                    val outputStream = ByteArrayOutputStream()
                    file.inputStream.use { it.copyTo(outputStream) }
                    val content = outputStream.toString("UTF-8")
                    val info = share.getFileInformation(fileName)
                    val etag = info.basicInformation.changeTime.toString()
                    CloudResponse(content, etag)
                } else {
                    CloudResponse(null, null, isNotFound = true)
                }
            }
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            CloudResponse(null, null, isError = true)
        }
    }

    override suspend fun uploadFile(fileName: String, content: String, ifMatchEtag: String?): Boolean = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(host).use { connection ->
                val ac = AuthenticationContext(user, pass.toCharArray(), domain)
                val session = connection.authenticate(ac)
                val share = session.connectShare(shareName) as DiskShare
                val file = share.openFile(fileName, EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null)
                file.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                true
            }
        } catch (e: Exception) { false }
    }

    override suspend fun listFiles(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(host).use { connection ->
                val ac = AuthenticationContext(user, pass.toCharArray(), domain)
                val session = try {
                    connection.authenticate(ac)
                } catch (e: Exception) {
                    throw Exception("AUTH_FAILED")
                }
                val share = session.connectShare(shareName) as DiskShare
                share.list(currentPath).filter { it.fileAttributes.toInt() and 0x10 == 0 }.map { it.fileName }
            }
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            emptyList() 
        }
    }

    override suspend fun listFolders(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(host).use { connection ->
                val ac = AuthenticationContext(user, pass.toCharArray(), domain)
                val session = try {
                    connection.authenticate(ac)
                } catch (e: Exception) {
                    throw Exception("AUTH_FAILED")
                }
                val share = session.connectShare(shareName) as DiskShare
                share.list(currentPath).filter { it.fileAttributes.toInt() and 0x10 != 0 }.map { it.fileName }.filter { it != "." && it != ".." }
            }
        } catch (e: Exception) { 
            if (e.message == "AUTH_FAILED") throw e
            emptyList() 
        }
    }

    override suspend fun createFolder(folderName: String, currentPath: String): Boolean = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(host).use { connection ->
                val ac = AuthenticationContext(user, pass.toCharArray(), domain)
                val session = connection.authenticate(ac)
                val share = session.connectShare(shareName) as DiskShare
                val folderPath = if (currentPath.endsWith("/")) "$currentPath$folderName" else "$currentPath/$folderName"
                share.mkdir(folderPath.replace("/", "\\").trim('\\'))
                true
            }
        } catch (e: Exception) { false }
    }

    override suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val client = SMBClient()
        try {
            client.connect(host).use { connection ->
                val ac = AuthenticationContext(user, pass.toCharArray(), domain)
                val session = connection.authenticate(ac)
                val share = session.connectShare(shareName) as DiskShare
                if (share.fileExists(fileName)) { share.rm(fileName); true } else false
            }
        } catch (e: Exception) { false }
    }
}
