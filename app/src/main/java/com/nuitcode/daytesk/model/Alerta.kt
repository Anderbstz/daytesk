package com.nuitcode.daytesk.model

data class Alerta(
    val id: Long,
    val mensaje: String,
    val tipo: AlertaTipo = AlertaTipo.RECORDATORIO,
    val leida: Boolean = false,
)
