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
)

fun InboxItemEntity.toDomain(): InboxItem = InboxItem(
    id = id,
    texto = texto,
    timestamp = timestamp,
    procesado = procesado,
)

fun InboxItem.toEntity(): InboxItemEntity = InboxItemEntity(
    id = id,
    texto = texto,
    timestamp = timestamp,
    procesado = procesado,
)
