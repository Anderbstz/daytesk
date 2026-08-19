package com.nuitcode.daytesk.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for the daytesk DB.
 *
 * **MIGRATION_1_2** — replace `TareaEntity.contexto: String` (4-value enum)
 * with `TareaEntity.contextoId: Long` (FK) plus a new `contextos` table.
 *
 * Runs as a single `transaction { ... }` so partial failure is impossible
 * (REQ-08 in the spec):
 *   1. CREATE TABLE `contextos` + UNIQUE index on `nombre`
 *   2. INSERT 4 seed rows (ids 1..4, esDefault = 1)
 *   3. ALTER TABLE `tareas` ADD COLUMN `contextoId INTEGER NOT NULL DEFAULT 3`
 *   4. UPDATE `tareas` SET contextoId = CASE `contexto` WHEN 'CASA' THEN 1 ... END
 *
 * The `DEFAULT 3` clause guarantees that any unknown legacy enum name (e.g.,
 * hand-edited DB or future-proofing) falls back to PERSONAL (id=3) instead
 * of producing a NULL FK.
 */
object Migrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `contextos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nombre` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `iconId` INTEGER,
                        `orden` INTEGER NOT NULL,
                        `esDefault` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_contextos_nombre` ON `contextos` (`nombre`)",
                )

                db.execSQL(
                    "INSERT OR IGNORE INTO `contextos` (id, nombre, color, iconId, orden, esDefault) VALUES (1, 'casa', ${ContextoSeedColor.CASA}, NULL, 1, 1)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `contextos` (id, nombre, color, iconId, orden, esDefault) VALUES (2, 'trabajo', ${ContextoSeedColor.TRABAJO}, NULL, 2, 1)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `contextos` (id, nombre, color, iconId, orden, esDefault) VALUES (3, 'personal', ${ContextoSeedColor.PERSONAL}, NULL, 3, 1)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `contextos` (id, nombre, color, iconId, orden, esDefault) VALUES (4, 'salud', ${ContextoSeedColor.SALUD}, NULL, 4, 1)",
                )

                db.execSQL(
                    "ALTER TABLE `tareas` ADD COLUMN `contextoId` INTEGER NOT NULL DEFAULT 3",
                )
                db.execSQL(
                    """
                    UPDATE `tareas`
                    SET `contextoId` = CASE `contexto`
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
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tareas` ADD COLUMN `fechaCompletada` INTEGER")
            db.execSQL(
                """
                UPDATE `tareas`
                SET `fechaCompletada` = `fechaCreacion`
                WHERE `estado` = 'COMPLETADA' AND `fechaCompletada` IS NULL
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `tareas` ADD COLUMN `repeticion` TEXT NOT NULL DEFAULT 'NINGUNA'")
            db.execSQL("ALTER TABLE `tareas` ADD COLUMN `cloudKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `tareas` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inbox_items` ADD COLUMN `cloudKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `inbox_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `contextos` ADD COLUMN `cloudKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `contextos` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        }
    }
}

/**
 * ARGB Int values for the four default seed contexts.
 * Match the Light palette in com.nuitcode.daytesk.theme.DayteskColors so the
 * UI looks identical after migration.
 *
 * `Color(0xFFFBC4AB).toArgb()` would be cleaner but Color.toArgb() lives in
 * androidx.compose.ui.graphics and we want this object to be Android-free.
 * These literal values are the equivalent 32-bit ARGB ints.
 */
internal object ContextoSeedColor {
    const val CASA: Long = 0xFFFBC4ABL          // Color(0xFFFBC4AB)
    const val TRABAJO: Long = 0xFF7DD6F0L       // Color(0xFF7DD6F0)
    const val PERSONAL: Long = 0xFFD4B8FDL      // Color(0xFFD4B8FD)
    const val SALUD: Long = 0xFFB2E87AL         // Color(0xFFB2E87A)
}