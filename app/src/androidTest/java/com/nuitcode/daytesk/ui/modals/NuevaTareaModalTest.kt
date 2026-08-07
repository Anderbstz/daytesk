package com.nuitcode.daytesk.ui.modals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nuitcode.daytesk.model.Tarea
import com.nuitcode.daytesk.theme.DayteskTheme
import org.junit.Rule
import org.junit.Test
import java.util.Calendar

class NuevaTareaModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setModal() {
        composeTestRule.setContent {
            DayteskTheme {
                NuevaTareaModal(onDismiss = {}, onSave = {})
            }
        }
    }

    // ── Phase 1: REQ-03 / REQ-04 — counter visibility + maxLength ────────

    @Test
    fun counter_visibleOnOpen() {
        setModal()
        composeTestRule.onNodeWithText("0/150").assertExists()
        composeTestRule.onNodeWithText("0/500").assertExists()
    }

    @Test
    fun counter_updatesAsUserTypes() {
        setModal()
        composeTestRule.onNodeWithText("Título de la tarea").performTextInput("12345")
        composeTestRule.onNodeWithText("5/150").assertExists()
    }

    @Test
    fun counter_tituloMaxLengthEnforced() {
        setModal()
        composeTestRule.onNodeWithText("Título de la tarea").performTextInput("x".repeat(200))
        composeTestRule.onNodeWithText("150/150").assertExists()
    }

    @Test
    fun counter_descripcionMaxLengthEnforced() {
        setModal()
        composeTestRule.onNodeWithText("Descripción (opcional)").performTextInput("y".repeat(600))
        composeTestRule.onNodeWithText("500/500").assertExists()
    }

    // ── Phase 2: REQ-01 — DatePicker open + cancel wiring ──────────────

    @Test
    fun datePicker_opensOnFechaRowClick() {
        setModal()
        composeTestRule.onNodeWithText("Sin fecha").performClick()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun datePicker_cancelDoesNotMutateFecha() {
        setModal()
        composeTestRule.onNodeWithText("Sin fecha").performClick()
        composeTestRule.onNodeWithText("Cancelar").performClick()
        composeTestRule.onNodeWithText("Sin fecha").assertIsDisplayed()
    }

    // ── Phase 3: REQ-01 / REQ-02 — TimePicker confirm writes chosen ts ──

    @Test
    fun datePicker_confirmSetsFechaVencimiento() {
        val captured = mutableListOf<Tarea>()
        composeTestRule.setContent {
            DayteskTheme {
                NuevaTareaModal(onDismiss = {}, onSave = { captured.add(it) })
            }
        }
        composeTestRule.onNodeWithText("Título de la tarea").performTextInput("Mi tarea")
        composeTestRule.onNodeWithText("Sin fecha").performClick()
        composeTestRule.onNodeWithText("OK").performClick() // confirm DatePicker
        composeTestRule.onNodeWithText("OK").performClick() // confirm TimePicker (defaults 12:00)
        composeTestRule.onNodeWithText("Crear tarea").performClick()
        assert(captured.size == 1) {
            "Expected 1 captured tarea, got ${captured.size}"
        }
        val t = captured[0]
        assert(t.titulo == "Mi tarea") {
            "Expected titulo 'Mi tarea', got '${t.titulo}'"
        }
        assert(t.fechaVencimiento != null) {
            "Expected non-null fechaVencimiento after confirming date+time"
        }
        // The TimePicker state defaults to 12:00; Calendar.set must produce
        // 12:00:00.000 in the default time zone — proves the binding logic.
        val cal = Calendar.getInstance().apply {
            timeInMillis = t.fechaVencimiento!!
        }
        assert(cal.get(Calendar.HOUR_OF_DAY) == 12) {
            "Expected HOUR_OF_DAY=12, got ${cal.get(Calendar.HOUR_OF_DAY)}"
        }
        assert(cal.get(Calendar.MINUTE) == 0) {
            "Expected MINUTE=0, got ${cal.get(Calendar.MINUTE)}"
        }
        assert(cal.get(Calendar.SECOND) == 0) {
            "Expected SECOND=0, got ${cal.get(Calendar.SECOND)}"
        }
        assert(cal.get(Calendar.MILLISECOND) == 0) {
            "Expected MILLISECOND=0, got ${cal.get(Calendar.MILLISECOND)}"
        }
    }
}
