package com.lagersystem.interactivesystem.scanner

import android.util.Base64

object InventoryTagPayload {
    fun encode(inventoryNumber: String, additionalInfo: String): String {
        val encodedInventoryNumber = Base64.encodeToString(
            inventoryNumber.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        val prefix = additionalInfo.trim()
        return if (prefix.isEmpty()) encodedInventoryNumber else "$prefix\n$encodedInventoryNumber"
    }

    fun decodeInventoryNumber(payload: String): String? {
        val encodedInventoryNumber = payload.trim().substringAfterLast('\n').trim()
        if (encodedInventoryNumber.isEmpty()) return null
        return try {
            String(
                Base64.decode(encodedInventoryNumber, Base64.URL_SAFE or Base64.NO_WRAP),
                Charsets.UTF_8
            ).takeIf { it.isNotBlank() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
