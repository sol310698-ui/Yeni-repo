package com.example.wearcommands.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Gorunmez, kilit-ustu Activity ile fotograf ceker. Activity on planda
 * sayildigi icin telefon kilitliyken/arka plandayken bile kameraya erisir —
 * arka plan foreground-servis kamera kisitlamasini asar.
 *
 * reason == null ise normal foto (/photo), doluysa guvenlik selfie'si
 * (/security, on kamera). Isi bitince kendini kapatir.
 */
class CaptureActivity : AppCompatActivity() {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            error("Kamera izni yok"); finish(); return
        }

        val front = intent.getBooleanExtra(EXTRA_FRONT, true)
        val reason = intent.getStringExtra(EXTRA_REASON)
        capture(front, reason)
    }

    private fun capture(front: Boolean, reason: String?) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull()
            if (provider == null) { error("Kamera acilamadi"); finish(); return@addListener }

            val selector = CameraSelector.Builder()
                .requireLensFacing(if (front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, imageCapture)
            }.onFailure { error("Kamera baglanamadi"); finish(); return@addListener }

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val jpeg = toJpeg(image)
                        image.close()
                        runCatching { provider.unbindAll() }
                        send(jpeg, reason)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runCatching { provider.unbindAll() }
                        error("Foto hatasi: ${exception.message}")
                        finish()
                    }
                }
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toJpeg(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val max = 480
        val scale = minOf(1f, max.toFloat() / maxOf(src.width, src.height))
        val scaled = Bitmap.createScaledBitmap(
            src, (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1), true
        )
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray()
    }

    private fun send(jpeg: ByteArray, reason: String?) {
        io.launch {
            if (reason != null) {
                val rb = reason.toByteArray(Charsets.UTF_8)
                val payload = ByteArray(rb.size + 1 + jpeg.size)
                System.arraycopy(rb, 0, payload, 0, rb.size)
                payload[rb.size] = 0
                System.arraycopy(jpeg, 0, payload, rb.size + 1, jpeg.size)
                runCatching { CommandSender.sendBytes(applicationContext, CommandProtocol.PATH_SECURITY, payload) }
            } else {
                runCatching { CommandSender.sendBytes(applicationContext, CommandProtocol.PATH_PHOTO, jpeg) }
                runCatching {
                    CommandSender.send(applicationContext, CommandProtocol.build(CommandProtocol.RSP_STATUS, "Foto cekildi"))
                }
            }
            runOnUiThread { finish() }
        }
    }

    private fun error(msg: String) {
        io.launch {
            runCatching {
                CommandSender.send(applicationContext, CommandProtocol.build(CommandProtocol.RSP_ERROR, msg))
            }
        }
    }

    companion object {
        const val EXTRA_FRONT = "front"
        const val EXTRA_REASON = "reason"

        private fun launch(context: Context, front: Boolean, reason: String?) {
            val app = context.applicationContext
            // Arka plandayken Activity ancak "Ustte gosterme" izniyle acilir.
            val canBackgroundStart = MobileApp.isForeground ||
                android.provider.Settings.canDrawOverlays(app)
            if (!canBackgroundStart) {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        CommandSender.send(
                            app,
                            CommandProtocol.build(CommandProtocol.RSP_ERROR, "Telefonda 'Ustte gosterme' iznini ver")
                        )
                    }
                }
                return
            }
            val i = Intent(context, CaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_FRONT, front)
            if (reason != null) i.putExtra(EXTRA_REASON, reason)
            runCatching { context.startActivity(i) }
        }

        /** Normal foto. */
        fun capture(context: Context, front: Boolean) = launch(context, front, null)

        /** Guvenlik selfie'si (on kamera). */
        fun captureSecurity(context: Context, reason: String) = launch(context, true, reason)
    }
}
