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
    entities = [TareaEntity::class, InboxItemEntity::class, ContextoEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tareaDao(): TareaDao
    abstract fun inboxItemDao(): InboxItemDao
    abstract fun contextoDao(): ContextoDao

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
            )
                .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateDatabase(INSTANCE ?: return@launch)
                            }
                        }
                    },
                )
                .build()
        }

        /**
         * First-install path. Seeds the 4 default `contextos` rows and then the
         * existing mock tareas / inbox items. On upgrade from v1 the migration
         * already seeded the same 4 rows so this callback is a no-op for them
         * (INSERT OR IGNORE in the migration guards against duplicates).
         */
        private suspend fun populateDatabase(db: AppDatabase) {
            val contextoDao = db.contextoDao()
            // Seed only if empty — first install path. The migration already
            // handles upgrade seeding; this callback only fires onCreate.
            if (contextoDao.getById(1) == null) {
                ContextoSeed.entries.forEach { seed ->
                    contextoDao.insert(seed.toEntity())
                }
            }
            MockData.tareas.forEach { tarea ->
                db.tareaDao().insertTarea(tarea.toEntity())
            }
            MockData.inboxItems.forEach { item ->
                db.inboxItemDao().insertItem(item.toEntity())
            }
        }
    }
}

/**
 * Hard-coded seed rows for the first-install path. Mirrors the migration in
 * [Migrations.MIGRATION_1_2] — same ids (1..4), same `esDefault = true`,
 * same orden (id).
 *
 * Colors are `Int` ARGB literals matching the Light palette in
 * `theme/Color.kt` so the seed matches what `Contexto.DEFAULTS` exposes
 * to the domain layer.
 */
internal enum class ContextoSeed(
    val id: Long,
    val nombre: String,
    val color: Int,
    val orden: Int,
) {
    CASA(1, "casa", 0xFFFBC4AB.toInt(), 1),
    TRABAJO(2, "trabajo", 0xFF7DD6F0.toInt(), 2),
    PERSONAL(3, "personal", 0xFFD4B8FD.toInt(), 3),
    SALUD(4, "salud", 0xFFB2E87A.toInt(), 4);

    fun toEntity(): ContextoEntity = ContextoEntity(
        id = id,
        nombre = nombre,
        color = color,
        iconId = null,
        orden = orden,
        esDefault = true,
    )
}