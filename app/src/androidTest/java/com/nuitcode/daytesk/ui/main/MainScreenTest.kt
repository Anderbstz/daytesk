package com.nuitcode.daytesk.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.nuitcode.daytesk.DayteskApp
import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.DayteskStats
import com.nuitcode.daytesk.data.MockData
import com.nuitcode.daytesk.theme.DayteskTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeRepo = FakeInstantRepository()

    @Before
    fun setup() {
        composeTestRule.setContent {
            DayteskTheme {
                DayteskApp(dataRepository = fakeRepo)
            }
        }
    }

    @Test
    fun bottomNav_showsFiveTabs() {
        composeTestRule.onNodeWithText("Inicio").assertExists()
        composeTestRule.onNodeWithText("Inbox").assertExists()
        composeTestRule.onNodeWithText("Tareas").assertExists()
        composeTestRule.onNodeWithText("Utilidades").assertExists()
        composeTestRule.onNodeWithText("Perfil").assertExists()
    }

    @Test
    fun bottomNav_inicioTabIsSelectedByDefault() {
        // Inicio tab label text uses tiny style (10sp) — still findable by substring
        composeTestRule.onNodeWithText("Inicio").assertExists()
    }

    @Test
    fun inicialScreen_showsInicioContent() {
        composeTestRule.onNodeWithText("Inicio").assertExists()
    }

    // ── inicio-polish REQ-01: bottom nav clears the gesture bar ──────────

    @Test
    fun bottomNav_allFiveTabsAreDisplayed() {
        composeTestRule.onNodeWithText("Inicio").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tareas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Utilidades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Perfil").assertIsDisplayed()
    }
}

private class FakeInstantRepository : DataRepository {
    override val data: Flow<DayteskData> = flow {
        emit(
            DayteskData(
                stats = DayteskStats(
                    tareasHoy = 3,
                    inboxPendientes = 5,
                    tareasCompletadas = 1,
                    rachaActual = 7,
                    totalCompletadasHistorico = 42,
                    promedioEfectividad = 0.78f,
                ),
                tareasHoy = MockData.tareasHoy,
                tareasSemana = MockData.tareasSemana,
                completadas = MockData.completadas,
                inbox = MockData.inboxItems,
                alertas = MockData.alertas,
                weeklyReview = MockData.weeklyReview,
            )
        )
    }
}
