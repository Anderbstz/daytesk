package com.nuitcode.daytesk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.DefaultContextoRepository
import com.nuitcode.daytesk.data.DefaultDataRepository
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.data.local.InboxItemDao
import com.nuitcode.daytesk.data.local.TareaDao
import com.nuitcode.daytesk.data.local.toEntity
import com.nuitcode.daytesk.model.InboxItem
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.ui.contextos.ContextEditModal
import com.nuitcode.daytesk.ui.contextos.ContextosScreen
import com.nuitcode.daytesk.ui.inbox.InboxScreen
import com.nuitcode.daytesk.ui.inicio.InicioScreen
import com.nuitcode.daytesk.ui.main.DayteskUiState
import com.nuitcode.daytesk.ui.main.MainScreenViewModel
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.ui.historial.HistorialScreen
import com.nuitcode.daytesk.ui.modals.DetalleTareaModal
import com.nuitcode.daytesk.ui.modals.NuevaTareaModal
import com.nuitcode.daytesk.ui.modals.ProcesarInboxModal
import com.nuitcode.daytesk.ui.modals.RevisionSemanalModal
import com.nuitcode.daytesk.ui.perfil.AyudaScreen
import com.nuitcode.daytesk.ui.perfil.ConfiguracionScreen
import com.nuitcode.daytesk.ui.perfil.PerfilScreen
import com.nuitcode.daytesk.ui.tareas.TareasScreen
import com.nuitcode.daytesk.ui.utilidades.UtilidadesScreen
import com.nuitcode.daytesk.utilities.ocr.ImageOcrScreen
import com.nuitcode.daytesk.utilities.audio.AudioTranscribeScreen
import com.nuitcode.daytesk.utilities.video.VideoTranscribeScreen
import kotlinx.coroutines.launch

private data class TabItem(
    val key: NavKey,
    val icon: ImageVector,
    val label: String,
)

private val tabs = listOf(
    TabItem(Inicio, Icons.Default.Home, "Inicio"),
    TabItem(Inbox, Icons.Default.Email, "Inbox"),
    TabItem(Tareas, Icons.Default.CheckCircle, "Tareas"),
    TabItem(Utilidades, Icons.Default.Build, "Utilidades"),
    TabItem(Perfil, Icons.Default.Person, "Perfil"),
)

// ── Production entry point ────────────────────────────────────

