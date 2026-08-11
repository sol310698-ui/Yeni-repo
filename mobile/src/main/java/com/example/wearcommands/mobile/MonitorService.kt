package com.example.wearcommands.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Kalici "koruma" servisi. ACTION_POWER_DISCONNECTED yayini yalnizca CALISMA
 * ANINDA kayitli aliciya gelir (manifest alicisina gelmez), bu yuzden bu
 * foreground servis acikken [PowerReceiver] runtime olarak kayitli tutulur;
 * telefon arka planda/kilitliyken de sarj cikisi yakalanip saate uyari gider.
 */
class MonitorService : Service() {

    private val powerReceiver = PowerReceiver()
    private var registered = false

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_POWER_CONNECTED)
            }
            ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun startForegroundCompat() {
        val notif = Notifications.serviceNotification(this, "Koruma aktif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        if (registered) {
            runCatching { unregisterReceiver(powerReceiver) }
            registered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4305

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }
    }
}
