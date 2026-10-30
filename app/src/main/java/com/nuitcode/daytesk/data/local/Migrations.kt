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
                        ELSE 3
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

    /**
     * Canonical DDL for the `recordatorios` table.
     *
     * Exposed as a literal so [MIGRATION_4_5] and `MigrationTest` execute the
     * exact same statement — the test can never drift from production DDL.
     *
     * Column order mirrors [RecordatorioEntity]'s declaration order so the
     * generated Room schema matches this statement byte-for-byte.
     */
    const val CREATE_RECORDATORIOS_DDL: String =
        "CREATE TABLE IF NOT EXISTS `recordatorios` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`texto` TEXT NOT NULL, " +
            "`fecha` INTEGER NOT NULL, " +
            "`repeticion` TEXT NOT NULL, " +
            "`cloudKey` TEXT NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, " +
            "`fechaCreacion` INTEGER NOT NULL)"

    /**
     * **MIGRATION_4_5** — introduces the `recordatorios` table.
     *
     * The `inbox_items` drop deliberately did NOT ship here: `InboxItemEntity`
     * was still part of [AppDatabase.entities] at v5, so dropping its table in
     * this migration would have failed Room's on-open schema validation
     * (`IllegalStateException`) on an upgraded v4 database. The drop moved to
     * [MIGRATION_5_6] once the Inbox entity/DAO were removed from the database.
     * This migration is historical and MUST NOT be edited.
     */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(CREATE_RECORDATORIOS_DDL)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * Canonical statement that drops the retired `inbox_items` table.
     *
     * Exposed as a literal so [MIGRATION_5_6] and `MigrationTest` execute the
     * exact same statement — the test can never drift from production DDL.
     */
    const val DROP_INBOX_ITEMS_DDL: String = "DROP TABLE IF EXISTS `inbox_items`"

    /**
     * **MIGRATION_5_6** — removes the `inbox_items` table after the Inbox
     * entity and DAO were deleted from [AppDatabase].
     *
     * Existing inbox rows are intentionally discarded, never migrated: the
     * spec records "inbox (REMOVED) / Migration: None". `IF EXISTS` keeps the
     * migration safe on databases where the table was already absent. Runs in
     * a transaction like every other migration.
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(DROP_INBOX_ITEMS_DDL)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * Canonical statement that adds the opt-in alarm flag.
     *
     * Exposed as a literal so [MIGRATION_6_7] and `MigrationTest` execute the
     * exact same statement — the test can never drift from production DDL.
     *
     * `NOT NULL DEFAULT 0` is what makes the upgrade safe: every existing
     * recordatorio becomes Stage 1 (`false`) without a table rebuild.
     */
    const val ALTER_RECORDATORIOS_ADD_ES_ALARMA_DDL: String =
        "ALTER TABLE `recordatorios` ADD COLUMN `esAlarma` INTEGER NOT NULL DEFAULT 0"

    /**
     * **MIGRATION_6_7** — adds `recordatorios.esAlarma` (vibration-only alarm
     * mode). Runs in a transaction like every other migration.
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL(ALTER_RECORDATORIOS_ADD_ES_ALARMA_DDL)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    /**
     * **MIGRATION_7_8** — adds the LimeKatze mirror columns. Local rows keep
     * `origin='DAYTESK'` and a null `remoteKey`; mirrored ones carry the remote
     * handle and a local mute flag.
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL("ALTER TABLE `recordatorios` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'DAYTESK'")
                db.execSQL("ALTER TABLE `recordatorios` ADD COLUMN `remoteKey` TEXT")
                db.execSQL("ALTER TABLE `recordatorios` ADD COLUMN `silenciado` INTEGER NOT NULL DEFAULT 0")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
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