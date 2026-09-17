package com.nuitcode.daytesk.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordatorioDao {
    /** Ordered by `fecha` ascending so the soonest reminder comes first. */
    @Query("SELECT * FROM recordatorios ORDER BY fecha ASC")
    fun getAllRecordatorios(): Flow<List<RecordatorioEntity>>

    @Query("SELECT * FROM recordatorios ORDER BY fecha ASC")
    suspend fun getAllOnce(): List<RecordatorioEntity>

    @Query("SELECT * FROM recordatorios WHERE id = :id")
    suspend fun getById(id: Long): RecordatorioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recordatorio: RecordatorioEntity): Long

    @Update
    suspend fun update(recordatorio: RecordatorioEntity)

    @Delete
    suspend fun delete(recordatorio: RecordatorioEntity)

    @Query("DELETE FROM recordatorios")
    suspend fun deleteAll()
}
