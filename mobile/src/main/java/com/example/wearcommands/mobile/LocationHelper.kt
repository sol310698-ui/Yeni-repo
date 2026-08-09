package com.example.wearcommands.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** Son bilinen konumu bulup harita baglantisi olusturur. */
object LocationHelper {

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @return "https://maps.google.com/?q=lat,lon" bicimli baglanti, ya da izin
     *   yoksa / konum bulunamazsa null.
     */
    fun lastKnownLink(context: Context): String? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        var best: Location? = null
        for (provider in lm.getProviders(true)) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) best = loc
        }
        val l = best ?: return null
        return "https://maps.google.com/?q=${l.latitude},${l.longitude}"
    }
}
