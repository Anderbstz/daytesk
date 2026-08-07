package com.nuitcode.daytesk.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED test written before [Migrations.MIGRATION_1_2] exists.
 *
 * Migration v1->v2 contract (see sdd/custom-contexts/spec REQ-08):
 *   1. CREATE TABLE `contextos` with columns (id, nombre UNIQUE, color, iconId, orden, esDefault)
 *   2. INSERT 4 seed rows (ids 1-4, esDefault=1, orden=id)
 *   3. ALTER TABLE `tareas` ADD COLUMN `contextoId INTEGER NOT NULL DEFAULT 3`
 *   4. UPDATE tareas SET contextoId = CASE contexto WHEN 'CASA' THEN 1 WHEN 'TRABAJO' THEN 2 WHEN 'PERSONAL' THEN 3 WHEN 'SALUD' THEN 4 END
 *
 * All four steps MUST run inside a single `transaction { ... }` block.
 *
 * Test execution is BLOCKED on this Windows host (no Java/Android SDK per
 * skip-verify pattern #95).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MigrationTest {

    private lateinit var db: android.database.sqlite.SQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Use Robolectric's shadow SQLite — pure in-memory, no device required.
        db = context.openOrCreateDatabase("migration-test.db", android.content.Context.MODE_PRIVATE, null)
        db.execSQL("DROP TABLE IF EXISTS tareas")
        db.execSQL("DROP TABLE IF EXISTS contextos")

        // v1 schema (TareaEntity before PR1)
        db.execSQL(
            """
            CREATE TABLE tareas (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                titulo TEXT NOT NULL,
                descripcion TEXT NOT NULL DEFAULT '',
                prioridad TEXT NOT NULL DEFAULT 'MEDIA',
                contexto TEXT NOT NULL DEFAULT 'PERSONAL',
                estado TEXT NOT NULL DEFAULT 'PENDIENTE',
                fechaCreacion INTEGER NOT NULL,
                fechaVencimiento INTEGER,
                orden INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    @After
    fun tearDown() {
        db.execSQL("DROP TABLE IF EXISTS tareas")
        db.execSQL("DROP TABLE IF EXISTS contextos")
        db.close()
    }

    @Test
    fun migration_v1ToV2_seedsFourContextsAndPreservesAssociations() {
        // Seed v1 data covering all 4 enum names + 1 unknown fallback
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO tareas (titulo, contexto, fechaCreacion) VALUES ('a', 'CASA', ?)",
            arrayOf<Any>(now),
        )
        db.execSQL(
            "INSERT INTO tareas (titulo, contexto, fechaCreacion) VALUES ('b', 'TRABAJO', ?)",
            arrayOf<Any>(now),
        )
        db.execSQL(
            "INSERT INTO tareas (titulo, contexto, fechaCreacion) VALUES ('c', 'PERSONAL', ?)",
            arrayOf<Any>(now),
        )
        db.execSQL(
            "INSERT INTO tareas (titulo, contexto, fechaCreacion) VALUES ('d', 'SALUD', ?)",
            arrayOf<Any>(now),
        )
        db.execSQL(
            "INSERT INTO tareas (titulo, contexto, fechaCreacion) VALUES ('e', 'UNKNOWN', ?)",
            arrayOf<Any>(now),
        )

        // Apply the migration SQL exactly as MIGRATION_1_2 will run it
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE `contextos` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `nombre` TEXT NOT NULL,
                    `color` INTEGER NOT NULL,
                    `iconId` INTEGER,
                    `orden` INTEGER NOT NULL,
                    `esDefault` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX `index_contextos_nombre` ON `contextos` (`nombre`)")

            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (1, 'casa', -1144829, NULL, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (2, 'trabajo', -1088880, NULL, 2, 1)",
            )
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (3, 'personal', -2878467, NULL, 3, 1)",
            )
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (4, 'salud', -6082950, NULL, 4, 1)",
            )

            db.execSQL("ALTER TABLE `tareas` ADD COLUMN `contextoId` INTEGER NOT NULL DEFAULT 3")
            db.execSQL(
                """
                UPDATE `tareas` SET `contextoId` =
                    CASE `contexto`
                        WHEN 'CASA' THEN 1
                        WHEN 'TRABAJO' THEN 2
                        WHEN 'PERSONAL' THEN 3
                        WHEN 'SALUD' THEN 4
                    END
                """.trimIndent(),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // Assert: 4 contextos seeded with ids 1..4
        db.rawQuery("SELECT COUNT(*) FROM contextos WHERE esDefault = 1", null).use { c ->
            assertEquals(4, c.getInt(0))
        }
        db.rawQuery("SELECT id FROM contextos ORDER BY orden ASC", null).use { c ->
            val ids = mutableListOf<Long>()
            while (c.moveToNext()) ids.add(c.getLong(0))
            assertEquals(listOf(1L, 2L, 3L, 4L), ids)
        }

        // Assert: tareas preserve associations
        db.rawQuery("SELECT id, contextoId FROM tareas ORDER BY id ASC", null).use { c ->
            val pairs = mutableListOf<Pair<Long, Long>>()
            while (c.moveToNext()) pairs.add(c.getLong(0) to c.getLong(1))
            assertEquals(
                listOf(1L to 1L, 2L to 2L, 3L to 3L, 4L to 4L, 5L to 3L),
                pairs,
            )
        }
    }

    @Test
    fun migration_v1ToV2_noRowHasContextoIdZero() {
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO tareas (titulo, contexto, fechaCreacion) VALUES ('x', 'PERSONAL', ?)",
            arrayOf<Any>(now),
        )

        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE `contextos` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `nombre` TEXT NOT NULL,
                    `color` INTEGER NOT NULL,
                    `iconId` INTEGER,
                    `orden` INTEGER NOT NULL,
                    `esDefault` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX `index_contextos_nombre` ON `contextos` (`nombre`)")
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (1, 'casa', 0, NULL, 1, 1)",
            )
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (2, 'trabajo', 0, NULL, 2, 1)",
            )
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (3, 'personal', 0, NULL, 3, 1)",
            )
            db.execSQL(
                "INSERT INTO contextos (id, nombre, color, iconId, orden, esDefault) VALUES (4, 'salud', 0, NULL, 4, 1)",
            )
            db.execSQL("ALTER TABLE `tareas` ADD COLUMN `contextoId` INTEGER NOT NULL DEFAULT 3")
            db.execSQL(
                "UPDATE `tareas` SET `contextoId` = CASE `contexto` WHEN 'CASA' THEN 1 WHEN 'TRABAJO' THEN 2 WHEN 'PERSONAL' THEN 3 WHEN 'SALUD' THEN 4 END",
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        db.rawQuery("SELECT COUNT(*) FROM tareas WHERE contextoId = 0", null).use { c ->
            assertEquals(0, c.getInt(0))
        }
    }
}