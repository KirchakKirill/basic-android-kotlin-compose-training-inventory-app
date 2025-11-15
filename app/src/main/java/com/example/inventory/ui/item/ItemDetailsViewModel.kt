/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.inventory.ui.item

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.inventory.SecurityManager
import com.example.inventory.data.ItemsRepository
import com.example.inventory.ui.settings.SettingsAttr
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.Writer


/**
 * ViewModel to retrieve, update and delete an item from the [ItemsRepository]'s data source.
 */
class ItemDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val itemsRepository: ItemsRepository
) : ViewModel() {

    private val itemId: Int = checkNotNull(savedStateHandle[ItemDetailsDestination.itemIdArg])
    val uiState: StateFlow<ItemDetailsUiState> = itemsRepository.getById(itemId).filterNotNull()
        .map { ItemDetailsUiState(outOfStock = it.quantity <= 0, itemDetails = it.toItemDetails()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(TIMEOUT_MILLIS),
            initialValue = ItemDetailsUiState()
        )

    private var cachedFile:String? by mutableStateOf(null)
    var sManager:SecurityManager? = null
        private set
    private val _sharedDataMap = MutableStateFlow<Map<SettingsAttr?,String?>>(mapOf())
    val sharedDataMap:StateFlow<Map<SettingsAttr?,String?>> = _sharedDataMap.asStateFlow()

    companion object {
        private const val TIMEOUT_MILLIS = 5_000L
    }

    init {

        viewModelScope.launch {
            sManager = SecurityManager.getInstance()
        }
    }

    fun reduceQuantityByOne() {

        viewModelScope.launch {
            val currentItem = uiState.value.itemDetails.toItem()

            if (currentItem.quantity > 0) {
                itemsRepository.update(currentItem.copy(quantity = currentItem.quantity - 1))
            }
        }
    }

    fun deleteItem() {
        viewModelScope.launch {
            itemsRepository.delete(uiState.value.itemDetails.toItem())
        }

    }

    suspend fun share(context: Context): Intent {
        val details = uiState.value.itemDetails
        val contentUri: Uri? = viewModelScope.async {
            if (saveItemJson(context, details)) {
                getUriForItemInfo(context, details.id)
            } else {
                null
            }
        }.await()

        Log.d("ContentURI", contentUri.toString())
        val intent = if (contentUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Item: ${details.name}\nPrice: ${details.price}\nQuantity: ${details.quantity}\n" +
                            "Supplier name: ${details.supplierName}\n Supplier email: ${details.supplierEmail}\n" +
                            "Supplier phone: ${details.supplierPhoneNumber}"
                )
                putExtra(Intent.EXTRA_SUBJECT, "Item: ${details.name}")
            }
        }


        return Intent.createChooser(intent, "Share Item Details")
    }

    private suspend fun saveItemJson(context: Context, details: ItemDetails): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val content = Json.encodeToString(details)
                val fileName = "item_${details.id}.json"
                val fileDir = File(context.filesDir, "my_docs")

                if (!fileDir.exists()) {
                    fileDir.mkdirs()
                }
                Log.d("ContentURI", fileDir.absolutePath)
                val fullPath = File(fileDir, fileName)
                if (fullPath.exists()) return@withContext true
                BufferedWriter(FileWriter(fullPath)).use {
                    it.write(content)
                }
                Log.d("ContentURI", "File saved: ${fullPath.absolutePath}")
                return@withContext true
            } catch (e: Exception) {
                Log.e("ContentURI", "Error saving file: ${e.message}", e)
                return@withContext false
            }

        }

    private fun getUriForItemInfo(context: Context, id: Int): Uri {
        val file = File(context.filesDir, "my_docs")
        val newFile = File(file, "item_$id.json")
        val contentUri = getUriForFile(context, "com.example.inventory.fileprovider", newFile)
        return contentUri
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun prepareSaveInFile(context: Context):File?{

        val file: File? = viewModelScope.async {
            val item = uiState.value.itemDetails
            sManager?.saveToCacheFile(context, item)
        }.await()

        if (file != null) {
            Toast.makeText(context, "File saved to path \'${file.path}\'", Toast.LENGTH_LONG).show()
            cachedFile = file.path
            return file
        } else {
            Toast.makeText(context, "File save failed", Toast.LENGTH_LONG).show()
            return null
        }

    }

    fun copyCachedFileToSelectedDestination(context: Context,destinationUri: Uri){
        viewModelScope.launch {
            cachedFile?.let {
                val sourceFile = File(it)
                if  (sourceFile.exists())
                {
                    try {
                        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                            sourceFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Toast.makeText(context, "File saved successfully!", Toast.LENGTH_LONG).show()
                    }
                    catch (e:Exception){
                        Toast.makeText(context, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                else{
                    Toast.makeText(context, "Cached file not found", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(context, "No file prepared for saving", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun getSharedData(context: Context){
        val keys = mapOf(
            SettingsAttr.SENS.key to "",
            SettingsAttr.BAN_SHARE.key to "",
            SettingsAttr.DEFAULT_QUANTITY.key to "",
            SettingsAttr.DEFAULT_QUANTITY_VALUE.key to "")

        val sharedDataMaP = sManager?.getAllSettings(context, keys)
        if  (sharedDataMaP != null)
        {
            _sharedDataMap.value = sharedDataMaP
        }
        else{
            Toast.makeText(context, "No shared data", Toast.LENGTH_LONG).show()
        }

    }
}


/**
 * UI state for ItemDetailsScreen
 */
data class ItemDetailsUiState(
    val outOfStock: Boolean = true,
    val itemDetails: ItemDetails = ItemDetails()
)
