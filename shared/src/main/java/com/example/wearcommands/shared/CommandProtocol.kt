package com.example.wearcommands.shared

/**
 * Telefon ve Wear OS saat arasinda gonderilen komutlarin ortak sozlesmesi.
 *
 * Komutlar UTF-8 metin olarak [PATH_COMMAND] yolundan gonderilir. Parametreli
 * komutlar "AD:deger" bicimindedir (or. "MSG:merhaba", "WIPE:1234").
 * Fotograf gibi ikili veriler ayri [PATH_PHOTO] yolundan gonderilir.
 */
object CommandProtocol {

    /** Metin komutlarinin gonderildigi yol. */
    const val PATH_COMMAND = "/command"

    /** Fotograf (JPEG byte[]) gonderilen yol (telefon -> saat). */
    const val PATH_PHOTO = "/photo"

    // --- Temel test komutlari ---
    const val CMD_PING = "PING"
    const val CMD_PONG = "PONG"
    const val CMD_VIBRATE = "VIBRATE"
    const val CMD_HELLO = "HELLO"

    // --- Uzaktan yonetim komutlari (saat -> telefon) ---
    const val CMD_TORCH_ON = "TORCH_ON"
    const val CMD_TORCH_OFF = "TORCH_OFF"
    const val CMD_ALARM_ON = "ALARM_ON"
    const val CMD_ALARM_OFF = "ALARM_OFF"
    const val CMD_LOCK = "LOCK"
    const val CMD_LOCATION = "LOCATION"
    const val CMD_AUDIO_START = "AUDIO_START"
    const val CMD_AUDIO_STOP = "AUDIO_STOP"
    const val CMD_VIDEO_START = "VIDEO_START"
    const val CMD_VIDEO_STOP = "VIDEO_STOP"
    const val CMD_PHOTO = "PHOTO"

    /** Parametreli: "MSG:<ekranda gosterilecek metin>". */
    const val CMD_MESSAGE = "MSG"

    /** Parametreli ve tehlikeli: "WIPE:<pin>". Telefonda PIN dogrulanir. */
    const val CMD_WIPE = "WIPE"

    // --- Telefon -> saat geri bildirim onekleri ---
    /** "KONUM:<harita baglantisi>". */
    const val RSP_LOCATION = "KONUM"

    /** "DURUM:<insan okunabilir durum metni>". */
    const val RSP_STATUS = "DURUM"

    /** "HATA:<mesaj>". */
    const val RSP_ERROR = "HATA"

    /** "AD:deger" komutunu (ad, deger) ciftine ayirir; deger yoksa bos string. */
    fun split(command: String): Pair<String, String> {
        val i = command.indexOf(':')
        return if (i < 0) command to "" else command.substring(0, i) to command.substring(i + 1)
    }

    /** "AD:deger" bicimli komut olusturur. */
    fun build(name: String, value: String): String = "$name:$value"
}
