package com.nuitcode.daytesk.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nuitcode.daytesk.model.Contexto

/**
 * Room-backed entity for user-managed contexts.
 *
 * Replaces the v1 `String` column on `TareaEntity` (the 4-value enum is gone
 * after PR1; see [Migrations.MIGRATION_1_2]).
 *
 * Stable ids 1..4 are reserved for the four defaults seeded by the migration
 * and the first-install [AppDatabase.populateDatabase] callback. The
 * [nombre] column carries a UNIQUE index so duplicate names surface as a
 * [android.database.sqlite.SQLiteConstraintException] — caught by
 * [com.nuitcode.daytesk.data.DefaultContextoRepository.add] and surfaced to
 * the UI as a friendly [com.nuitcode.daytesk.data.DuplicateContextoNameException].
 */
@Entity(
    tableName = "contextos",
    indices = [Index(value = ["nombre"], unique = true)],
)
data class ContextoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val color: Int,
    val iconId: Int? = null,
    val orden: Int,
    val esDefault: Boolean,
    val cloudKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Maps a [ContextoEntity] to its domain [Contexto].
 * `esDefault` is preserved via the companion `DEFAULTS` lookup; the domain
 * model itself does not carry the flag (REQ-05 / design decision).
 */
fun ContextoEntity.toDomain(): Contexto = Contexto(
    id = id,
    nombre = nombre,
    color = color,
    iconId = iconId,
    cloudKey = cloudKey.ifBlank {
        when {
            id in 1L..4L -> "default-$id"
            id != 0L -> "local-contexto-$id"
            else -> java.util.UUID.randomUUID().toString()
        }
    },
    updatedAt = updatedAt,
)

/**
 * Inverse mapping for repository writes. The repository decides whether to
 * preserve `esDefault` (defaults are immutable; custom rows are always
 * `esDefault = false` on insert/update).
 */
fun Contexto.toEntity(orden: Int, esDefault: Boolean): ContextoEntity = ContextoEntity(
    id = id,
    nombre = nombre,
    color = color,
    iconId = iconId,
    orden = orden,
    esDefault = esDefault,
    cloudKey = cloudKey.ifBlank {
        when {
            id in 1L..4L -> "default-$id"
            id != 0L -> "local-contexto-$id"
            else -> java.util.UUID.randomUUID().toString()
        }
    },
    updatedAt = updatedAt,
)