package com.example.wearcommands.shared

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Karsi cihazdan gelen fotograflari (JPEG byte[]) uygulama icine yayar.
 * [CommandListenerService] fotograf mesajini alinca burayi tetikler; saatteki
 * ekran bu akisi dinleyip onizlemeyi gosterir.
 */
object PhotoBus {

    private val _incoming = MutableSharedFlow<ByteArray>(
        replay = 1,
        extraBufferCapacity = 4
    )
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    fun publish(jpeg: ByteArray) {
        _incoming.tryEmit(jpeg)
    }
}
