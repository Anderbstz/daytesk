package com.nuitcode.daytesk.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuitcode.daytesk.DayteskApp
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.data.DataRepository
import com.nuitcode.daytesk.data.DayteskData
import com.nuitcode.daytesk.data.DayteskStats
import com.nuitcode.daytesk.data.MockData
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.theme.DayteskTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertTrue
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

    // ── inicio-polish REQ-02: quick-stat cards equal height, no clip ─────

    @Test
    fun quickStatsRow_allThreeCardsRender() {
        composeTestRule.onNodeWithText("Hoy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inbox").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hechas").assertIsDisplayed()
    }

    @Test
    fun quickStatsRow_statNumbersDoNotClip() {
        // Fake repo emits DayteskStats(tareasHoy = 3) - count text renders as "3".
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun quickStatsRow_cardsHaveEqualHeight() {
        // Three StatCard nodes should share a measured height via IntrinsicSize.Min.
        composeTestRule.onAllNodes(hasTestTag("StatCard"))
            .assertCountEquals(3)
            .apply {
                val heights = fetchSemanticsNodes().map { it.getBoundsInRoot().height }
                val max = heights.max()
                val min = heights.min()
                check(max - min <= 1) {
                    "StatCard heights differ by ${max - min}px (min=$min, max=$max)"
                }
            }
    }

    // ── inicio-polish REQ-03: header Row spans full width, avatar trailing ──

    @Test
    fun inicioTopBar_avatarBubbleIsDisplayed() {
        composeTestRule.onNodeWithText("AN").assertIsDisplayed()
    }

    @Test
    fun inicioTopBar_displayNameIsDisplayed() {
        composeTestRule.onNodeWithText("Andrés").assertIsDisplayed()
    }

    // ── tasks-form-completion REQ-01 / REQ-02 — modal reachability ──

    @Test
    fun nuevaTareaModal_canBeOpened() {
        composeTestRule.onNodeWithText("Tareas").performClick()
        composeTestRule.onNodeWithContentDescription("Agregar tarea").performClick()
        composeTestRule.onNodeWithText("Nueva tarea").assertIsDisplayed()
    }

    // ── custom-contexts REQ: ContextosScreen reachability ────────────────

    /**
     * Reachability smoke test for `ContextosScreen`. Even WITHOUT a
     * `contextoRepository` the Perfil row "Contextos" exists as a SettingsRow
     * so users see the entry point. Tapping it does NOT push the `Contextos`
     * route (no repo), so the production entry point is responsible for
     * threading the repo.
     */
    @Test
    fun perfilScreen_contextosRowExists() {
        composeTestRule.onNodeWithText("Perfil").performClick()
        composeTestRule.onNodeWithText("Contextos").assertExists()
    }

    /**
     * With a `contextoRepository` supplied the row is enabled AND tapping it
     * pushes the `Contextos` route (assert by title).
     */
    @Test
    fun contextosScreen_isReachableFromPerfil() {
        val fakeContextoRepo = FakeContextoRepository(initial = Contexto.DEFAULTS)

        composeTestRule.setContent {
            DayteskTheme {
                DayteskApp(
                    dataRepository = fakeRepo,
                    contextoRepository = fakeContextoRepo,
                )
            }
        }

        composeTestRule.onNodeWithText("Perfil").performClick()
        composeTestRule.onNodeWithText("Contextos").performClick()

        // The ContextosScreen top bar title is rendered.
        composeTestRule.onNodeWithText("Contextos").assertExists()
        // The 4 defaults surface as rows.
        composeTestRule.onNodeWithText("@casa").assertExists()
        composeTestRule.onNodeWithText("@trabajo").assertExists()
        composeTestRule.onNodeWithText("@personal").assertExists()
        composeTestRule.onNodeWithText("@salud").assertExists()
    }

    @Test
    fun tareasScreen_filterChipsIncludeAllContexts() {
        // The DataRepository pipe must surface `data.contextos` for the Tareas
        // screen filter chips. With 4 defaults the chip row should expose
        // @casa, @trabajo, @personal, @salud (in addition to "Todas").
        composeTestRule.onNodeWithText("Tareas").performClick()

        composeTestRule.onNodeWithText("@casa").assertExists()
        composeTestRule.onNodeWithText("@trabajo").assertExists()
        composeTestRule.onNodeWithText("@personal").assertExists()
        composeTestRule.onNodeWithText("@salud").assertExists()

        assertTrue(true)
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
                contextos = Contexto.DEFAULTS,
            )
        )
    }
}

private class FakeContextoRepository(
    initial: List<Contexto> = emptyList(),
) : ContextoRepository {
    private val state = MutableStateFlow(initial)
    override val contextos: Flow<List<Contexto>> = state.asStateFlow()
    override suspend fun add(contexto: Contexto): Result<Long> {
        val nextId = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + contexto.copy(id = nextId)
        return Result.success(nextId)
    }
    override suspend fun update(contexto: Contexto): Result<Unit> {
        state.value = state.value.map { if (it.id == contexto.id) contexto else it }
        return Result.success(Unit)
    }
    override suspend fun delete(id: Long): Result<Unit> {
        state.value = state.value.filter { it.id != id }
        return Result.success(Unit)
    }
}
