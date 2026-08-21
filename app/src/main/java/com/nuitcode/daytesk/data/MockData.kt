package com.nuitcode.daytesk.data

import com.nuitcode.daytesk.model.Alerta
import com.nuitcode.daytesk.model.AlertaTipo
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.model.InboxItem
import com.nuitcode.daytesk.model.Prioridad
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado

object MockData {
    private val now = System.currentTimeMillis()
    private val dayMs = 86_400_000L

    // Stable ids mirror Contexto.DEFAULTS — used by TareaEntity.contextoId (FK).
    // 1=casa, 2=trabajo, 3=personal, 4=salud.

    // ── 6 Tareas ──────────────────────────────────────────────
    private val tarea1 = Tarea(
        id = 1,
        titulo = "Llamar al dentista",
        descripcion = "Confirmar cita de las 10am",
        prioridad = Prioridad.ALTA,
        contextoId = 4L, // salud
        contexto = Contexto.DEFAULTS[3],
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = now + dayMs,
        orden = 0,
    )

    private val tarea2 = Tarea(
        id = 2,
        titulo = "Informe trimestral",
        descripcion = "Completar informe para reunión de las 5pm",
        prioridad = Prioridad.ALTA,
        contextoId = 2L, // trabajo
        contexto = Contexto.DEFAULTS[1],
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = now + dayMs / 2,
        orden = 1,
    )

    private val tarea3 = Tarea(
        id = 3,
        titulo = "Sacar la basura",
        descripcion = "",
        prioridad = Prioridad.BAJA,
        contextoId = 1L, // casa
        contexto = Contexto.DEFAULTS[0],
        estado = TareaEstado.COMPLETADA,
        fechaCompletada = now,
        orden = 2,
    )

    private val tarea4 = Tarea(
        id = 4,
        titulo = "Limpiar el garaje",
        descripcion = "Organizar cajas y barrer",
        prioridad = Prioridad.MEDIA,
        contextoId = 1L, // casa
        contexto = Contexto.DEFAULTS[0],
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = now + 3 * dayMs,
        orden = 3,
    )

    private val tarea5 = Tarea(
        id = 5,
        titulo = "Leer un libro",
        descripcion = "Terminar 'El poder del hábito'",
        prioridad = Prioridad.BAJA,
        contextoId = Contexto.FALLBACK_ID,
        contexto = Contexto.FALLBACK,
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = now + 7 * dayMs,
        orden = 4,
    )

    private val tarea6 = Tarea(
        id = 6,
        titulo = "Comprar regalo cumpleaños",
        descripcion = "Regalo para mamá",
        prioridad = Prioridad.MEDIA,
        contextoId = Contexto.FALLBACK_ID,
        contexto = Contexto.FALLBACK,
        estado = TareaEstado.PENDIENTE,
        fechaVencimiento = now + 5 * dayMs,
        orden = 5,
    )

    val tareas: List<Tarea> = listOf(tarea1, tarea2, tarea3, tarea4, tarea5, tarea6)

    // ── Grouped lists ─────────────────────────────────────────
    val tareasHoy: List<Tarea> = listOf(tarea1, tarea2, tarea3)
    val tareasSemana: List<Tarea> = listOf(tarea4, tarea5)
    val completadas: List<Tarea> = listOf(tarea3)

    // ── 5 Inbox Items ─────────────────────────────────────────
    val inboxItems: List<InboxItem> = listOf(
        InboxItem(1, "Revisar correo del banco", now - dayMs),
        InboxItem(2, "Buscar vuelos para vacaciones", now - 2 * dayMs),
        InboxItem(3, "Comprar nuevo monitor", now - 3 * dayMs),
        InboxItem(4, "Llamar al seguro médico", now - 4 * dayMs),
        InboxItem(5, "Actualizar CV", now - 5 * dayMs),
    )

    // ── 2 Alertas ─────────────────────────────────────────────
    val alertas: List<Alerta> = listOf(
        Alerta(1, "Llamar al dentista vence hoy", AlertaTipo.VENCIMIENTO),
        Alerta(2, "Recordatorio: Informe trimestral a las 5pm", AlertaTipo.RECORDATORIO),
    )

    // ── Stats ─────────────────────────────────────────────────
    val stats: DayteskStats = DayteskStats(
        tareasHoy = 3,
        inboxPendientes = 5,
        tareasCompletadas = 1,
        rachaActual = 7,
        totalCompletadasHistorico = 42,
        promedioEfectividad = 0.78f,
    )

    // ── Weekly review ─────────────────────────────────────────
    val weeklyReview: List<String> = listOf(
        "Revisar próximos 7 días",
        "Vaciar inbox",
        "Actualizar proyectos",
        "Revisar metas semanales",
        "Reflexión personal",
    )
}