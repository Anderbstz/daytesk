package com.nuitcode.daytesk.model

data class InboxItem(
    val id: Long,
    val texto: String,
    val timestamp: Long = System.currentTimeMillis(),
    val procesado: Boolean = false,
)
