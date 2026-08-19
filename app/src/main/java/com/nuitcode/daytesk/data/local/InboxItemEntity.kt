package com.nuitcode.daytesk.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nuitcode.daytesk.model.InboxItem

@Entity(tableName = "inbox_items")
data class InboxItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis(),
    val procesado: Boolean = false,
    val cloudKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

fun InboxItemEntity.toDomain(): InboxItem = InboxItem(
    id = id,
    texto = texto,
    timestamp = timestamp,
    procesado = procesado,
    cloudKey = cloudKey.ifBlank { "local-inbox-$id" },
    updatedAt = updatedAt,
)

fun InboxItem.toEntity(): InboxItemEntity = InboxItemEntity(
    id = id,
    texto = texto,
    timestamp = timestamp,
    procesado = procesado,
    cloudKey = cloudKey.ifBlank {
        if (id != 0L) "local-inbox-$id" else java.util.UUID.randomUUID().toString()
    },
    updatedAt = updatedAt,
)