@Composable
fun DayteskApp(
    database: AppDatabase,
    contextoRepository: ContextoRepository =
        DefaultContextoRepository(database.contextoDao()),
) {
    val tareaDao = database.tareaDao()
    val inboxItemDao = database.inboxItemDao()
    val contextoDao = database.contextoDao()
    val repository = remember { DefaultDataRepository(tareaDao, inboxItemDao, contextoDao) }
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(repository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showNuevaTarea by remember { mutableStateOf(false) }
    var showRevisionSemanal by remember { mutableStateOf(false) }
    var detalleTareaSeleccionada by remember { mutableStateOf<Tarea?>(null) }
    var procesarInboxSeleccionado by remember { mutableStateOf<InboxItem?>(null) }
    var showAddContexto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Hoisted so modals outside the Success branch can still read `data.contextos`.
    var cachedData by remember { mutableStateOf<DayteskData?>(null) }

    when (val state = uiState) {
        is DayteskUiState.Loading -> {
            // Brief loading
        }
        is DayteskUiState.Success -> {
            cachedData = state.data
            LaunchedEffect(state.data) {
                ReminderScheduler.reschedulePending(
                    context,
                    state.data.tareasHoy + state.data.tareasSemana +
                        state.data.otrasPendientes + state.data.vencidas,
                )
            }
            DayteskNavScaffold(
                data = state.data,
                contextoRepository = contextoRepository,
                tareaDao = tareaDao,
                inboxItemDao = inboxItemDao,
                onShowNuevaTarea = { showNuevaTarea = true },
                onShowDetalleTarea = { tarea -> detalleTareaSeleccionada = tarea },
                onShowProcesarInbox = { item -> procesarInboxSeleccionado = item },
                onShowRevisionSemanal = { showRevisionSemanal = true },
            )
        }
        is DayteskUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Error: ${state.throwable.message}",
                    style = DayteskTypography.bodyMd,
                    color = DayteskColors.Urgent,
                )
            }
        }
    }

    // ── Modal: Nueva Tarea ─────────────────────────────────────
    cachedData?.let { currentData ->
        if (showNuevaTarea) {
            NuevaTareaModal(
                contextos = currentData.contextos,
                canAddContexto = currentData.contextos.size < Contexto.MAX_COUNT,
                onAddContexto = { showAddContexto = true },
                onDismiss = { showNuevaTarea = false },
                onSave = { tarea ->
                    scope.launch {
                        val id = tareaDao.insertTarea(tarea.toEntity())
                        ReminderScheduler.scheduleIfDue(context, id, tarea.titulo, tarea.fechaVencimiento)
                        showNuevaTarea = false
                    }
                },
            )
        }
    }

    // ── Modal: Detalle Tarea ──────────────────────────────────
    detalleTareaSeleccionada?.let { tarea ->
        DetalleTareaModal(
            tarea = tarea,
            onDismiss = { detalleTareaSeleccionada = null },
            onComplete = {
                scope.launch {
                    tareaDao.updateTarea(tarea.withCompletion(true).toEntity())
                    ReminderScheduler.cancelTaskReminder(context, tarea.id)
                    detalleTareaSeleccionada = null
                }
            },
            onDelete = {
                scope.launch {
                    ReminderScheduler.cancelTaskReminder(context, tarea.id)
                    tareaDao.deleteTarea(tarea.toEntity())
                    detalleTareaSeleccionada = null
                }
            },
        )
    }

    // ── Modal: Procesar Inbox ──────────────────────────────────
    cachedData?.let { currentData ->
        procesarInboxSeleccionado?.let { item ->
            ProcesarInboxModal(
                item = item,
                contextos = currentData.contextos,
                canAddContexto = currentData.contextos.size < Contexto.MAX_COUNT,
                onAddContexto = { showAddContexto = true },
                onDismiss = { procesarInboxSeleccionado = null },
                onSave = { contexto, prioridad, fecha ->
                    scope.launch {
                        val nuevaTarea = Tarea(
                            id = 0,
                            titulo = item.texto,
                            descripcion = "",
                            contextoId = contexto.id,
                            contexto = contexto,
                            prioridad = prioridad,
                            estado = TareaEstado.PENDIENTE,
                            fechaVencimiento = fecha,
                        )
                        val id = tareaDao.insertTarea(nuevaTarea.toEntity())
                        ReminderScheduler.scheduleIfDue(context, id, nuevaTarea.titulo, fecha)
                        inboxItemDao.deleteItem(item.toEntity())
                        procesarInboxSeleccionado = null
                    }
                },
                onDelete = {
                    scope.launch {
                        inboxItemDao.deleteItem(item.toEntity())
                        procesarInboxSeleccionado = null
                    }
                },
            )
        }
    }

    cachedData?.let { currentData ->
        if (showRevisionSemanal) {
            RevisionSemanalModal(
                inboxPendientes = currentData.stats.inboxPendientes,
                tareasVencidas = currentData.vencidas.size,
                tareasCompletadas = currentData.stats.tareasCompletadas,
                onDismiss = { showRevisionSemanal = false },
                onCompletar = { showRevisionSemanal = false },
                onProgramar = {
                    ReminderScheduler.scheduleWeeklyReview(context)
                    showRevisionSemanal = false
                },
            )
        }
    }

    if (showAddContexto) {
        ContextEditModal(
            initial = null,
            onDismiss = { showAddContexto = false },
            onSave = { nombre, color ->
                scope.launch {
                    contextoRepository.add(
                        Contexto(id = 0, nombre = nombre, color = color),
                    )
                    showAddContexto = false
                }
            },
        )
    }
}

// ── Test entry point (read-only, no CRUD) ─────────────────────

