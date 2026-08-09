package com.example.wearcommands.mobile

import android.content.Context
import com.example.wearcommands.shared.CommandExecutor
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Telefonda calisan komut isleyici. Saatten gelen komutlari ilgili
 * islem/servise yonlendirir ve gerektiginde saate geri bildirim gonderir.
 */
class PhoneCommandExecutor : CommandExecutor {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCommand(context: Context, command: String): Boolean {
        val ctx = context.applicationContext
        val (name, value) = CommandProtocol.split(command)
        val front = value.equals("front", ignoreCase = true)

        when (name) {
            CommandProtocol.CMD_TORCH_ON ->
                if (TorchController.setTorch(ctx, true)) status(ctx, "Fener acildi") else error(ctx, "Fener yok")
            CommandProtocol.CMD_TORCH_OFF ->
                if (TorchController.setTorch(ctx, false)) status(ctx, "Fener kapandi") else error(ctx, "Fener yok")

            CommandProtocol.CMD_ALARM_ON -> { AlarmService.start(ctx); status(ctx, "Alarm basladi") }
            CommandProtocol.CMD_ALARM_OFF -> { AlarmService.stop(ctx); status(ctx, "Alarm durdu") }

            CommandProtocol.CMD_LOCK ->
                if (AdminHelper.lockNow(ctx)) status(ctx, "Kilitlendi")
                else error(ctx, "Cihaz yoneticisi kapali")

            CommandProtocol.CMD_LOCATION -> io.launch {
                val link = LocationHelper.lastKnownLink(ctx)
                if (link != null) send(ctx, CommandProtocol.build(CommandProtocol.RSP_LOCATION, link))
                else send(ctx, CommandProtocol.build(CommandProtocol.RSP_ERROR, "Konum yok/izin yok"))
            }

            CommandProtocol.CMD_MESSAGE -> { Notifications.showMessage(ctx, value); status(ctx, "Mesaj gosterildi") }

            CommandProtocol.CMD_AUDIO_START -> { AudioRecordService.start(ctx); status(ctx, "Ses kaydi basladi") }
            CommandProtocol.CMD_AUDIO_STOP -> { AudioRecordService.stop(ctx); status(ctx, "Ses kaydi durdu") }

            CommandProtocol.CMD_VIDEO_START -> { VideoService.start(ctx, front); status(ctx, "Video basladi") }
            CommandProtocol.CMD_VIDEO_STOP -> { VideoService.stop(ctx); status(ctx, "Video durdu") }

            CommandProtocol.CMD_PHOTO -> PhotoService.capture(ctx, front)

            CommandProtocol.CMD_WIPE ->
                if (PinStore.verify(ctx, value)) {
                    if (AdminHelper.wipe(ctx)) status(ctx, "Silme baslatildi")
                    else error(ctx, "Cihaz yoneticisi kapali")
                } else error(ctx, "PIN yanlis")

            else -> return false
        }
        return true
    }

    private fun status(ctx: Context, text: String) =
        send(ctx, CommandProtocol.build(CommandProtocol.RSP_STATUS, text))

    private fun error(ctx: Context, text: String) =
        send(ctx, CommandProtocol.build(CommandProtocol.RSP_ERROR, text))

    private fun send(ctx: Context, msg: String) {
        io.launch { runCatching { CommandSender.send(ctx, msg) } }
    }
}
