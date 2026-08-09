package com.example.wearcommands.mobile

import android.app.admin.DevicePolicyManager
import android.content.Context

/** Cihaz Yoneticisi islemleri: etkin mi, kilitle, veri sil. */
object AdminHelper {

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isActive(context: Context): Boolean =
        dpm(context).isAdminActive(PhoneAdminReceiver.component(context))

    /** @return kilitlendi mi. */
    fun lockNow(context: Context): Boolean {
        if (!isActive(context)) return false
        dpm(context).lockNow()
        return true
    }

    /**
     * Fabrika ayarlarina dondurur (tum kullanici verisi silinir). Geri donusu
     * yoktur. Sadece PIN dogrulandiktan sonra cagrilmalidir.
     * @return islem baslatildi mi.
     */
    fun wipe(context: Context): Boolean {
        if (!isActive(context)) return false
        dpm(context).wipeData(0)
        return true
    }
}
