package com.example.wearcommands.mobile

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle

/**
 * Cihaz Yoneticisi alicisi. Ekran kilidi ve fabrika sifirlama icin gereklidir.
 * Ayrica yanlis sifre denemesinde ([onPasswordFailed]) on kameradan guvenlik
 * selfie'si cekip saate gonderir (davetsiz misafir). Bunun icin device_admin
 * politikasinda <watch-login /> tanimlidir.
 */
class PhoneAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        super.onPasswordFailed(context, intent, user)
        runCatching {
            CaptureActivity.captureSecurity(context.applicationContext, "Yanlis sifre")
        }
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, PhoneAdminReceiver::class.java)
    }
}
