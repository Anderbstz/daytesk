package com.nuitcode.daytesk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.DefaultDataRepository
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import com.nuitcode.daytesk.ui.inbox.InboxScreen
import com.nuitcode.daytesk.ui.inicio.InicioScreen
import com.nuitcode.daytesk.ui.main.DayteskUiState
import com.nuitcode.daytesk.ui.main.MainScreenViewModel
import com.nuitcode.daytesk.ui.perfil.PerfilScreen
import com.nuitcode.daytesk.ui.tareas.TareasScreen
import com.nuitcode.daytesk.ui.utilidades.UtilidadesScreen
import androidx.navigation3.runtime.NavKey

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

@Composable
fun DayteskApp(
    dataRepository: DataRepository = DefaultDataRepository(),
) {
    val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(dataRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is DayteskUiState.Loading -> {
            // Brief loading — no content
        }
        is DayteskUiState.Success -> {
            DayteskNavScaffold(data = state.data)
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
}

@Composable
private fun DayteskNavScaffold(data: DayteskData) {
    val backStack = rememberNavBackStack(Inicio)
    val currentEntry = backStack.lastOrNull()

    Scaffold(
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
                onClick = { /* TODO: quick capture */ },
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
                entry<Inicio> { InicioScreen(data) }
                entry<Inbox> { InboxScreen(data) }
                entry<Tareas> { TareasScreen(data) }
                entry<Utilidades> { UtilidadesScreen(data) }
                entry<Perfil> { PerfilScreen(data) }
            },
        )
    }
}

@Composable
private fun DayteskBottomBar(
    currentEntry: NavKey?,
    onTabSelected: (NavKey) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
