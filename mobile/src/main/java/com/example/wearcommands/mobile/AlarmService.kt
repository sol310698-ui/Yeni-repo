package com.example.wearcommands.mobile

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder

/**
 * Telefonu yuksek sesle caldirir (sessiz modda bile) — "telefonumu bul".
 * ACTION_START ile baslar, ACTION_STOP ile susar.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var previousVolume = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> start()
        }
        return START_STICKY
    }

    private fun start() {
        startForeground(NOTIF_ID, Notifications.serviceNotification(this, "Alarm calıyor"))

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(
            AudioManager.STREAM_ALARM,
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        player = MediaPlayer().apply {
            setDataSource(this@AlarmService, uri)
            setAudioStreamType(AudioManager.STREAM_ALARM)
            isLooping = true
            prepare()
            start()
        }
    }

    override fun onDestroy() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        if (previousVolume >= 0) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4301
        const val ACTION_START = "com.example.wearcommands.ALARM_START"
        const val ACTION_STOP = "com.example.wearcommands.ALARM_STOP"

        fun start(context: Context) {
            val i = Intent(context, AlarmService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
