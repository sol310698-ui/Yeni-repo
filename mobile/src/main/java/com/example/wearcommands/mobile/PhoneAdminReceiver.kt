package com.example.wearcommands.mobile

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Cihaz Yoneticisi (Device Admin) alicisi. Ekran kilidi ([android.app.admin.
 * DevicePolicyManager.lockNow]) ve fabrika sifirlama ([DevicePolicyManager.
 * wipeData]) icin gereklidir. Kullanicinin bir kez etkinlestirmesi gerekir.
 */
class PhoneAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, PhoneAdminReceiver::class.java)
    }
}
