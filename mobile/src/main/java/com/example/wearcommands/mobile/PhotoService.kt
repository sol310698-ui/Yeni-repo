package com.example.wearcommands.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Ekranda onizleme gostermeden tek kare fotograf ceker (gizli), kucultup
 * saate onizleme olarak gonderir. Islem bitince kendini kapatir.
 */
class PhotoService : LifecycleService() {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForegroundCompat()
        val front = intent?.getBooleanExtra(EXTRA_FRONT, false) ?: false
        capture(front)
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notif = Notifications.serviceNotification(this, "Fotograf")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun capture(front: Boolean) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull()
            if (provider == null) { fail("Kamera acilamadi"); return@addListener }

            val selector = CameraSelector.Builder()
                .requireLensFacing(if (front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, imageCapture)
            }.onFailure { fail("Kamera baglanamadi"); return@addListener }

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val jpeg = toDownscaledJpeg(image)
                        image.close()
                        runCatching { provider.unbindAll() }
                        sendPhoto(jpeg)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runCatching { provider.unbindAll() }
                        fail("Foto hatasi: ${exception.message}")
                    }
                }
            )
        }, ContextCompat.getMainExecutor(this))
    }

    /** ImageProxy(JPEG) -> en fazla 480px, ~%70 kaliteli kucuk JPEG. */
    private fun toDownscaledJpeg(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes

        val max = 480
        val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray()
    }

    private fun sendPhoto(jpeg: ByteArray) {
        io.launch {
            runCatching { CommandSender.sendBytes(applicationContext, CommandProtocol.PATH_PHOTO, jpeg) }
            runCatching {
                CommandSender.send(
                    applicationContext,
                    CommandProtocol.build(CommandProtocol.RSP_STATUS, "Foto cekildi")
                )
            }
            stopSelf()
        }
    }

    private fun fail(msg: String) {
        io.launch {
            runCatching {
                CommandSender.send(applicationContext, CommandProtocol.build(CommandProtocol.RSP_ERROR, msg))
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4303
        const val EXTRA_FRONT = "front"

        fun capture(context: Context, front: Boolean) {
            val i = Intent(context, PhotoService::class.java).putExtra(EXTRA_FRONT, front)
            ContextCompat.startForegroundService(context, i)
        }
    }
}
