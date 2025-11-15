package com.example.inventory

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val keyAlias = "secure_prefs_key"

    init {
        createKeyIfNeeded()
    }

    private fun createKeyIfNeeded() {
        if (!keyStore.containsAlias(keyAlias)) {
            try {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val keySpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(false)
                    .build()
                keyGenerator.init(keySpec)
                keyGenerator.generateKey()
            } catch (e: Exception) {
                Log.e("CryptoManager", "Failed to create key: ${e.message}")
            }
        }
    }

    private fun getKey(): Key? {
        return try {
            keyStore.getKey(keyAlias, null)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Failed to get key: ${e.message}")
            null
        }
    }

    fun encrypt(value: String): String? {
        val key = getKey() ?: return null

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            Base64.encodeToString(iv + encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Encryption failed: ${e.message}")
            null
        }
    }

    fun decrypt(encryptedValue: String): String? {
        val key = getKey() ?: return null

        return try {
            val decoded = Base64.decode(encryptedValue, Base64.DEFAULT)
            val iv = decoded.copyOfRange(0, 12)
            val encryptedBytes = decoded.copyOfRange(12, decoded.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Decryption failed: ${e.message}")
            null
        }
    }
}