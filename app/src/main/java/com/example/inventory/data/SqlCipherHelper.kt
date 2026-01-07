package com.example.inventory.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.sqlcipher.database.SQLiteDatabase
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SqlCipherHelper(
    context: Context
) {

    private val TAG = "SqlCipherHelper"
    private val masterKey = MasterKey.Builder(context, "sql_master_prefs_key")
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "sql_cipher_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val aliasMasterKey: String = "db_master_key"
    private val secretKeyToCipher: SecretKey
    private val keyStoreHelper = KeyStoreHelper()

    init {
        Log.d(TAG, "init helper")
        secretKeyToCipher = keyStoreHelper.getKeyFromKeyStore(aliasMasterKey)
        if (!encryptedPrefs.contains("sql_cipher_key") || !encryptedPrefs.contains("sql_cipher_iv")) {
            createEncryptedSqlCipherKey(secretKeyToCipher)
        }
    }

    fun convertToEncrypt(context: Context, originalDB: String) {
        val original = context.getDatabasePath(originalDB)
        val encrypt = context.getDatabasePath("${original}_encrypted")

        if (original.exists() && !encrypt.exists()) {
            val cipherKey =
                getCipherKey() //достаем из  EncryptedSharedPreferences, дешифруем в массив байтов
            val chars = cipherKey.toHex().toCharArray()
            val passphrase = SQLiteDatabase.getBytes(chars)
            val db = SQLiteDatabase.openOrCreateDatabase(original, "", null)

            db.rawExecSQL("ATTACH DATABASE '${encrypt.path}' AS encrypted KEY '${String(passphrase)}'")
            db.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            db.rawExecSQL("DETACH DATABASE encrypted")

            db.close()

            original.delete()
            encrypt.renameTo(original)
        }
    }

    @SuppressLint("CommitPrefEdits")
    fun createEncryptedSqlCipherKey(secretKey: SecretKey) {
        try {
            val randomSqlCipherKey = ByteArray(32)
            SecureRandom().nextBytes(randomSqlCipherKey)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encrypted = cipher.doFinal(randomSqlCipherKey)

            encryptedPrefs.edit()
                .putString("sql_cipher_key", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("sql_cipher_iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()

            Log.d(TAG, "New SQLCipher key created and encrypted")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating encrypted SQLCipher key", e)
            throw RuntimeException("Failed to create SQLCipher key", e)
        }
    }

    fun getCipherKey(): ByteArray {
        val decryptedSqlCipherKeyBase64 = encryptedPrefs.getString("sql_cipher_key", null)
        val decryptedSqlCipherIvBase64 = encryptedPrefs.getString("sql_cipher_iv", null)

        if (decryptedSqlCipherKeyBase64 == null || decryptedSqlCipherIvBase64 == null) {
            createEncryptedSqlCipherKey(secretKeyToCipher)
            return getCipherKey()
        }

        val decryptedSqlCipherKey = Base64.decode(decryptedSqlCipherKeyBase64, Base64.NO_WRAP)
        val decryptedSqlCipherIv = Base64.decode(decryptedSqlCipherIvBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKeyToCipher,
            GCMParameterSpec(128, decryptedSqlCipherIv)
        )

        return  cipher.doFinal(decryptedSqlCipherKey)
    }

    fun isEncrypted(context: Context, dbName: String): Boolean {
        return try {
            val dbFile = context.getDatabasePath(dbName)

            if (!dbFile.exists()) {
                return true
            }

            val db = SQLiteDatabase.openOrCreateDatabase(
                dbFile,
                getCipherKey().toHex(),
                null,
                null
            )
            db.close()
            true
        } catch (e: Exception) {
            Log.e("SQL", e.message ?: "")
            false
        }
    }

}

fun ByteArray.toHex(): String =
    joinToString("") { "%02x".format(it) }