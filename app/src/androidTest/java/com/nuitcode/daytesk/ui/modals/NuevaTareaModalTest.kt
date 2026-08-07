package com.nuitcode.daytesk.ui.modals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nuitcode.daytesk.theme.DayteskTheme
import org.junit.Rule
import org.junit.Test

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
}
