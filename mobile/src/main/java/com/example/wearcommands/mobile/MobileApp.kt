package com.example.wearcommands.mobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.wearcommands.shared.CommandRegistry

/**
 * Uygulama basinda telefon komut isleyicisini kaydeder. Ayrica uygulamanin
 * on planda olup olmadigini izler (arka plandan Activity baslatilabilir mi
 * kararinda kullanilir).
 */
class MobileApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        CommandRegistry.executor = PhoneCommandExecutor()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++; isForeground = true
            }
            override fun onActivityStopped(activity: Activity) {
                started--; if (started <= 0) isForeground = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        /** Uygulamanin gorunur (on planda) bir Activity'si var mi. */
        @Volatile
        var isForeground = false
    }
}
