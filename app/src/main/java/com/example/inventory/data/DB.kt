package com.example.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Item::class], version = 3, exportSchema = false)
abstract  class InventoryDatabase: RoomDatabase(){

     abstract fun getItemDAO(): ItemDAO

     companion object {

         @Volatile private var Instance: InventoryDatabase? = null

         fun getDatabase(context:Context): InventoryDatabase
         {
             return Instance ?: synchronized(this)
             {
                 Room.databaseBuilder(
                     context = context,
                     klass = InventoryDatabase::class.java,
                     "item_database"
                 )
                     .fallbackToDestructiveMigration()
                     .build()
                     .also {
                         Instance = it
                     }
             }
         }
     }
}