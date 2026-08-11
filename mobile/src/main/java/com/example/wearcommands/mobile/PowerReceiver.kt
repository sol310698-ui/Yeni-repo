package com.example.wearcommands.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Telefon sarjdan cikarilinca saate uyari gonderir (anti-hirsizlik / "masamdan
 * alma"). ACTION_POWER_DISCONNECTED, manifest'te kayitli aliciyla uygulama
 * kapaliyken de teslim edilir.
 */
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_DISCONNECTED) return
        val ctx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                CommandSender.send(ctx, CommandProtocol.build(CommandProtocol.RSP_ALERT, "Sarj cikarildi!"))
            }
            pending.finish()
        }
    }
}
