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
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.Item
import com.example.inventory.data.ItemsRepository
import com.example.inventory.data.OfflineItemsRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import com.example.inventory.SecurityManager
import com.example.inventory.data.TypeItem
import com.example.inventory.ui.settings.SettingsAttr

/**
 * ViewModel to validate and insert items in the Room database.
 */
class ItemEntryViewModel(
    private val itemsRepository: ItemsRepository
) : ViewModel() {

    /**
     * Holds current item ui state
     */

    companion object {
        const val PHONE_PATTERN = """^\+[1-9][0-9]{3,14}$"""
        const val EMAIL_PATTERN = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"""
    }


    var itemUiState by mutableStateOf(ItemUiState())
        private set

    var sManager:SecurityManager? = null
        private set

    init {

        viewModelScope.launch {
            sManager = SecurityManager.getInstance()

        }
    }

    /**
     * Updates the [itemUiState] with the value provided in the argument. This method also triggers
     * a validation for input values.
     */
    fun updateUiState(itemDetails: ItemDetails) {
        val resValidate  = validateInput(itemDetails)
        itemUiState =
            ItemUiState(itemDetails = itemDetails, isEntryValid = resValidate.all { it.value }, mapError = resValidate)
    }
    fun getShared(context: Context, attr: SettingsAttr):String{
        val res = sManager?.getSettings(context,attr.key) ?: ""
        Log.d("ItemInputForm","key = ${attr.key}")
        Log.d("ItemInputForm", "getShared: [$res]")
        return  sManager?.getSettings(context,attr.key) ?: ""
    }

    private fun validateInput(uiState: ItemDetails = itemUiState.itemDetails): Map<String,Boolean> {
        return with(uiState) {
            val patternPhone =  PHONE_PATTERN.toRegex()
            val patternEmail = EMAIL_PATTERN.toRegex()
            val priceCheck  = price.isNotBlank()
            val nameCheck  = name.isNotBlank()
            val quantityCheck  = quantity.isNotBlank()
            val supplierNameCheck  =  supplierName.isNotBlank() && supplierName.length >= 5
            val supplierEmailCheck =  supplierEmail.isNotBlank() && patternEmail.matches(supplierEmail)
            val supplierPhoneNumberCheck = supplierPhoneNumber.isNotBlank()
                    && patternPhone.matches(supplierPhoneNumber)

            val res = mutableMapOf(
            "name" to  nameCheck,
            "price" to priceCheck,
            "quantity" to quantityCheck,
            "supplierName" to supplierNameCheck,
            "supplierEmail" to supplierEmailCheck,
            "supplierPhoneNumber" to  supplierPhoneNumberCheck)

            res

        }
    }

     fun saveItem(){
        viewModelScope.launch {
            if (validateInput().all { it.value })
            {
                itemsRepository.insert(itemUiState.itemDetails.toItem())
            }
        }
    }

    fun createWithFile(context: Context, destinationUri: Uri){
        viewModelScope.launch {
            val content = sManager?.readFile(context,destinationUri)
            content?.let {
                val decryptContent = sManager?.decrypt(context,it)
                decryptContent?.let { item ->
                    val newItemDetail = ItemDetails(
                        name = item.name,
                        price = item.price,
                        quantity = item.quantity,
                        supplierName = item.supplierName,
                        supplierEmail = item.supplierEmail,
                        supplierPhoneNumber = item.supplierPhoneNumber,
                        typeItem = TypeItem.file
                    )
                    if (validateInput(newItemDetail).all { it.value })
                    {
                        itemsRepository.insert(newItemDetail.toItem())
                    }
                } ?: run {
                    Toast.makeText(context,"Error during decrypt content",Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(context,"Error during read content from file",Toast.LENGTH_LONG).show()
            }
        }
    }
}

/**
 * Represents Ui State for an Item.
 */
data class ItemUiState(
    val itemDetails: ItemDetails = ItemDetails(),
    val isEntryValid: Boolean = false,
    val mapError: Map<String,Boolean> = mapOf(
        "name" to false,
        "price" to false,
        "quantity" to false,
        "supplierName" to false,
        "supplierEmail" to false,
        "supplierPhoneNumber" to false
    )
)

@Serializable
data class ItemDetails(
    val id: Int = 0,
    val name: String = "",
    val price: String = "",
    val quantity: String = "",
    val supplierName:String = "",
    val supplierEmail:String = "",
    val supplierPhoneNumber:String = "",
    val typeItem:TypeItem = TypeItem.manual
)

/**
 * Extension function to convert [ItemDetails] to [Item]. If the value of [ItemDetails.price] is
 * not a valid [Double], then the price will be set to 0.0. Similarly if the value of
 * [ItemDetails.quantity] is not a valid [Int], then the quantity will be set to 0
 */
fun ItemDetails.toItem(): Item = Item(
    id = id,
    name = name,
    price = price.toDoubleOrNull() ?: 0.0,
    quantity = quantity.toIntOrNull() ?: 0,
    supplierName = supplierName,
    supplierEmail = supplierEmail,
    supplierPhoneNumber = supplierPhoneNumber,
    type = typeItem
)

fun Item.formatedPrice(): String {
    return NumberFormat.getCurrencyInstance().format(price)
}

/**
 * Extension function to convert [Item] to [ItemUiState]
 */
fun Item.toItemUiState(isEntryValid: Boolean = false): ItemUiState = ItemUiState(
    itemDetails = this.toItemDetails(),
    isEntryValid = isEntryValid
)

/**
 * Extension function to convert [Item] to [ItemDetails]
 */
fun Item.toItemDetails(): ItemDetails = ItemDetails(
    id = id,
    name = name,
    price = price.toString(),
    quantity = quantity.toString(),
    supplierName = supplierName,
    supplierEmail = supplierEmail,
    supplierPhoneNumber = supplierPhoneNumber,
    typeItem = type
)
