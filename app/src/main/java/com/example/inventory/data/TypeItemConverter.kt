package com.example.inventory.data

import androidx.room.TypeConverter

class TypeItemConverter
{
    @TypeConverter
    fun fromTypeItem(typeItem: TypeItem):String{
        return typeItem.name
    }

    @TypeConverter
    fun toTypeItem(typeItem: String):TypeItem
    {
        return when {
            typeItem == TypeItem.manual.name -> TypeItem.manual
            typeItem == TypeItem.file.name -> TypeItem.file
            else -> { TypeItem.manual }
        }
    }
}