package com.lagersystem.interactivesystem.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagersystem.interactivesystem.data.packinglist.PackingList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val WEBDAV_URL = stringPreferencesKey("webdav_url")
    private val CLOUD_PROTOCOL = stringPreferencesKey("cloud_protocol")
    private val WEBDAV_USER = stringPreferencesKey("webdav_user")
    private val WEBDAV_PASS = stringPreferencesKey("webdav_pass")
    private val LAST_SYNC_ETAG = stringPreferencesKey("last_sync_etag")
    private val IS_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
    private val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
    private val ADMIN_PASSWORD = stringPreferencesKey("admin_password")
    private val RECENT_WAREHOUSES = stringPreferencesKey("recent_warehouses")
    private val NFC_DISABLED_PERMANENT = booleanPreferencesKey("nfc_disabled_permanent")
    private val CAMERA_DISABLED_PERMANENT = booleanPreferencesKey("camera_disabled_permanent")
    private fun themePreferenceKey(userId: String) = stringPreferencesKey("theme_$userId")
    private fun returnReminderKey(userId: String) = booleanPreferencesKey("return_reminders_$userId")

    val webDavUrl: Flow<String?> = context.dataStore.data.map { it[WEBDAV_URL] }
    val cloudProtocol: Flow<String?> = context.dataStore.data.map { it[CLOUD_PROTOCOL] }
    val webDavUser: Flow<String?> = context.dataStore.data.map { it[WEBDAV_USER] }
    val webDavPass: Flow<String?> = context.dataStore.data.map { it[WEBDAV_PASS] }
    val lastSyncEtag: Flow<String?> = context.dataStore.data.map { it[LAST_SYNC_ETAG] }
    val isSetupCompleted: Flow<Boolean> = context.dataStore.data.map { it[IS_SETUP_COMPLETED] ?: false }
    val currentUserId: Flow<String?> = context.dataStore.data.map { it[CURRENT_USER_ID] }
    val adminPassword: Flow<String?> = context.dataStore.data.map { it[ADMIN_PASSWORD] }
    fun themePreference(userId: String?): Flow<String> = context.dataStore.data.map { preferences ->
        userId?.let { preferences[themePreferenceKey(it)] } ?: "SYSTEM"
    }
    fun returnRemindersEnabled(userId: String): Flow<Boolean> =
        context.dataStore.data.map { it[returnReminderKey(userId)] ?: false }
    val recentWarehouseIds: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        prefs[RECENT_WAREHOUSES]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }
    val nfcDisabledPermanent: Flow<Boolean> = context.dataStore.data.map { it[NFC_DISABLED_PERMANENT] ?: false }
    val cameraDisabledPermanent: Flow<Boolean> = context.dataStore.data.map { it[CAMERA_DISABLED_PERMANENT] ?: false }

    suspend fun setNfcDisabledPermanent(disabled: Boolean) {
        context.dataStore.edit { it[NFC_DISABLED_PERMANENT] = disabled }
    }

    suspend fun setCameraDisabledPermanent(disabled: Boolean) {
        context.dataStore.edit { it[CAMERA_DISABLED_PERMANENT] = disabled }
    }

    suspend fun addRecentWarehouse(warehouseId: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_WAREHOUSES]?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
            val updated = listOf(warehouseId) + current.filter { it != warehouseId }
            prefs[RECENT_WAREHOUSES] = updated.take(3).joinToString(",")
        }
    }

    suspend fun saveWebDavSettings(url: String, user: String, pass: String, protocol: String? = "WEBDAV") {
        context.dataStore.edit {
            it[WEBDAV_URL] = url
            it[WEBDAV_USER] = user
            it[WEBDAV_PASS] = pass
            if (protocol != null) it[CLOUD_PROTOCOL] = protocol
        }
    }

    suspend fun saveLastSyncEtag(etag: String?) {
        context.dataStore.edit {
            val cleaned = etag?.trim()?.removeSurrounding("\"")
            if (cleaned == null) it.remove(LAST_SYNC_ETAG)
            else it[LAST_SYNC_ETAG] = cleaned
        }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit {
            it[IS_SETUP_COMPLETED] = completed
        }
    }

    suspend fun setCurrentUserId(userId: String?) {
        context.dataStore.edit {
            if (userId == null) it.remove(CURRENT_USER_ID)
            else it[CURRENT_USER_ID] = userId
        }
    }

    suspend fun setAdminPassword(password: String) {
        context.dataStore.edit {
            it[ADMIN_PASSWORD] = password
        }
    }

    suspend fun setThemePreference(userId: String, preference: String) {
        context.dataStore.edit {
            it[themePreferenceKey(userId)] = preference
        }
    }

    suspend fun setReturnRemindersEnabled(userId: String, enabled: Boolean) {
        context.dataStore.edit {
            it[returnReminderKey(userId)] = enabled
        }
    }

    private val PACKING_LISTS_PREFIX = "packing_lists_"
    private fun packingListKey(warehouseId: Long) = stringPreferencesKey("$PACKING_LISTS_PREFIX$warehouseId")

    fun packingLists(warehouseId: Long, gson: Gson): Flow<List<PackingList>> = context.dataStore.data.map { prefs ->
        val json = prefs[packingListKey(warehouseId)] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<PackingList>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun savePackingList(warehouseId: Long, packingList: PackingList, gson: Gson) {
        context.dataStore.edit { prefs ->
            val key = packingListKey(warehouseId)
            val json = prefs[key]
            val type = object : TypeToken<List<PackingList>>() {}.type
            val current: MutableList<PackingList> = try {
                if (json != null) gson.fromJson(json, type) ?: mutableListOf() else mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            current.add(0, packingList)
            prefs[key] = gson.toJson(current)
        }
    }

    suspend fun deletePackingList(warehouseId: Long, id: String, gson: Gson) {
        context.dataStore.edit { prefs ->
            val key = packingListKey(warehouseId)
            val json = prefs[key] ?: return@edit
            val type = object : TypeToken<List<PackingList>>() {}.type
            val current: MutableList<PackingList> = try {
                gson.fromJson(json, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
            current.removeAll { it.id == id }
            prefs[key] = gson.toJson(current)
        }
    }
}
