package com.example.wearcommands.shared

import android.content.Context

/**
 * Cihaza ozel komut isleyicisi. Telefon uygulamasi kendi [CommandExecutor]'unu
 * [CommandRegistry] uzerinden kaydeder; boylece fener/kilit/konum gibi
 * telefona ozel islemler shared modulden bagimsiz calisir.
 *
 * Not: [onCommand] genelde arka planda (WearableListenerService icinde)
 * cagrilir. Uzun islemler kendi coroutine/servisinde yurutulmelidir.
 */
interface CommandExecutor {
    /**
     * Karsi cihazdan gelen [command] islensin.
     * @return komut bu executor tarafindan islendiyse true.
     */
    fun onCommand(context: Context, command: String): Boolean
}

/** Uygulama basinda ayarlanan tekil executor kaydi. */
object CommandRegistry {
    @Volatile
    var executor: CommandExecutor? = null
}
