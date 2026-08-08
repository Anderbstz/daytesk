package com.nuitcode.daytesk.model

data class Tarea(
    val id: Long,
    val titulo: String,
    val descripcion: String = "",
    val prioridad: Prioridad = Prioridad.MEDIA,
    /**
     * FK into the `contextos` table. Stable id 3 = PERSONAL default.
     */
    val contextoId: Long = 3L,
    /**
     * Resolved `Contexto` for this `Tarea`. Populated by
     * `DefaultDataRepository.data` from the 3-way combine (tareas + inbox +
     * contextos). Defaults to [Contexto.DEFAULTS] (PERSONAL) so freshly
     * built `Tarea` instances carry a sensible value before the repository
     * resolves the FK.
     */
    val contexto: Contexto = Contexto.DEFAULTS[2],
    val estado: TareaEstado = TareaEstado.PENDIENTE,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaVencimiento: Long? = null,
    val orden: Int = 0,
) {
    companion object {
        const val TITULO_MAX_LENGTH = 150
        const val DESCRIPCION_MAX_LENGTH = 500
    }
}
