package com.example.wearcommands.mobile

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Ekran goruntusu servisi (MediaProjection). Kullanici bir kez izin verir,
 * servis sanal ekrani bir ImageReader'a yansitir ve acik kalir. Saatten
 * SCREENSHOT komutu gelince en son kare JPEG'e cevrilip saate gonderilir
 * (foto ile ayni /photo yolu — galeride gorunur).
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var w = 0
    private var h = 0
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            ACTION_CAPTURE -> capture()
            ACTION_STOP -> { cleanup(); stopSelf() }
            else -> {
                val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
                @Suppress("DEPRECATION")
                val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data != null) init(code, data) else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notif = Notifications.serviceNotification(this, "Ekran erisimi")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun init(code: Int, data: Intent) {
        val metrics = resources.displayMetrics
        w = metrics.widthPixels
        h = metrics.heightPixels
        val density = metrics.densityDpi
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val p = mpm.getMediaProjection(code, data) ?: run { stopSelf(); return }
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { cleanup() }
        }, null)
        val reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
        virtualDisplay = p.createVirtualDisplay(
            "wc_screen", w, h, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )
        imageReader = reader
        projection = p
        isActive = true
    }

    private fun capture() {
        val reader = imageReader ?: run { fail("Ekran izni yok"); return }
        io.launch {
            var image = reader.acquireLatestImage()
            var tries = 0
            while (image == null && tries < 10) {
                Thread.sleep(50); image = reader.acquireLatestImage(); tries++
            }
            if (image == null) { fail("Kare alinamadi"); return@launch }
            val jpeg = runCatching {
                val bmp = toBitmap(image)
                encodeUnderLimit(bmp)
            }.getOrNull()
            image.close()
            if (jpeg == null) { fail("Goruntu hatasi"); return@launch }
            runCatching { CommandSender.sendBytes(applicationContext, CommandProtocol.PATH_PHOTO, jpeg) }
            runCatching {
                CommandSender.send(applicationContext, CommandProtocol.build(CommandProtocol.RSP_STATUS, "Ekran goruntusu"))
            }
        }
    }

    private fun toBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * w
        val bmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(plane.buffer)
        return if (bmp.width != w) Bitmap.createBitmap(bmp, 0, 0, w, h) else bmp
    }

    /** MessageClient ~100KB siniri icin kucult + kaliteyi dusur. */
    private fun encodeUnderLimit(src: Bitmap): ByteArray {
        val maxDim = 600
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        val scaled = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1), true
        )
        var q = 70
        var out = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        while (out.size > 90_000 && q > 30) {
            q -= 10
            out = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        }
        return out
    }

    private fun fail(msg: String) {
        io.launch {
            runCatching {
                CommandSender.send(applicationContext, CommandProtocol.build(CommandProtocol.RSP_ERROR, msg))
            }
        }
    }

    private fun cleanup() {
        runCatching { virtualDisplay?.release() }
        runCatching { imageReader?.close() }
        runCatching { projection?.stop() }
        virtualDisplay = null; imageReader = null; projection = null
        isActive = false
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4306
        const val ACTION_CAPTURE = "com.example.wearcommands.SCREEN_CAPTURE"
        const val ACTION_STOP = "com.example.wearcommands.SCREEN_STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        /** Ekran izni acik mi (projeksiyon calisiyor mu). */
        @Volatile
        var isActive = false
            private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, i)
        }

        fun capture(context: Context) {
            val i = Intent(context, ScreenCaptureService::class.java).setAction(ACTION_CAPTURE)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
