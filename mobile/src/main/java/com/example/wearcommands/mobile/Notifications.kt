package com.example.wearcommands.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Bildirim kanallari, foreground servis bildirimi ve tam ekran mesaj bildirimi. */
object Notifications {

    const val CH_SERVICE = "wc_service"
    const val CH_MESSAGE = "wc_message"
    private const val MESSAGE_ID = 4201

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Arka plan islemleri", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MESSAGE, "Ekran mesaji", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** Foreground servisler icin sade bildirim. */
    fun serviceNotification(context: Context, title: String): Notification {
        ensureChannels(context)
        return androidx.core.app.NotificationCompat.Builder(context, CH_SERVICE)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    /** Saatten gelen mesaji tam ekran bildirimi ile gosterir (arka planda da calisir). */
    fun showMessage(context: Context, text: String) {
        ensureChannels(context)
        val intent = Intent(context, MessageActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MessageActivity.EXTRA_TEXT, text)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getActivity(context, 0, intent, flags)

        val notif = androidx.core.app.NotificationCompat.Builder(context, CH_MESSAGE)
            .setContentTitle("Saatten mesaj")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(MESSAGE_ID, notif)
    }
}
