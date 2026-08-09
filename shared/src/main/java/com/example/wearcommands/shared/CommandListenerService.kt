package com.example.wearcommands.shared

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Karsi cihazdan gelen mesajlari dinleyen servis (hem telefon hem saat).
 *
 * - [CommandProtocol.PATH_PHOTO]: gelen JPEG [PhotoBus]'a yayilir.
 * - [CommandProtocol.PATH_COMMAND]: metin komutu once [CommandBus]'a yayilir
 *   (ekranda gostermek icin), sonra tum cihazlarda gecerli yerlesik tepkiler
 *   (PING->PONG, VIBRATE) uygulanir ve son olarak cihaza ozel
 *   [CommandRegistry.executor]'a devredilir (telefon: fener/kilit/konum vb.).
 */
class CommandListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            CommandProtocol.PATH_PHOTO -> {
                PhotoBus.publish(event.data)
                return
            }
            CommandProtocol.PATH_COMMAND -> {
                val command = String(event.data, Charsets.UTF_8)
                CommandBus.publish(command)
                handleBuiltIn(command)
                CommandRegistry.executor?.onCommand(applicationContext, command)
            }
        }
    }

    /** Her iki cihazda da gecerli yerlesik tepkiler. */
    private fun handleBuiltIn(command: String) {
        when (command) {
            CommandProtocol.CMD_PING -> respond(CommandProtocol.CMD_PONG)
            CommandProtocol.CMD_VIBRATE -> vibrate()
        }
    }

    private fun respond(command: String) {
        scope.launch { runCatching { CommandSender.send(applicationContext, command) } }
    }

    private fun vibrate() {
        val vibrator = resolveVibrator() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(400)
        }
    }

    private fun resolveVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
