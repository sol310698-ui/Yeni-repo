package com.example.wearcommands.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.wearcommands.mobile.databinding.ActivityScanBinding
import com.example.wearcommands.shared.CryptoManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Saatteki eslesme QR kodunu kamerayla tarar; okunan gizli anahtari kaydeder.
 * Bundan sonra tum paketler bu anahtarla sifrelenir.
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        startCamera()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: run {
                toast("Kamera acilamadi"); finish(); return@addListener
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, QrAnalyzer { text -> onQr(text) })

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { toast("Kamera baglanamadi"); finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onQr(text: String) {
        if (handled) return
        handled = true
        runOnUiThread {
            CryptoManager.setSecret(applicationContext, text)
            toast("Eşleştirildi — şifreleme aktif")
            finish()
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    /** ImageProxy Y duzleminden QR cozer. */
    private class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
        private val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

        override fun analyze(image: ImageProxy) {
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val source = PlanarYUVLuminanceSource(
                    bytes, plane.rowStride, image.height,
                    0, 0, image.width, image.height, false
                )
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decodeWithState(bitmap)
                onFound(result.text)
            } catch (e: Exception) {
                // QR bulunamadi; sonraki kare denenecek.
            } finally {
                reader.reset()
                image.close()
            }
        }
    }
}
