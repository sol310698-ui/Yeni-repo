package com.example.wearcommands.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekranda hicbir sey gostermeden ortam sesini kaydeder (gizli). Kayit
 * telefonun ozel klasorune yazilir: Android/data/.../files/kayitlar/
 * ACTION_START ile baslar, ACTION_STOP ile durur.
 */
class AudioRecordService : Service() {

    private var recorder: MediaRecorder? = null
    private var outFile: File? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> start()
        }
        return START_STICKY
    }

    private fun start() {
        startForegroundCompat()
        runCatching {
            val dir = File(getExternalFilesDir(null), "kayitlar").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val f = File(dir, "ses_$stamp.m4a")
            outFile = f

            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r
        }.onFailure { stopSelf() }
    }

    private fun startForegroundCompat() {
        val notif = Notifications.serviceNotification(this, "Kayit")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4302
        const val ACTION_START = "com.example.wearcommands.AUDIO_START"
        const val ACTION_STOP = "com.example.wearcommands.AUDIO_STOP"

        fun start(context: Context) {
            val i = Intent(context, AudioRecordService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, AudioRecordService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
