package com.example.wearcommands.mobile

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekranda hicbir sey gostermeden ortam sesini kaydeder (gizli).
 *
 * Kayit, dosya yoneticisi/Muzik uygulamasindan gorunen ortak klasore yazilir:
 * Android 10+ -> Music/WearCommands (MediaStore), altinda ise dogrudan
 * paylasilan Music/WearCommands klasorune.
 */
class AudioRecordService : Service() {

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null

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
            val name = "ses_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".m4a"

            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/WearCommands")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                pendingUri = uri
                val fd = uri?.let { contentResolver.openFileDescriptor(it, "w") }
                pfd = fd
                r.setOutputFile(fd!!.fileDescriptor)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "WearCommands")
                dir.mkdirs()
                r.setOutputFile(File(dir, name).absolutePath)
            }

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
        pfd?.runCatching { close() }
        pfd = null
        // MediaStore kaydini gorunur yap.
        pendingUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(uri, done, null, null) }
            }
        }
        pendingUri = null
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
