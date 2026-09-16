package com.nuitcode.daytesk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Repeticion

/**
 * Room entity for the `recordatorios` table.
 *
 * `repeticion` is stored by enum name (like [TareaEntity]); `fecha` is a
 * NOT NULL epoch-millis column because the domain model makes the date
 * mandatory. `cloudKey` backfills to `local-recordatorio-<id>` for rows that
 * predate sync so a pulled payload can still be matched.
 */
@Entity(tableName = "recordatorios")
data class RecordatorioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val texto: String,
    val fecha: Long,
    val repeticion: String = "NINGUNA",
    val cloudKey: String = "",
    val updatedAt: Long,
    val fechaCreacion: Long,
)

/**
 * Entity -> domain. Unknown/legacy repetition names fall back to
 * [Repeticion.NINGUNA] instead of throwing.
 */
fun RecordatorioEntity.toDomain(): Recordatorio = Recordatorio(
    id = id,
    texto = texto,
    fecha = fecha,
    repeticion = runCatching { Repeticion.valueOf(repeticion) }.getOrDefault(Repeticion.NINGUNA),
    cloudKey = cloudKey.ifBlank { "local-recordatorio-$id" },
    updatedAt = updatedAt,
    fechaCreacion = fechaCreacion,
)

/**
 * Domain -> entity. Mirrors [TareaEntity.toEntity]: a blank cloudKey becomes
 * `local-recordatorio-<id>` for persisted rows, or a fresh UUID for new ones.
 */
fun Recordatorio.toEntity(): RecordatorioEntity = RecordatorioEntity(
    id = id,
    texto = texto,
    fecha = fecha,
    repeticion = repeticion.name,
    cloudKey = cloudKey.ifBlank {
        if (id != 0L) "local-recordatorio-$id" else java.util.UUID.randomUUID().toString()
    },
    updatedAt = updatedAt,
    fechaCreacion = fechaCreacion,
)
