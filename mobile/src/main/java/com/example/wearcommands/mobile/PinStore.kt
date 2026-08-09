package com.example.wearcommands.mobile

import android.content.Context

/**
 * Veri silme (wipe) gibi tehlikeli islemler icin PIN saklar/dogrular.
 * PIN telefonda ayarlanir; saat komutu gonderirken PIN'i birlikte iletir.
 *
 * Basitlik icin PIN dogrudan (hash'siz) SharedPreferences'ta tutulur; bu bir
 * kisisel/debug araci icindir. Varsayilan PIN yoksa wipe reddedilir.
 */
object PinStore {
    private const val PREF = "secure_prefs"
    private const val KEY_PIN = "wipe_pin"
    private const val DEFAULT_PIN = "1234"

    fun getPin(context: Context): String {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return p.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_PIN, pin).apply()
    }

    /** Girilen PIN dogru mu? Bos PIN her zaman reddedilir. */
    fun verify(context: Context, entered: String): Boolean =
        entered.isNotEmpty() && entered == getPin(context)
}