@Composable
fun DayteskApp(
    dataRepository: DataRepository,
    contextoRepository: ContextoRepository? = null,
) {
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(dataRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showNuevaTarea by remember { mutableStateOf(false) }
    var cachedData by remember { mutableStateOf<DayteskData?>(null) }

    when (val state = uiState) {
        is DayteskUiState.Loading -> {}
        is DayteskUiState.Success -> {
            cachedData = state.data
            DayteskNavScaffold(
                data = state.data,
                contextoRepository = contextoRepository,
                tareaDao = null,
                inboxItemDao = null,
                onShowNuevaTarea = { showNuevaTarea = true },
                onShowDetalleTarea = {},
                onShowProcesarInbox = {},
                onShowRevisionSemanal = {},
            )
        }
        is DayteskUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Error: ${state.throwable.message}",
                    style = DayteskTypography.bodyMd,
                    color = DayteskColors.Urgent,
                )
            }
        }
    }

    cachedData?.let { currentData ->
        if (showNuevaTarea) {
            NuevaTareaModal(
                contextos = currentData.contextos,
                onDismiss = { showNuevaTarea = false },
                onSave = { showNuevaTarea = false },
            )
        }
    }
}

// ── Navigation scaffold ───────────────────────────────────────

@Composable
private fun DayteskNavScaffold(
    data: DayteskData,
    contextoRepository: ContextoRepository?,
    tareaDao: TareaDao?,
    inboxItemDao: InboxItemDao?,
    onShowNuevaTarea: () -> Unit,
    onShowDetalleTarea: (Tarea) -> Unit,
    onShowProcesarInbox: (InboxItem) -> Unit,
    onShowRevisionSemanal: () -> Unit,
) {
    val backStack = rememberNavBackStack(Inicio)
    val currentEntry = backStack.lastOrNull()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pluginStore = remember { com.nuitcode.daytesk.data.HomePluginStore(context) }
    var homePlugins by remember { mutableStateOf(pluginStore.load()) }

    Scaffold(
        modifier = Modifier,
        bottomBar = {
            DayteskBottomBar(
                currentEntry = currentEntry,
                onTabSelected = { key ->
                    backStack.clear()
                    backStack.add(key)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onShowNuevaTarea,
                containerColor = DayteskColors.Primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar tarea",
                )
            }
        },
        containerColor = DayteskColors.Background,
    ) { paddingValues ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            entryProvider = entryProvider {
                entry<Inicio> {
                    InicioScreen(
                        data = data,
                        plugins = homePlugins,
                        onTaskClick = { taskId ->
                            findTaskById(data, taskId)?.let { onShowDetalleTarea(it) }
                        },
                        onTaskToggle = { taskId, completed ->
                            if (tareaDao != null) {
                                scope.launch {
                                    val task = findTaskById(data, taskId) ?: return@launch
                                    val updated = task.withCompletion(completed)
                                    tareaDao.updateTarea(updated.toEntity())
                                    if (completed) {
                                        ReminderScheduler.cancelTaskReminder(context, task.id)
                                    } else {
                                        ReminderScheduler.scheduleIfDue(
                                            context,
                                            task.id,
                                            task.titulo,
                                            task.fechaVencimiento,
                                        )
                                    }
                                }
                            }
                        },
                        onSeeAll = {
                            backStack.clear()
                            backStack.add(Tareas)
                        },
                        onOpenWeeklyReview = onShowRevisionSemanal,
                        onToggleRecientes = { homePlugins = pluginStore.toggleRecientes() },
                        onPinTask = { id -> homePlugins = pluginStore.pin(id) },
                        onUnpinTask = { id -> homePlugins = pluginStore.unpin(id) },
                    )
                }
                entry<Inbox> {
                    InboxScreen(
                        data = data,
                        onItemClick = { itemId ->
                            data.inbox.find { it.id == itemId }?.let { onShowProcesarInbox(it) }
                        },
                        onProcessAll = {
                            if (tareaDao != null && inboxItemDao != null) {
                                scope.launch {
                                    val contexto = data.contextos.firstOrNull() ?: return@launch
                                    data.inbox.forEach { item ->
                                        val nueva = Tarea(
                                            id = 0,
                                            titulo = item.texto,
                                            contextoId = contexto.id,
                                            contexto = contexto,
                                        )
                                        tareaDao.insertTarea(nueva.toEntity())
                                        inboxItemDao.deleteItem(item.toEntity())
                                    }
                                }
                            }
                        },
                        onAddItem = { texto ->
                            if (inboxItemDao != null) {
                                scope.launch {
                                    inboxItemDao.insertItem(
                                        InboxItem(id = 0, texto = texto).toEntity(),
                                    )
                                }
                            }
                        },
                    )
                }
                entry<Tareas> {
                    TareasScreen(
                        data = data,
                        onTaskClick = { taskId ->
                            findTaskById(data, taskId)?.let { onShowDetalleTarea(it) }
                        },
                        onTaskToggle = { taskId, completed ->
                            if (tareaDao != null) {
                                scope.launch {
                                    val task = findTaskById(data, taskId) ?: return@launch
                                    val updated = task.withCompletion(completed)
                                    tareaDao.updateTarea(updated.toEntity())
                                    if (completed) {
                                        ReminderScheduler.cancelTaskReminder(context, task.id)
                                    } else {
                                        ReminderScheduler.scheduleIfDue(
                                            context,
                                            task.id,
                                            task.titulo,
                                            task.fechaVencimiento,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
entry<Utilidades> {
                    UtilidadesScreen(
                        data = data,
                        onNavigate = { route -> backStack.add(route) },
                    )
                }
                entry<ImageOcr> {
                    ImageOcrScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<AudioTranscribe> {
                    AudioTranscribeScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<VideoTranscribe> {
                    VideoTranscribeScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<Perfil> {
                    PerfilScreen(
                        data = data,
                        onNavigateContextos = {
                            if (contextoRepository != null) {
                                backStack.add(Contextos)
                            }
                        },
                        onNavigateConfiguracion = { backStack.add(Configuracion) },
                        onNavigateAyuda = { backStack.add(Ayuda) },
                        onNavigateHistorial = { backStack.add(Historial) },
                        onClearLocalData = {
                            if (tareaDao != null && inboxItemDao != null) {
                                scope.launch {
                                    tareaDao.deleteAll()
                                    inboxItemDao.deleteAll()
                                }
                            }
                        },
                    )
                }
                entry<Historial> {
                    HistorialScreen(
                        data = data,
                        onBack = { backStack.removeLastOrNull() },
                        onTaskClick = { taskId ->
                            findTaskById(data, taskId)?.let { onShowDetalleTarea(it) }
                        },
                    )
                }
                entry<Configuracion> {
                    ConfiguracionScreen(
                        onBack = { backStack.removeLastOrNull() },
                        onOpenWeeklyReview = onShowRevisionSemanal,
                    )
                }
                entry<Ayuda> {
                    AyudaScreen(onBack = { backStack.removeLastOrNull() })
                }
                entry<Contextos> {
                    if (contextoRepository != null) {
                        ContextosScreen(
                            contextoRepository = contextoRepository,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    } else {
                        // Defensive: production entry always supplies a repo.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Contextos no disponibles",
                                style = DayteskTypography.bodyMd,
                                color = DayteskColors.Urgent,
                            )
                        }
                    }
                }
            },
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────

@Composable
private fun DeferredUtilityScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title estará disponible en la próxima entrega.",
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextSecondary,
        )
    }
}

private fun findTaskById(data: DayteskData, id: Long): Tarea? =
    data.tareasHoy.find { it.id == id }
        ?: data.tareasSemana.find { it.id == id }
        ?: data.otrasPendientes.find { it.id == id }
        ?: data.completadas.find { it.id == id }

private fun Tarea.withCompletion(completed: Boolean): Tarea =
    if (completed) {
        copy(estado = TareaEstado.COMPLETADA, fechaCompletada = System.currentTimeMillis())
    } else {
        copy(estado = TareaEstado.PENDIENTE, fechaCompletada = null)
    }

@Composable
private fun DayteskBottomBar(
    currentEntry: NavKey?,
    onTabSelected: (NavKey) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = DayteskSpacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = currentEntry?.javaClass == tab.key.javaClass
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab.key) }
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) DayteskColors.Primary else DayteskColors.TextDisabled,
                        modifier = Modifier.size(24.dp),
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(DayteskColors.Primary),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = DayteskTypography.tiny,
                        color = if (isSelected) DayteskColors.Primary else DayteskColors.TextDisabled,
                    )
                }
            }
        }
    }
}
