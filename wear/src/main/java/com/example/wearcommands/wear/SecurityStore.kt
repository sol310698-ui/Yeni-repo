package com.example.wearcommands.wear

import android.content.Context
import java.io.File

/** Guvenlik selfie'lerini (sebep + zaman ile) saatte saklar ve listeler. */
object SecurityStore {

    data class Entry(val file: File, val reason: String, val time: Long)

    private fun dir(context: Context): File =
        File(context.filesDir, "security").apply { mkdirs() }

    fun save(context: Context, reason: String, jpeg: ByteArray): File {
        val t = System.currentTimeMillis()
        val slug = reason.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(24).ifBlank { "olay" }
        val f = File(dir(context), "sec_${t}__$slug.jpg")
        f.writeBytes(jpeg)
        return f
    }

    fun list(context: Context): List<Entry> =
        dir(context).listFiles()?.mapNotNull { parse(it) }?.sortedByDescending { it.time } ?: emptyList()

    private fun parse(f: File): Entry? {
        val name = f.name.removePrefix("sec_").removeSuffix(".jpg")
        val parts = name.split("__", limit = 2)
        val time = parts.getOrNull(0)?.toLongOrNull() ?: f.lastModified()
        val reason = parts.getOrNull(1)?.replace("-", " ")?.trim().takeUnless { it.isNullOrBlank() } ?: "Guvenlik"
        return Entry(f, reason, time)
    }
}
