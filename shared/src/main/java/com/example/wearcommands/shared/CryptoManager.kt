package com.example.wearcommands.shared

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Telefon ve saat arasindaki tum paketleri sifreler.
 *
 * Eslesme: saat rastgele bir gizli anahtar uretir, QR olarak gosterir; telefon
 * QR'i tarayip ayni gizli anahtari kaydeder. Iki tarafta da AES-256 anahtari
 * = SHA-256(gizli anahtar) olarak turetilir. Sifreleme AES/GCM/NoPadding'dir.
 *
 * Paket bicimi (sifreli): [MAGIC][12 bayt IV][GCM sifreli veri].
 * Anahtar yoksa (henuz eslesme olmadiysa) veri duz gonderilir; alici tarafta
 * MAGIC yoksa veri oldugu gibi kullanilir. Boylece eslesmeden once de calisir.
 */
object CryptoManager {

    private const val PREF = "wc_crypto"
    private const val K_SECRET = "secret"
    private const val MAGIC: Byte = 0x01
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getSecret(ctx: Context): String? = prefs(ctx).getString(K_SECRET, null)
    fun hasKey(ctx: Context): Boolean = getSecret(ctx) != null
    fun setSecret(ctx: Context, secret: String) =
        prefs(ctx).edit().putString(K_SECRET, secret).apply()
    fun clear(ctx: Context) = prefs(ctx).edit().remove(K_SECRET).apply()

    /** Rastgele yeni gizli anahtar (QR icerigi). */
    fun generateSecret(): String {
        val b = ByteArray(24)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun aesKey(secret: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    /** Anahtar varsa sifreler; yoksa veriyi oldugu gibi dondurur. */
    fun encrypt(ctx: Context, plain: ByteArray): ByteArray {
        val secret = getSecret(ctx) ?: return plain
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey(secret), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        val out = ByteArray(1 + IV_LEN + ct.size)
        out[0] = MAGIC
        System.arraycopy(iv, 0, out, 1, IV_LEN)
        System.arraycopy(ct, 0, out, 1 + IV_LEN, ct.size)
        return out
    }

    /** Sifreli paketi cozer; sifreli degilse/anahtar yoksa oldugu gibi dondurur. */
    fun decrypt(ctx: Context, data: ByteArray): ByteArray {
        val secret = getSecret(ctx) ?: return data
        if (data.isEmpty() || data[0] != MAGIC || data.size <= 1 + IV_LEN) return data
        return try {
            val iv = data.copyOfRange(1, 1 + IV_LEN)
            val ct = data.copyOfRange(1 + IV_LEN, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey(secret), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ct)
        } catch (e: Exception) {
            data
        }
    }
}
