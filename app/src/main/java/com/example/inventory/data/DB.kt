package com.example.inventory.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [Item::class], version = 5, exportSchema = false)
abstract class InventoryDatabase : RoomDatabase() {

    abstract fun getItemDAO(): ItemDAO

    companion object {
        private val TAG = "InventoryDatabase"

        @Volatile
        private var Instance: InventoryDatabase? = null
        private const val DATABASE_NAME = "item_database"

        fun getDatabase(context: Context): InventoryDatabase {
            SQLiteDatabase.loadLibs(context)
            val sqlCipherHelper = SqlCipherHelper(context)

            if (!sqlCipherHelper.isEncrypted(context, DATABASE_NAME)) {
                sqlCipherHelper.convertToEncrypt(context, DATABASE_NAME)
                Log.d("SQL", "База зашифрована")
            }

            return Instance ?: synchronized(this) {

                val passPhrase = SQLiteDatabase.getBytes(sqlCipherHelper.getCipherKey().toHex().toCharArray())
                val factory = SupportFactory(passPhrase)

                Room.databaseBuilder(
                    context = context,
                    klass = InventoryDatabase::class.java,
                    name = DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .openHelperFactory(factory)
                    .build()
                    .also {
                        Instance = it
                        Log.d(TAG, "Database instance created")
                    }
            }
        }
    }
}