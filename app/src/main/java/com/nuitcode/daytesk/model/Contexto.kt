package com.nuitcode.daytesk.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * User-managed context. Was a 4-value enum (PR1 of `custom-contexts` replaces
 * it with a Room-backed entity). Backed by [com.nuitcode.daytesk.data.local.ContextoEntity]
 * and exposed to UI consumers through `ContextoRepository.contextos: Flow<List<Contexto>>`.
 *
 * `id` is the FK referenced by [Tarea.contextoId]. Stable ids 1..4 are reserved
 * for the four defaults seeded by the migration (CASA, TRABAJO, PERSONAL, SALUD).
 *
 * `color` is an ARGB Int (the 32-bit representation of a Compose `Color`). It
 * is converted lazily via [color] / [colorLight].
 *
 * `iconId` is currently unused by the UI (kept nullable for future
 * per-context icon support without another migration).
 */
data class Contexto(
    val id: Long,
    val nombre: String,
    val color: Int,
    val iconId: Int? = null,
) {
    fun color(): Color = Color(color)
    fun colorLight(): Color = color().copy(alpha = 0.15f)

    @Composable
    fun label(): String = "@$nombre"

    /**
     * A `Contexto` is considered a "default" (one of the 4 seeds) when its
     * `id` matches one of [DEFAULTS]. Defaults are immutable in the UI — name
     * editing is allowed (REQ-04), but color/icon are not, and deletion is
     * blocked at DAO + Repository + UI layers (REQ-05).
     */
    fun isDefault(): Boolean = DEFAULTS.any { it.id == id }

    companion object {
        /**
         * The four seed contexts. Their ids (1..4) are referenced by the v1->v2
         * migration `CASE-WHEN` and by `TareaEntity.contextoId` default.
         *
         * Color values are the ARGB Int equivalents of the Light palette
         * entries in `theme/Color.kt` so what the UI shows after migration is
         * identical to what the old enum showed.
         */
        val DEFAULTS: List<Contexto> = listOf(
            Contexto(id = 1, nombre = "casa", color = 0xFFFBC4AB.toInt()),
            Contexto(id = 2, nombre = "trabajo", color = 0xFF7DD6F0.toInt()),
            Contexto(id = 3, nombre = "personal", color = 0xFFD4B8FD.toInt()),
            Contexto(id = 4, nombre = "salud", color = 0xFFB2E87A.toInt()),
        )

        /**
         * 8-color palette surfaced by the Add/Edit context modal. Single-select
         * (the modal picks exactly one).
         */
        val PALETTE: List<Int> = listOf(
            0xFFEF5350.toInt(),
            0xFFAB47BC.toInt(),
            0xFF5C6BC0.toInt(),
            0xFF29B6F6.toInt(),
            0xFF26A69A.toInt(),
            0xFF9CCC65.toInt(),
            0xFFFFCA28.toInt(),
            0xFF8D6E63.toInt(),
        )
    }
}