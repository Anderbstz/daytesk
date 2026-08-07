package com.nuitcode.daytesk.ui.contextos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskShapes
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import kotlinx.coroutines.launch

/**
 * CRUD screen for user-managed contexts. Reached from `PerfilScreen`
 * (the "Contextos" SettingsRow pushes the `Contextos` route). Owns:
 *
 *  - the FAB → add flow (`ContextEditModal(initial = null)`)
 *  - the row tap → edit flow (`ContextEditModal(initial = contexto)`)
 *  - the row long-press → delete confirm (`AlertDialog`, hidden for defaults)
 *  - empty-state placeholder when the list has zero items
 *
 * The screen is read-only over [ContextoRepository.contextos] via
 * `collectAsStateWithLifecycle`; writes go through `add` / `update` /
 * `delete`. Default-context deletion is gated in the UI (`onLongPress`
 * is only armed when `!contexto.isDefault()`).
 */
@Composable
fun ContextosScreen(
    contextoRepository: ContextoRepository,
    onBack: () -> Unit,
) {
    val contextos by contextoRepository.contextos.collectAsStateWithLifecycle(
        initialValue = emptyList(),
    )
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Contexto?>(null) }
    var deleting by remember { mutableStateOf<Contexto?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .safeDrawingPadding()
            .semantics { testTag = "contextos_screen" },
        containerColor = DayteskColors.Background,
        topBar = {
            ContextosTopBar(onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = DayteskColors.Primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.semantics { testTag = "contextos_fab_add" },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar contexto",
                )
            }
        },
    ) { padding ->
        if (contextos.isEmpty()) {
            ContextosEmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(contextos, key = { it.id }) { contexto ->
                    ContextoRow(
                        contexto = contexto,
                        onClick = { editing = contexto },
                        onLongPress = {
                            if (!contexto.isDefault()) deleting = contexto
                        },
                    )
                }
            }
        }
    }

    // ── Add modal ─────────────────────────────────────────────
    if (showAdd) {
        ContextEditModal(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { nombre, color ->
                scope.launch {
                    val result = contextoRepository.add(
                        Contexto(id = 0, nombre = nombre, color = color),
                    )
                    if (result.isFailure) {
                        errorMessage = result.exceptionOrNull()?.message
                    }
                    showAdd = false
                }
            },
        )
    }

    // ── Edit modal ────────────────────────────────────────────
    editing?.let { current ->
        ContextEditModal(
            initial = current,
            onDismiss = { editing = null },
            onSave = { nombre, color ->
                scope.launch {
                    val result = contextoRepository.update(
                        current.copy(nombre = nombre, color = color),
                    )
                    if (result.isFailure) {
                        errorMessage = result.exceptionOrNull()?.message
                    }
                    editing = null
                }
            },
        )
    }

    // ── Delete confirmation ───────────────────────────────────
    deleting?.let { current ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = {
                Text(
                    text = "¿Eliminar contexto?",
                    style = DayteskTypography.h3,
                    color = DayteskColors.TextPrimary,
                )
            },
            text = {
                Text(
                    text = "Vas a eliminar \"@${current.nombre}\". Esta acción no se puede deshacer.",
                    style = DayteskTypography.bodySm,
                    color = DayteskColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = contextoRepository.delete(current.id)
                            if (result.isFailure) {
                                errorMessage = result.exceptionOrNull()?.message
                            }
                            deleting = null
                        }
                    },
                    modifier = Modifier.semantics { testTag = "contextos_delete_confirm" },
                ) {
                    Text(
                        text = "Eliminar",
                        style = DayteskTypography.label.copy(color = DayteskColors.Urgent),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    modifier = Modifier.semantics { testTag = "contextos_delete_cancel" },
                ) {
                    Text(
                        text = "Cancelar",
                        style = DayteskTypography.label.copy(color = DayteskColors.TextSecondary),
                    )
                }
            },
            containerColor = DayteskColors.Surface,
        )
    }

    // ── Error toast (rendered as a simple top banner) ─────────
    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", style = DayteskTypography.label)
                }
            },
            title = { Text("Error", style = DayteskTypography.h3) },
            text = { Text(message, style = DayteskTypography.bodySm) },
            containerColor = DayteskColors.Surface,
        )
    }
}

// ── Top bar ────────────────────────────────────────────────────

@Composable
private fun ContextosTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DayteskSpacing.xl, vertical = DayteskSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Volver",
            tint = DayteskColors.TextSecondary,
            modifier = Modifier
                .size(24.dp)
                .clickable { onBack() }
                .semantics { testTag = "contextos_back" },
        )
        Spacer(modifier = Modifier.width(DayteskSpacing.md))
        Text(
            text = "Contextos",
            style = DayteskTypography.display,
            color = DayteskColors.TextPrimary,
            modifier = Modifier.semantics { testTag = "contextos_title" },
        )
    }
}

// ── Row ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContextoRow(
    contexto: Contexto,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DayteskShapes.medium)
            .background(DayteskColors.Surface)
            .border(1.dp, DayteskColors.Border, DayteskShapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(14.dp)
            .semantics { testTag = "contextos_row_${contexto.id}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color swatch
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(contexto.color))
                .border(1.dp, DayteskColors.Border, CircleShape),
        )
        Spacer(modifier = Modifier.width(DayteskSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contexto.label(),
                style = DayteskTypography.bodyMd.copy(fontWeight = FontWeight.Medium),
                color = DayteskColors.TextPrimary,
            )
            if (contexto.isDefault()) {
                Text(
                    text = "Predeterminado",
                    style = DayteskTypography.caption,
                    color = DayteskColors.TextDisabled,
                )
            }
        }

        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Más",
            tint = DayteskColors.TextDisabled,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Empty state ────────────────────────────────────────────────

@Composable
private fun ContextosEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = DayteskColors.TextDisabled,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Agregá tu primer contexto",
            style = TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = DayteskColors.TextSecondary,
            ),
            modifier = Modifier.semantics { testTag = "contextos_empty_cta" },
        )
    }
}
