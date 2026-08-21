package com.nuitcode.daytesk.model

data class InboxItem(
    val id: Long,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis(),
    val procesado: Boolean = false,
    val cloudKey: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
)
