package com.nuitcode.daytesk.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nuitcode.daytesk.data.MockData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [TareaEntity::class, InboxItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tareaDao(): TareaDao
    abstract fun inboxItemDao(): InboxItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "daytesk.db",
            ).addCallback(
                object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            populateDatabase(INSTANCE ?: return@launch)
                        }
                    }
                },
            ).build()
        }

        private suspend fun populateDatabase(db: AppDatabase) {
            MockData.tareas.forEach { tarea ->
                db.tareaDao().insertTarea(tarea.toEntity())
            }
            MockData.inboxItems.forEach { item ->
                db.inboxItemDao().insertItem(item.toEntity())
            }
        }
    }
}
