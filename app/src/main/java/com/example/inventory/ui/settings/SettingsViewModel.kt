package com.example.inventory.ui.settings

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.inventory.SecurityManager
import kotlinx.coroutines.launch


class SettingsViewModel:ViewModel()
{
     var  _settingsPackState by mutableStateOf(SettingsPack())
        private set

    var sManager:SecurityManager? = null
        private set

    init {
            viewModelScope.launch {
                sManager = SecurityManager.getInstance()
            }


    }

    private val _defaultQuantity = MutableStateFlow("")
    val defaultQuantity:StateFlow<String> = _defaultQuantity.asStateFlow()

    fun changeSettings(pack: SettingsPack){
        _settingsPackState = pack
        Log.d("Settings", _settingsPackState.toString())
    }

    fun changeDefaultValueForQuantity(value:String){
        _defaultQuantity.value = value

        Log.d("Settings", "Default: ${_defaultQuantity.value}")
    }


    fun saveSettings(context: Context){
        viewModelScope.launch {
            val settingsToSave = mapOf( SettingsAttr.SENS.key to _settingsPackState.sensitiveData.toString(),
                SettingsAttr.BAN_SHARE.key to _settingsPackState.banShare.toString(),
                SettingsAttr.DEFAULT_QUANTITY.key to _settingsPackState.defaultValueQuantity.toString(),
                SettingsAttr.DEFAULT_QUANTITY_VALUE.key to defaultQuantity.value)
            sManager?.saveAllSettings(context,settingsToSave)
        }
    }

    fun getSettings(context: Context){
        viewModelScope.launch {
            val keys = mapOf(SettingsAttr.SENS.key to "",
                SettingsAttr.BAN_SHARE.key to "",
                SettingsAttr.DEFAULT_QUANTITY.key to "",
                SettingsAttr.DEFAULT_QUANTITY_VALUE.key to "")
            val data = sManager?.getAllSettings(context,keys)

            data?.let {
                _settingsPackState = SettingsPack(
                    sensitiveData = data[SettingsAttr.SENS].toBoolean(),
                    banShare = data[SettingsAttr.BAN_SHARE].toBoolean(),
                    defaultValueQuantity = data[SettingsAttr.DEFAULT_QUANTITY].toBoolean())
                _defaultQuantity.value = data[SettingsAttr.DEFAULT_QUANTITY_VALUE] ?: ""
            }

        }
    }
}

data class SettingsPack(
    val sensitiveData: Boolean = false,
    val banShare: Boolean = false,
    val defaultValueQuantity: Boolean = false
)

enum class SettingsAttr(val key: String){
    SENS("sensitive_data_prefs"),
    BAN_SHARE("ban_share_prefs"),
    DEFAULT_QUANTITY("default_quantity_state_prefs"),
    DEFAULT_QUANTITY_VALUE("default_quantity_value_prefs")

}