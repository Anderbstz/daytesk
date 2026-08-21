package com.nuitcode.daytesk.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TareaDao {
    @Query("SELECT * FROM tareas ORDER BY orden ASC")
    fun getAllTareas(): Flow<List<TareaEntity>>

    @Query("SELECT * FROM tareas ORDER BY orden ASC")
    suspend fun getAllOnce(): List<TareaEntity>

    @Query("SELECT * FROM tareas WHERE id = :id")
    suspend fun getTareaById(id: Long): TareaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarea(tarea: TareaEntity): Long

    @Update
    suspend fun updateTarea(tarea: TareaEntity)

    @Delete
    suspend fun deleteTarea(tarea: TareaEntity)

    @Query("DELETE FROM tareas")
    suspend fun deleteAll()
}
