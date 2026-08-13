package com.example.wearcommands.shared

import android.content.Context

/**
 * Gelen foto/guvenlik selfie'sini kalici olarak kaydeden cihaza ozel arayuz.
 * Saat uygulamasi bunu [MediaRegistry] uzerinden kaydeder; boylece uygulama
 * ekrani KAPALIYKEN bile ([CommandListenerService] icinden) fotograf diske
 * yazilir (or. kilit ekraninda cekilen selfie Guvenlik sekmesine duser).
 */
interface MediaSink {
    fun savePhoto(context: Context, jpeg: ByteArray)
    fun saveSecurity(context: Context, reason: String, jpeg: ByteArray)
}

/** Uygulama basinda ayarlanan tekil medya kaydedici. */
object MediaRegistry {
    @Volatile
    var sink: MediaSink? = null
}
