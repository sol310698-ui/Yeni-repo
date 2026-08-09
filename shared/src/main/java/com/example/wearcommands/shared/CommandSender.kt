package com.example.wearcommands.shared

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Bagli tum cihazlara (telefon -> saat ya da saat -> telefon) veri gonderir.
 */
object CommandSender {

    /**
     * [command] komutunu su an bagli olan tum node'lara gonderir.
     * @return komutu basariyla teslim edilen node sayisi.
     */
    suspend fun send(context: Context, command: String): Int =
        sendBytes(context, CommandProtocol.PATH_COMMAND, command.toByteArray(Charsets.UTF_8))

    /**
     * Ham [payload]'i [path] yolundan bagli tum node'lara gonderir
     * (or. fotograf icin [CommandProtocol.PATH_PHOTO]).
     * @return teslim edilen node sayisi.
     */
    suspend fun sendBytes(context: Context, path: String, payload: ByteArray): Int {
        val appContext = context.applicationContext
        val nodeClient = Wearable.getNodeClient(appContext)
        val messageClient = Wearable.getMessageClient(appContext)

        val nodes = nodeClient.connectedNodes.await()
        var delivered = 0
        for (node in nodes) {
            messageClient.sendMessage(node.id, path, payload).await()
            delivered++
        }
        return delivered
    }
}
