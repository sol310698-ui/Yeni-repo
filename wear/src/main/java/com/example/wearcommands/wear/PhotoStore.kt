package com.example.wearcommands.wear

import android.content.Context
import java.io.File

/** Saate gelen fotograflari saatin ic depolamasinda saklar ve listeler. */
object PhotoStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    /** Gelen JPEG'i kaydeder, olusan dosyayi dondurur. */
    fun save(context: Context, jpeg: ByteArray): File {
        val f = File(dir(context), "foto_${System.currentTimeMillis()}.jpg")
        f.writeBytes(jpeg)
        return f
    }

    /** Kayitli fotograflar, en yeni once. */
    fun list(context: Context): List<File> =
        dir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
}
