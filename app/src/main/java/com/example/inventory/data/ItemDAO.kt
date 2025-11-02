package com.example.inventory.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDAO
{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item:Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(vararg items:Item)

    @Update
    suspend fun update(item:Item)

    @Delete
    suspend fun delete(item:Item)

    @Delete
    suspend fun deleteItems(items: List<Item>)

    @Query("SELECT * FROM items i WHERE i.id = :id")
    fun findById(id:Int): Flow<Item>

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun findAll(): Flow<List<Item>>

}