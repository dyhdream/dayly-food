package com.daylyfood.app.data.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's API key encrypted with a hardware-backed key in Android Keystore.
 * The key never leaves the device, never appears in the backup file, and is not readable
 * by other apps.
 */
@Singleton
class AiKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("ai_keys", Context.MODE_PRIVATE)

    fun save(providerId: String, apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(apiKey.trim().toByteArray())
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(providerId, payload).apply()
    }

    fun read(providerId: String): String? {
        val payload = prefs.getString(providerId, null) ?: return null
        val parts = payload.split(":")
        if (parts.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
        }.getOrNull()
    }

    fun has(providerId: String): Boolean = prefs.contains(providerId)

    fun clear(providerId: String) {
        prefs.edit().remove(providerId).apply()
    }

    /** Masked form for display, e.g. "sk-a…3f9c". */
    fun masked(providerId: String): String? {
        val key = read(providerId) ?: return null
        if (key.length <= 8) return "已保存"
        return key.take(4) + "…" + key.takeLast(4)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "dayly-food-ai-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
