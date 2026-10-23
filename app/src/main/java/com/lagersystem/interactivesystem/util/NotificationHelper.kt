package com.lagersystem.interactivesystem.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.lagersystem.interactivesystem.MainActivity
import com.lagersystem.interactivesystem.R

object NotificationHelper {
    private const val CHANNEL_ID = "inventory_sync_channel"
    private const val RETURN_REMINDER_CHANNEL_ID = "return_reminders_channel"

    fun showNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Hintergrund-Synchronisierung", 
                NotificationManager.IMPORTANCE_HIGH // High importance for real-time feel
            ).apply {
                description = "Informiert über Änderungen im Lagerbestand durch andere Geräte"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Use a unique ID for each sync notification to avoid overwriting recent history
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showReturnReminder(context: Context, itemName: String, inventoryNumber: String, amount: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    RETURN_REMINDER_CHANNEL_ID,
                    "Rückgabeerinnerungen",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Erinnert nach 24 Stunden an noch ausgeliehene Geräte."
                }
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            inventoryNumber.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, RETURN_REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Rückgabeerinnerung")
            .setContentText("$amount × $itemName ($inventoryNumber) ist noch ausgeliehen.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$amount × $itemName ($inventoryNumber) ist seit mindestens 24 Stunden ausgeliehen. Bitte bei Gelegenheit zurückgeben.")
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(inventoryNumber.hashCode(), notification)
    }
}
