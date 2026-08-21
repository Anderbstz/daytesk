package com.nuitcode.daytesk.model

data class Tarea(
    val id: Long,
    val titulo: String,
    val descripcion: String = "",
    val prioridad: Prioridad = Prioridad.MEDIA,
    /**
     * FK into the `contextos` table. Defaults to [Contexto.FALLBACK_ID].
     */
    val contextoId: Long = Contexto.FALLBACK_ID,
    /**
     * Resolved `Contexto` for this `Tarea`. Populated by
     * `DefaultDataRepository.data` from the 3-way combine (tareas + inbox +
     * contextos). Defaults to [Contexto.DEFAULTS] (PERSONAL) so freshly
     * built `Tarea` instances carry a sensible value before the repository
     * resolves the FK.
     */
    val contexto: Contexto = Contexto.FALLBACK,
    val estado: TareaEstado = TareaEstado.PENDIENTE,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaVencimiento: Long? = null,
    val fechaCompletada: Long? = null,
    val orden: Int = 0,
    val repeticion: Repeticion = Repeticion.NINGUNA,
    val cloudKey: String = java.util.UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TITULO_MAX_LENGTH = 150
        const val DESCRIPCION_MAX_LENGTH = 500
    }
}
