package com.nuitcode.daytesk.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nuitcode.daytesk.theme.DayteskColors

enum class Prioridad {
    BAJA,
    MEDIA,
    ALTA;

    fun color(): Color = when (this) {
        BAJA -> DayteskColors.Success
        MEDIA -> DayteskColors.Warning
        ALTA -> DayteskColors.Urgent
    }

    fun colorLight(): Color = when (this) {
        BAJA -> DayteskColors.SuccessLight
        MEDIA -> DayteskColors.WarningLight
        ALTA -> DayteskColors.UrgentLight
    }

    @Composable
    fun label(): String = when (this) {
        BAJA -> "Baja"
        MEDIA -> "Media"
        ALTA -> "Alta"
    }
}
