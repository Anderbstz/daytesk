package com.nuitcode.daytesk.model

import androidx.compose.runtime.Composable

enum class TareaEstado {
    PENDIENTE,
    COMPLETADA,
    VENCIDA;

    @Composable
    fun label(): String = when (this) {
        PENDIENTE -> "Pendiente"
        COMPLETADA -> "Completada"
        VENCIDA -> "Vencida"
    }
}
