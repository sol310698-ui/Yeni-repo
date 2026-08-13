package com.example.wearcommands.wear

import android.app.Application
import android.content.Context
import com.example.wearcommands.shared.MediaRegistry
import com.example.wearcommands.shared.MediaSink

/**
 * Uygulama basinda medya kaydediciyi baglar; boylece gelen foto/guvenlik
 * selfie'si, saat uygulamasi ekrani kapaliyken bile diske kaydedilir.
 */
class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MediaRegistry.sink = object : MediaSink {
            override fun savePhoto(context: Context, jpeg: ByteArray) {
                PhotoStore.save(context, jpeg)
            }
            override fun saveSecurity(context: Context, reason: String, jpeg: ByteArray) {
                SecurityStore.save(context, reason, jpeg)
            }
        }
    }
}
