package com.example.wearcommands.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekranda onizleme gostermeden gizli video kaydeder. ACTION_START ile baslar,
 * ACTION_STOP ile durur. Dosya telefonun ozel klasorune yazilir.
 */
class VideoService : LifecycleService() {

    private var recording: Recording? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopRecording(); stopSelf(); return START_NOT_STICKY }
            else -> { startForegroundCompat(); startRecording(intent?.getBooleanExtra(EXTRA_FRONT, false) ?: false) }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notif = Notifications.serviceNotification(this, "Video kaydi")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (hasAudio()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(NOTIF_ID, notif, type)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun hasAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startRecording(front: Boolean) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: run { stopSelf(); return@addListener }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            val selector = CameraSelector.Builder()
                .requireLensFacing(if (front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK)
                .build()

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, videoCapture)
            }.onFailure { stopSelf(); return@addListener }

            val dir = File(getExternalFilesDir(null), "kayitlar").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "video_$stamp.mp4")
            val options = FileOutputOptions.Builder(file).build()

            var pending = videoCapture.output.prepareRecording(this, options)
            if (hasAudio()) pending = pending.withAudioEnabled()

            recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    runCatching { provider.unbindAll() }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopRecording() {
        recording?.runCatching { stop() }
        recording = null
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4304
        const val EXTRA_FRONT = "front"
        const val ACTION_START = "com.example.wearcommands.VIDEO_START"
        const val ACTION_STOP = "com.example.wearcommands.VIDEO_STOP"

        fun start(context: Context, front: Boolean) {
            val i = Intent(context, VideoService::class.java).setAction(ACTION_START).putExtra(EXTRA_FRONT, front)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, VideoService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
