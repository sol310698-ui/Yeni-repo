package com.example.wearcommands.mobile

import android.app.Application
import com.example.wearcommands.shared.CommandRegistry

/**
 * Uygulama basinda telefon komut isleyicisini kaydeder. Application.onCreate,
 * gelen mesajla baslayan servisten once calistigi icin executor her zaman
 * hazir olur.
 */
class MobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        CommandRegistry.executor = PhoneCommandExecutor()
    }
}
