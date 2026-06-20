package com.nuitcode.daytesk.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nuitcode.daytesk.theme.DayteskColors

enum class Contexto {
    CASA,
    TRABAJO,
    PERSONAL,
    SALUD;

    fun color(): Color = when (this) {
        CASA -> DayteskColors.ContextCasa
        TRABAJO -> DayteskColors.ContextTrabajo
        PERSONAL -> DayteskColors.ContextPersonal
        SALUD -> DayteskColors.ContextSalud
    }

    fun colorLight(): Color = color().copy(alpha = 0.15f)

    @Composable
    fun label(): String = when (this) {
        CASA -> "@casa"
        TRABAJO -> "@trabajo"
        PERSONAL -> "@personal"
        SALUD -> "@salud"
    }
}
