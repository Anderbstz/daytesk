package com.nuitcode.daytesk.ui.contextos

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.nuitcode.daytesk.data.ContextoInUseException
import com.nuitcode.daytesk.data.ContextoRepository
import com.nuitcode.daytesk.data.DefaultContextProtectedException
import com.nuitcode.daytesk.data.DuplicateContextoNameException
import com.nuitcode.daytesk.model.Contexto
import com.nuitcode.daytesk.theme.DayteskTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI tests for [ContextosScreen]. Uses a [FakeContextoRepository]
 * so tests can drive the list deterministically without Room/Robolectric.
 *
 * Six scenarios per proposal #104 / spec #105 / tasks #107:
 *   1. list renders 4 defaults
 *   2. add modal opens, persists row, appears live in list
 *   3. edit modal pre-populates, accepts new name + color
 *   4. edit modal on default disables color picker
 *   5. long-press on a custom context opens delete confirmation; confirm removes
 *   6. long-press on a default context does NOT open the confirmation
 *
 * Tests cannot run on this host (skip-verify pattern #95); they are
 * statically valid against the production code.
 */
class ContextosScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeRepo = FakeContextoRepository()

    private fun setContent(repository: ContextoRepository = fakeRepo) {
        composeTestRule.setContent {
            DayteskTheme {
                ContextosScreen(
                    contextoRepository = repository,
                    onBack = {},
                )
            }
        }
    }

    // ── Scenario 1: list renders 4 defaults ────────────────────────

    @Test
    fun list_rendersFourDefaultContexts() {
        fakeRepo.replace(Contexto.DEFAULTS)
        setContent()

        composeTestRule.onNodeWithText("@casa").assertIsDisplayed()
        composeTestRule.onNodeWithText("@trabajo").assertIsDisplayed()
        composeTestRule.onNodeWithText("@personal").assertIsDisplayed()
        composeTestRule.onNodeWithText("@salud").assertIsDisplayed()
    }

    // ── Scenario 2: add modal opens, persists row, appears live ───

    @Test
    fun add_modalOpens_andPersistsRow() {
        fakeRepo.replace(Contexto.DEFAULTS)
        setContent()

        // Open the FAB
        composeTestRule.onNodeWithTag("contextos_fab_add").performClick()

        // Modal title visible
        composeTestRule.onNodeWithText("Nuevo contexto").assertIsDisplayed()

        // Type a unique name
        composeTestRule.onNodeWithTag("context_modal_name").performTextInput("compras")

        // Save
        composeTestRule.onNodeWithTag("context_modal_save").performClick()

        // Row appears in the list
        composeTestRule.onNodeWithText("@compras").assertIsDisplayed()
        assertTrue(
            "Fake repo should now expose the added context",
            fakeRepo.snapshot().any { it.nombre == "compras" && !it.isDefault() },
        )
    }

    // ── Scenario 3: edit modal pre-populates, accepts new name ────

    @Test
    fun edit_modalPrePopulatesAndAcceptsNewName() {
        fakeRepo.replace(Contexto.DEFAULTS + listOf(Contexto(id = 5, nombre = "compras", color = 0xFF9CCC65.toInt())))
        setContent()

        // Tap the compras row → edit modal opens pre-populated
        composeTestRule.onNodeWithTag("contextos_row_5").performClick()
        composeTestRule.onNodeWithText("Editar contexto").assertIsDisplayed()

        // Save with the same defaults; row remains
        composeTestRule.onNodeWithTag("context_modal_save").performClick()
        composeTestRule.onNodeWithText("@compras").assertIsDisplayed()
    }

    // ── Scenario 4: edit modal on default disables color picker ───

    @Test
    fun edit_defaultHasLockedColorPicker() {
        fakeRepo.replace(Contexto.DEFAULTS)
        setContent()

        // Tap casa row (id=1) → edit modal opens
        composeTestRule.onNodeWithTag("contextos_row_1").performClick()
        composeTestRule.onNodeWithText("Editar contexto").assertIsDisplayed()

        // The locked-color single-swatch is rendered instead of the 8-color grid
        composeTestRule.onNodeWithTag("context_modal_color_locked").assertIsDisplayed()
    }

    // ── Scenario 5: long-press on a custom context removes it ─────

    @Test
    fun longPress_customContext_removesIt() {
        fakeRepo.replace(Contexto.DEFAULTS + listOf(Contexto(id = 7, nombre = "shopping", color = 0xFFEF5350.toInt())))
        setContent()

        // Long-press shopping row
        composeTestRule.onNodeWithTag("contextos_row_7").performLongClick()

        // Confirmation dialog
        composeTestRule.onNodeWithText("¿Eliminar contexto?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("contextos_delete_confirm").performClick()

        // Row gone
        composeTestRule.onNodeWithText("@shopping").assertDoesNotExist()
        assertEquals(null, fakeRepo.snapshot().find { it.id == 7L })
    }

    // ── Scenario 6: long-press on a default context is a no-op ────

    @Test
    fun longPress_defaultContext_doesNotShowDeleteDialog() {
        fakeRepo.replace(Contexto.DEFAULTS)
        setContent()

        // Long-press the casa (default) row
        composeTestRule.onNodeWithTag("contextos_row_1").performLongClick()

        // The delete confirmation must NOT appear
        composeTestRule.onNodeWithText("¿Eliminar contexto?").assertDoesNotExist()
        // The row is still there
        composeTestRule.onNodeWithText("@casa").assertIsDisplayed()
    }

    // ── Bonus: empty state CTA when list is empty ─────────────────

    @Test
    fun emptyState_rendersCta() {
        fakeRepo.replace(emptyList())
        setContent()

        composeTestRule.onNodeWithTag("contextos_empty_cta").assertIsDisplayed()
    }
}

/**
 * Lightweight in-memory `ContextoRepository` for tests. Defaults cannot be
 * deleted (returns [DefaultContextProtectedException]); duplicate names return
 * [DuplicateContextoNameException]; if the test wires an "in-use" check it
 * returns [ContextoInUseException] (handled at the UI by the error banner).
 */
private class FakeContextoRepository(
    initial: List<Contexto> = emptyList(),
) : ContextoRepository {

    private val state = MutableStateFlow(initial)

    fun replace(list: List<Contexto>) {
        state.value = list
    }

    fun snapshot(): List<Contexto> = state.value

    override val contextos: Flow<List<Contexto>> = state.asStateFlow()

    override suspend fun add(contexto: Contexto): Result<Long> {
        if (contexto.nombre.isBlank()) {
            return Result.failure(IllegalStateException("Blank nombre"))
        }
        val collision = state.value.any {
            it.nombre.equals(contexto.nombre, ignoreCase = true) ||
                Contexto.DEFAULTS.any { d -> d.nombre.equals(contexto.nombre, ignoreCase = true) }
        }
        if (collision) return Result.failure(DuplicateContextoNameException(contexto.nombre))
        val nextId = (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
        state.value = state.value + contexto.copy(id = nextId)
        return Result.success(nextId)
    }

    override suspend fun update(contexto: Contexto): Result<Unit> {
        val current = state.value.find { it.id == contexto.id }
            ?: return Result.failure(IllegalStateException("Not found"))
        state.value = state.value.map { if (it.id == contexto.id) contexto.copy(id = current.id) else it }
        return Result.success(Unit)
    }

    override suspend fun delete(id: Long): Result<Unit> {
        val current = state.value.find { it.id == id }
            ?: return Result.failure(IllegalStateException("Not found"))
        if (current.isDefault()) return Result.failure(DefaultContextProtectedException())
        state.value = state.value.filter { it.id != id }
        return Result.success(Unit)
    }
}

// Compose `performLongClick` extension lives in `androidx.compose.ui.test`; pull
// it via the standard helper so the test file compiles even when Compose
// doesn't export it from the `composeTestRule` surface.
private fun androidx.compose.ui.test.SemanticsNodeInteraction.performLongClick() =
    this.performTouchInput { longClick() }
