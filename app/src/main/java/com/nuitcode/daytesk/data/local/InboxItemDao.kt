package com.nuitcode.daytesk.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxItemDao {
    @Query("SELECT * FROM inbox_items ORDER BY timestamp DESC")
    fun getAllItems(): Flow<List<InboxItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InboxItemEntity): Long

    @Update
    suspend fun updateItem(item: InboxItemEntity)

    @Delete
    suspend fun deleteItem(item: InboxItemEntity)

    @Query("DELETE FROM inbox_items")
    suspend fun deleteAll()
}
