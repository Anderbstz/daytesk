package com.nuitcode.daytesk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.nuitcode.daytesk.data.local.RecordatorioDao
import com.nuitcode.daytesk.data.local.TareaDao
import com.nuitcode.daytesk.data.local.toEntity
import com.nuitcode.daytesk.data.archiveExpiredTasks
import com.nuitcode.daytesk.data.deleteRecordatorio
import com.nuitcode.daytesk.data.persistRecordatorio
import com.nuitcode.daytesk.data.persistTaskCompletion
import com.nuitcode.daytesk.data.rollExpiredRecordatorios
import com.nuitcode.daytesk.model.Recordatorio
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.model.TareaEstado
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.sync.CloudSync
import com.nuitcode.daytesk.widget.NextTaskWidgetProvider
import com.nuitcode.daytesk.widget.RecordatorioWidgetProvider
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.ui.contextos.ContextEditModal
import com.nuitcode.daytesk.ui.contextos.ContextosScreen
import com.nuitcode.daytesk.ui.inicio.InicioScreen
import com.nuitcode.daytesk.ui.main.DayteskUiState
import com.nuitcode.daytesk.ui.main.MainScreenViewModel
import com.nuitcode.daytesk.notification.ReminderScheduler
import com.nuitcode.daytesk.notification.applyNotificationsEnabled
import com.nuitcode.daytesk.ui.historial.HistorialScreen
import com.nuitcode.daytesk.ui.modals.DetalleTareaModal
import com.nuitcode.daytesk.ui.modals.NuevaTareaModal
import com.nuitcode.daytesk.ui.modals.RevisionSemanalModal
import com.nuitcode.daytesk.ui.recordatorios.RecordatorioModal
import com.nuitcode.daytesk.ui.recordatorios.RecordatoriosScreen
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
    TabItem(Recordatorios, Icons.Default.Notifications, "Recordatorios"),
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
    initialTab: NavKey = Inicio,
    onLogout: () -> Unit = {},
) {
    val tareaDao = database.tareaDao()
    val recordatorioDao = database.recordatorioDao()
    val contextoDao = database.contextoDao()
    val repository = remember { DefaultDataRepository(tareaDao, recordatorioDao, contextoDao) }
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(repository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showNuevaTarea by remember { mutableStateOf(false) }
    var editingTarea by remember { mutableStateOf<Tarea?>(null) }
    var showRevisionSemanal by remember { mutableStateOf(false) }
    var detalleTareaSeleccionada by remember { mutableStateOf<Tarea?>(null) }
    // Saved so a rotation keeps the create/edit surface open. The edit target is
    // held by id (a domain object is not saveable) and resolved from the loaded
    // data, preserving the text the user already typed.
    var showRecordatorioModal by rememberSaveable { mutableStateOf(false) }
    var recordatorioEditandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAddContexto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }
    val cloudSync = remember { CloudSync(context, database, sessionStore) }
    var syncReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cloudSync.onStart()
        syncReady = true
    }

    // Hoisted so modals outside the Success branch can still read `data.contextos`.
    var cachedData by remember { mutableStateOf<DayteskData?>(null) }

    when (val state = uiState) {
        is DayteskUiState.Loading -> {
            // Brief loading
        }
        is DayteskUiState.Success -> {
            cachedData = state.data
            RequestNotificationPermissionOnLaunch()
            LaunchedEffect(state.data, syncReady) {
                if (!syncReady) return@LaunchedEffect
                archiveExpiredTasks(context, tareaDao)
                ReminderScheduler.reschedulePending(
                    context,
                    state.data.tareasHoy + state.data.tareasSemana +
                        state.data.otrasPendientes + state.data.vencidas,
                )
                // Roll recurring reminders forward, then re-register every
                // alarm (both are idempotent with the receiver's own roll).
                rollExpiredRecordatorios(context, database)
                ReminderScheduler.rescheduleRecordatorios(context, state.data.recordatorios)
                NextTaskWidgetProvider.refresh(context)
                RecordatorioWidgetProvider.refresh(context)
                CloudSync.schedulePush(scope, cloudSync)
            }
            DayteskNavScaffold(
                data = state.data,
                contextoRepository = contextoRepository,
                tareaDao = tareaDao,
                recordatorioDao = recordatorioDao,
                initialTab = initialTab,
                onShowNuevaTarea = {
                    editingTarea = null
                    showNuevaTarea = true
                },
                onShowDetalleTarea = { tarea -> detalleTareaSeleccionada = tarea },
                onNewRecordatorio = {
                    recordatorioEditandoId = null
                    showRecordatorioModal = true
                },
                onEditRecordatorio = { recordatorio ->
                    recordatorioEditandoId = recordatorio.id
                    showRecordatorioModal = true
                },
                onShowRevisionSemanal = { showRevisionSemanal = true },
                onLogout = onLogout,
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
                initial = editingTarea,
                onDismiss = {
                    showNuevaTarea = false
                    editingTarea = null
                },
                onSave = { tarea ->
                    scope.launch {
                        if (tarea.id == 0L) {
                            val id = tareaDao.insertTarea(tarea.toEntity())
                            ReminderScheduler.scheduleIfDue(context, id, tarea.titulo, tarea.fechaVencimiento)
                        } else {
                            tareaDao.updateTarea(tarea.toEntity())
                            ReminderScheduler.scheduleIfDue(context, tarea.id, tarea.titulo, tarea.fechaVencimiento)
                        }
                        showNuevaTarea = false
                        editingTarea = null
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
            onEdit = {
                editingTarea = tarea
                showNuevaTarea = true
                detalleTareaSeleccionada = null
            },
            onComplete = {
                scope.launch {
                    persistTaskCompletion(context, tareaDao, tarea, completed = true)
                    detalleTareaSeleccionada = null
                }
            },
            onDelete = {
                scope.launch {
                    ReminderScheduler.cancelTaskReminder(context, tarea.id)
                    tareaDao.deleteTarea(tarea.toEntity())
                    NextTaskWidgetProvider.refresh(context)
                    detalleTareaSeleccionada = null
                }
            },
        )
    }

    // ── Modal: Nuevo/Editar Recordatorio ──────────────────────
    // Resolved by id so the edit target survives rotation. An edit waits for the
    // first data emission before composing: composing it earlier would key the
    // modal on `null` and later reset the restored input when the target
    // arrives, which is exactly the loss this restore is meant to prevent.
    val recordatorioEditando =
        cachedData?.recordatorios?.firstOrNull { it.id == recordatorioEditandoId }
    if (showRecordatorioModal && (recordatorioEditandoId == null || recordatorioEditando != null)) {
        RecordatorioModal(
            initial = recordatorioEditando,
            onDismiss = {
                showRecordatorioModal = false
                recordatorioEditandoId = null
            },
            onSave = { recordatorio ->
                scope.launch {
                    persistRecordatorio(context, recordatorioDao, recordatorio)
                    showRecordatorioModal = false
                    recordatorioEditandoId = null
                }
            },
            onDelete = { recordatorio ->
                scope.launch {
                    deleteRecordatorio(context, recordatorioDao, recordatorio)
                    showRecordatorioModal = false
                    recordatorioEditandoId = null
                }
            },
        )
    }

    cachedData?.let { currentData ->
        if (showRevisionSemanal) {
            RevisionSemanalModal(
                recordatoriosPendientes = currentData.stats.recordatoriosPendientes,
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

/**
 * Requests `POST_NOTIFICATIONS` once the logged-in shell is on screen.
 *
 * The `rememberSaveable` guard makes the prompt fire at most once per launch:
 * a configuration change (rotation) restores the flag instead of re-prompting,
 * satisfying the "Prompt on launch" scenario without nagging the user.
 */
@Composable
private fun RequestNotificationPermissionOnLaunch() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (prompted) return@LaunchedEffect
        prompted = true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                recordatorioDao = null,
                onShowNuevaTarea = { showNuevaTarea = true },
                onShowDetalleTarea = {},
                onNewRecordatorio = {},
                onEditRecordatorio = {},
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
    recordatorioDao: RecordatorioDao?,
    onShowNuevaTarea: () -> Unit,
    onShowDetalleTarea: (Tarea) -> Unit,
    onNewRecordatorio: () -> Unit,
    onEditRecordatorio: (Recordatorio) -> Unit,
    onShowRevisionSemanal: () -> Unit,
    initialTab: NavKey = Inicio,
    onLogout: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(initialTab)
    val currentEntry = backStack.lastOrNull()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pluginStore = remember { com.nuitcode.daytesk.data.HomePluginStore(context) }
    var homePlugins by remember { mutableStateOf(pluginStore.load()) }
    // Anchors the Inicio FAB dropdown; only the Inicio branch ever opens it.
    var menu by remember { mutableStateOf(false) }

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
            when (currentEntry) {
                is Recordatorios -> Fab(
                    onClick = onNewRecordatorio,
                    icon = Icons.Default.Notifications,
                    contentDescription = "Nuevo recordatorio",
                )
                is Inicio -> Box {
                    FloatingActionButton(
                        onClick = { menu = true },
                        containerColor = DayteskColors.Primary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar",
                        )
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nueva tarea") },
                            onClick = {
                                menu = false
                                onShowNuevaTarea()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Nuevo recordatorio") },
                            onClick = {
                                menu = false
                                onNewRecordatorio()
                            },
                        )
                    }
                }
                // Tareas and every other route (Perfil, Utilidades, sub-routes)
                // keep the original task-creation behavior.
                else -> Fab(
                    onClick = onShowNuevaTarea,
                    icon = Icons.Default.Add,
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
                                    persistTaskCompletion(context, tareaDao, task, completed)
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
                entry<Recordatorios> {
                    RecordatoriosScreen(
                        data = data,
                        onEdit = onEditRecordatorio,
                        onDelete = { recordatorio ->
                            if (recordatorioDao != null) {
                                scope.launch {
                                    deleteRecordatorio(context, recordatorioDao, recordatorio)
                                }
                            }
                        },
                        onNew = onNewRecordatorio,
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
                                    persistTaskCompletion(context, tareaDao, task, completed)
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
                            onLogout()
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
                        onNotificationsChanged = { enabled ->
                            scope.launch {
                                val tareas = tareaDao
                                val recordatorios = recordatorioDao
                                if (tareas != null && recordatorios != null) {
                                    applyNotificationsEnabled(
                                        context,
                                        enabled,
                                        tareas,
                                        recordatorios,
                                    )
                                } else {
                                    SessionStore(context).notificationsEnabled = enabled
                                }
                            }
                        },
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
private fun Fab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = DayteskColors.Primary,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}

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
        ?: data.vencidas.find { it.id == id }
        ?: data.completadas.find { it.id == id }
        ?: data.historial.find { it.id == id }

@Composable
private fun DayteskBottomBar(
    currentEntry: NavKey?,
    onTabSelected: (NavKey) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
