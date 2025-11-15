package com.example.inventory

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.inventory.ui.item.ItemDetails
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import com.ionspin.kotlin.crypto.secretbox.SecretBoxCorruptedOrTamperedDataExceptionOrInvalidKey
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import com.ionspin.kotlin.crypto.util.encodeToUByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import androidx.core.content.edit
import com.example.inventory.ui.settings.SettingsAttr
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.json.JSONObject


class SecurityManager private constructor()
{

    private  var  cryptoManager: CryptoManager

    init {
        cryptoManager = CryptoManager()
    }

    companion object {
        @Volatile
        private var instance: SecurityManager? = null

        suspend fun getInstance(): SecurityManager {
            if (instance == null) {

                LibsodiumInitializer.initialize()

                synchronized(this) {
                    if (instance == null) {
                        instance = SecurityManager()
                    }
                }
            }
            return instance!!
        }
    }



    @OptIn(ExperimentalUnsignedTypes::class)
    fun getOrCreateKey(context: Context):UByteArray{
        val prefs  =  context.getSharedPreferences("crypto_prefs",Context.MODE_PRIVATE)

       return prefs.getString("encrypt_key",null)?.let {
           Base64.decode(it,Base64.DEFAULT).asUByteArray()
        } ?: run {
            val key = LibsodiumRandom.buf(32)
            val base64Key = Base64.encodeToString(key.asByteArray(),Base64.DEFAULT)
            prefs.edit { putString("encrypt_key", base64Key) }
            key
       }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getOrCreateNonce(context: Context):UByteArray{
        val prefs = context.getSharedPreferences("crypto_prefs",Context.MODE_PRIVATE)

        return prefs.getString("encrypt_nonce",null)?.let {
            Base64.decode(it,Base64.DEFAULT).asUByteArray()
        } ?: run {
            val nonce = LibsodiumRandom.buf(24)
            val base64Nonce = Base64.encodeToString(nonce.asByteArray(),Base64.DEFAULT)
            prefs.edit {putString("encrypt_nonce",base64Nonce)}
            nonce
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun encrypt(context: Context,item: ItemDetails):UByteArray{
        val key  = getOrCreateKey(context)
        val nonce = getOrCreateNonce(context)
        val json = Json.encodeToString(item).encodeToUByteArray()
        val encrypted = SecretBox.easy(json, nonce, key)
        return encrypted
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun saveToCacheFile(context: Context, item: ItemDetails): File? = withContext(Dispatchers.IO){
        try {
            val encrypted  = encrypt(context,item)

            val pathDir = File(context.cacheDir,"encrypted_docs")

            if (!pathDir.exists()){
                pathDir.mkdirs()
            }

            val fullPath = File(pathDir,"encrypted_item_${item.id}.json")


            fullPath.outputStream().buffered().use{ outputStream ->
                outputStream.write(encrypted.asByteArray())

            }
            Log.d("SecurityManager" , "Path: ${fullPath.path}")
            return@withContext fullPath
        }
        catch (e:Exception){
            Log.e("SecurityManager", e.message!!)
            return@withContext null
        }
    }

    suspend fun readFile(context: Context,uri: Uri):ByteArray? = withContext(Dispatchers.IO){
        val content:ByteArray? = context.contentResolver.openInputStream(uri)?.buffered().use {
            bInputStream -> bInputStream?.readBytes()
        }
        return@withContext content
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalSerializationApi::class)
     fun decrypt(context: Context, content:ByteArray) : ItemDetails? {
        try {
            val key  = getOrCreateKey(context)
            val nonce = getOrCreateNonce(context)
            val decrypted = SecretBox.openEasy(content.asUByteArray(), nonce, key).asByteArray()
            val item = Json.decodeFromString<ItemDetails>(String(decrypted,Charsets.UTF_8))
            return item
        }
        catch (e: SecretBoxCorruptedOrTamperedDataExceptionOrInvalidKey)
        {
            Log.e("SecurityManager", "The content cannot be verified: ${e.message}")
            return null
        }
        catch (e:Exception){
            Log.e("SecurityManager", "${e.message}")
            return null
        }
    }

     fun saveSettings(context: Context,key: String, value: Boolean) {
        val encryptedValue = cryptoManager.encrypt(value.toString())
        context.getSharedPreferences("crypto_settings_prefs",Context.MODE_PRIVATE).edit {
            putString(
                key,
                encryptedValue
            )
        }
    }

     fun getSettings(context: Context,key: String):String?{
        val value = context.getSharedPreferences("crypto_settings_prefs",Context.MODE_PRIVATE).getString(key,null)
        if  (value != null) {
            val decryptedValue = cryptoManager.decrypt(value)
            Log.d("SecurityManager", "decrypt value: $decryptedValue")
            return decryptedValue
        }

       return null
     }

     fun saveAllSettings(context: Context,settings:Map<String,String>){

        val mappedSettings = settings.map {(k,v) -> k to cryptoManager.encrypt(v) }
        context.getSharedPreferences("crypto_settings_prefs",Context.MODE_PRIVATE).edit {
            mappedSettings.forEach { (k,v) -> putString(k, v) }
        }
    }

     fun getAllSettings(context: Context, keys:Map<String,String>) : Map<SettingsAttr?,String?>{

        val prefs = context.getSharedPreferences("crypto_settings_prefs",Context.MODE_PRIVATE)
        val resMap = keys
            .map { (k,_) -> k to prefs.getString(k,null) }
            .filter { (_,v) -> v != null }
            .toMap()

        val decrypted = resMap.map { (k, v) ->
            val settingsAttr = SettingsAttr.entries.find { it.key == k }
            settingsAttr to cryptoManager.decrypt(v!!)
        }.filter { (attr, v) -> attr != null && v != null }.toMap()

        return decrypted
    }


}