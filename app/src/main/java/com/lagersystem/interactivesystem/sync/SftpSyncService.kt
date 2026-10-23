package com.lagersystem.interactivesystem.sync

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpSyncService @Inject constructor() : CloudSyncService {
    private var host: String = ""
    private var port: Int = 22
    private var user: String = ""
    private var pass: String = ""

    override suspend fun configure(url: String, user: String, pass: String) {
        val cleanUrl = url.removePrefix("sftp://")
        val parts = cleanUrl.split(":", limit = 2)
        this.host = parts[0].substringBefore("/")
        this.port = if (parts.size > 1) parts[1].substringBefore("/").toIntOrNull() ?: 22 else 22
        this.user = user
        this.pass = pass
    }

    private fun createSession(): Session {
        val jsch = JSch()
        val session = jsch.getSession(user, host, port)
        session.setPassword(pass)
        val config = Properties()
        config["StrictHostKeyChecking"] = "no"
        session.setConfig(config)
        return session
    }

    override suspend fun getFileWithMeta(fileName: String): CloudResponse = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession()
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val outputStream = ByteArrayOutputStream()
            channel.get(fileName, outputStream)
            val content = outputStream.toString("UTF-8")
            val attrs = channel.lstat(fileName)
            val etag = attrs.mTime.toString()
            CloudResponse(content, etag)
        } catch (e: Exception) { 
            if (e.message?.contains("auth", ignoreCase = true) == true) throw Exception("AUTH_FAILED")
            if (e is SftpException && e.id == 2) { // SSH_FX_NO_SUCH_FILE
                CloudResponse(null, null, isNotFound = true)
            } else {
                CloudResponse(null, null, isError = true)
            }
        } finally { channel?.disconnect(); session?.disconnect() }
    }

    override suspend fun uploadFile(fileName: String, content: String, ifMatchEtag: String?): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession()
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val inputStream = ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
            channel.put(inputStream, fileName)
            true
        } catch (e: Exception) { false } finally { channel?.disconnect(); session?.disconnect() }
    }

    override suspend fun listFiles(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession()
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val files = channel.ls(currentPath)
            val result = mutableListOf<String>()
            if (files is Vector<*>) {
                files.forEach {
                    if (it is ChannelSftp.LsEntry && !it.attrs.isDir) result.add(it.filename)
                }
            }
            result
        } catch (e: Exception) { 
            if (e.message?.contains("auth", ignoreCase = true) == true) throw Exception("AUTH_FAILED")
            emptyList() 
        } finally { channel?.disconnect(); session?.disconnect() }
    }

    override suspend fun listFolders(currentPath: String): List<String> = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession()
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val files = channel.ls(currentPath)
            val result = mutableListOf<String>()
            if (files is Vector<*>) {
                files.forEach {
                    if (it is ChannelSftp.LsEntry && it.attrs.isDir && it.filename != "." && it.filename != "..") result.add(it.filename)
                }
            }
            result
        } catch (e: Exception) { 
            if (e.message?.contains("auth", ignoreCase = true) == true) throw Exception("AUTH_FAILED")
            emptyList() 
        } finally { channel?.disconnect(); session?.disconnect() }
    }

    override suspend fun createFolder(folderName: String, currentPath: String): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession()
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            val folderPath = if (currentPath.endsWith("/")) "$currentPath$folderName" else "$currentPath/$folderName"
            channel.mkdir(folderPath)
            true
        } catch (e: Exception) { false } finally { channel?.disconnect(); session?.disconnect() }
    }

    override suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = createSession()
            session.connect()
            channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            channel.rm(fileName)
            true
        } catch (e: Exception) { false } finally { channel?.disconnect(); session?.disconnect() }
    }
}
