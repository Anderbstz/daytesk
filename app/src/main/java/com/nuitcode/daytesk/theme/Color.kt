package com.nuitcode.daytesk.theme

import androidx.compose.ui.graphics.Color

// ── Light palette ──────────────────────────────────────────────
object DayteskColors {
    val Background = Color(0xFFF7F8FC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5E7EB)
    val Divider = Color(0xFFF3F4F6)

    // Accent / Primary
    val Primary = Color(0xFF7C8CF5)
    val PrimaryLight = Color(0xFFEEF0FF)

    // Semantic
    val Success = Color(0xFF6BCB77)
    val SuccessLight = Color(0xFFE8F5E9)
    val Warning = Color(0xFFFBBF24)
    val WarningLight = Color(0xFFFFFBEB)
    val Urgent = Color(0xFFF87171)
    val UrgentLight = Color(0xFFFFF3F3)

    // Context
    val ContextCasa = Color(0xFFFBC4AB)
    val ContextTrabajo = Color(0xFF7DD6F0)
    val ContextPersonal = Color(0xFFD4B8FD)
    val ContextSalud = Color(0xFFB2E87A)

    // Context text
    val ContextCasaText = Color(0xFF7C2D12)
    val ContextTrabajoText = Color(0xFF1E3A8A)
    val ContextPersonalText = Color(0xFF5B21B6)
    val ContextSaludText = Color(0xFF1B5E20)

    // Text
    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF6B7280)
    val TextDisabled = Color(0xFF9CA3AF)
}

// ── Dark palette ───────────────────────────────────────────────
object DayteskDarkColors {
    val Background = Color(0xFF12121A)
    val Surface = Color(0xFF1E1E2A)
    val Border = Color(0xFF2D2D3F)
    val Divider = Color(0xFF2A2A3A)

    val Primary = Color(0xFF9AA6FF)
    val PrimaryLight = Color(0xFF2D2F4D)

    val Success = Color(0xFF6BCB77)
    val SuccessLight = Color(0xFF1E3322)
    val Warning = Color(0xFFFBBF24)
    val WarningLight = Color(0xFF332D12)
    val Urgent = Color(0xFFF87171)
    val UrgentLight = Color(0xFF3D1A1A)

    val ContextCasa = Color(0xFFFBC4AB).copy(alpha = 0.7f)
    val ContextTrabajo = Color(0xFF7DD6F0).copy(alpha = 0.7f)
    val ContextPersonal = Color(0xFFD4B8FD).copy(alpha = 0.7f)
    val ContextSalud = Color(0xFFB2E87A).copy(alpha = 0.7f)

    val ContextCasaText = Color(0xFFFBC4AB)
    val ContextTrabajoText = Color(0xFF7DD6F0)
    val ContextPersonalText = Color(0xFFD4B8FD)
    val ContextSaludText = Color(0xFFB2E87A)

    val TextPrimary = Color(0xFFF0F0F4)
    val TextSecondary = Color(0xFF9CA3AF)
    val TextDisabled = Color(0xFF6B7280)
}
