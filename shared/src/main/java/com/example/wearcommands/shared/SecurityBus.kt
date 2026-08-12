package com.example.wearcommands.shared

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Gelen guvenlik selfie'lerini (sebep + JPEG) uygulama icine yayar. */
object SecurityBus {

    data class Item(val reason: String, val jpeg: ByteArray)

    private val _incoming = MutableSharedFlow<Item>(replay = 1, extraBufferCapacity = 4)
    val incoming: SharedFlow<Item> = _incoming.asSharedFlow()

    fun publish(reason: String, jpeg: ByteArray) {
        _incoming.tryEmit(Item(reason, jpeg))
    }
}
