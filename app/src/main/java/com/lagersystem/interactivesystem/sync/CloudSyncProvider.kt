package com.lagersystem.interactivesystem.sync

import com.lagersystem.interactivesystem.data.PreferenceManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncProvider @Inject constructor(
    private val webDavService: WebDavService,
    private val ftpSyncService: FtpSyncService,
    private val sftpSyncService: SftpSyncService,
    private val smbSyncService: SmbSyncService,
    private val preferenceManager: PreferenceManager
) {
    suspend fun getService(): CloudSyncService {
        val protocol = preferenceManager.cloudProtocol.first() ?: "WEBDAV"
        return when (protocol.uppercase()) {
            "FTP" -> ftpSyncService
            "SFTP" -> sftpSyncService
            "SMB" -> smbSyncService
            else -> webDavService
        }
    }
}
