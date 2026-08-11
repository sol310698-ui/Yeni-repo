package com.example.wearcommands.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Telefon yeniden baslayinca koruma servisini tekrar baslatir. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { MonitorService.start(context.applicationContext) }
        }
    }
}
