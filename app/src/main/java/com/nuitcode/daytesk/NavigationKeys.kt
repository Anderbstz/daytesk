package com.nuitcode.daytesk

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Inicio : NavKey
@Serializable data object Inbox : NavKey
@Serializable data object Tareas : NavKey
@Serializable data object Utilidades : NavKey
@Serializable data object Perfil : NavKey
@Serializable data object ImageOcr : NavKey
@Serializable data object AudioTranscribe : NavKey
@Serializable data object VideoTranscribe : NavKey
