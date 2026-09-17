package com.nuitcode.daytesk.ui.recordatorios

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strict TDD — RED test written before the double-tap save guard existed.
 *
 * Contract (see sdd/recordatorios-ux review, RESIL-005 / RELI-003):
 *   - The first tap on a save surface acquires the guard.
 *   - A second tap while the first save is still in flight is a no-op.
 *   - Guards are per-surface, so a second surface is never blocked by the first.
 */
class SaveGuardTest {

    @Test
    fun firstSaveIsAllowed() {
        assertTrue("the first tap must always acquire the guard", SaveGuard().tryBegin())
    }

    @Test
    fun secondTapWhileSavingIsANoOp() {
        val guard = SaveGuard()

        assertTrue(guard.tryBegin())

        assertFalse("a fast double tap must not enqueue a second insert", guard.tryBegin())
        assertFalse("and every further tap stays blocked", guard.tryBegin())
    }

    @Test
    fun guardsAreIndependentPerSurface() {
        val modalGuard = SaveGuard()
        val captureGuard = SaveGuard()

        assertTrue(modalGuard.tryBegin())
        assertTrue("a save on one surface must not block the other", captureGuard.tryBegin())
    }
}
