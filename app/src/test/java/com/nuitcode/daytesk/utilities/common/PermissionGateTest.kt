package com.nuitcode.daytesk.utilities.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionGateTest {

    @Test
    fun permissionState_whenAllPermissionsGranted_allowsPendingAction() {
        assertEquals(
            PermissionGateOutcome.GRANTED,
            resolvePermissionGateOutcome(
                allGranted = true,
                shouldShowRationale = false,
                hasRequested = false,
            ),
        )
    }

    @Test
    fun permissionState_afterDenial_showsRationaleOrSettingsGuidance() {
        assertEquals(
            PermissionGateOutcome.RATIONALE,
            resolvePermissionGateOutcome(
                allGranted = false,
                shouldShowRationale = true,
                hasRequested = true,
            ),
        )
        assertEquals(
            PermissionGateOutcome.SETTINGS,
            resolvePermissionGateOutcome(
                allGranted = false,
                shouldShowRationale = false,
                hasRequested = true,
            ),
        )
    }
}
