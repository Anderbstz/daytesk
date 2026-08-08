package com.nuitcode.daytesk.utilities.common

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** User-facing explanation shown before requesting a runtime permission. */
data class PermissionRationale(
    val title: String,
    val message: String,
)

enum class PermissionGateOutcome {
    GRANTED,
    RATIONALE,
    SETTINGS,
}

internal fun resolvePermissionGateOutcome(
    allGranted: Boolean,
    shouldShowRationale: Boolean,
    hasRequested: Boolean,
): PermissionGateOutcome = when {
    allGranted -> PermissionGateOutcome.GRANTED
    hasRequested && shouldShowRationale -> PermissionGateOutcome.RATIONALE
    hasRequested -> PermissionGateOutcome.SETTINGS
    else -> PermissionGateOutcome.RATIONALE
}

/**
 * Defers the permission prompt until the wrapped action is requested by the user.
 * The action is replayed after the permission result is granted.
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    rationale: PermissionRationale,
    content: @Composable (requestPermission: (onGranted: () -> Unit) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var hasRequested by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PermissionGateOutcome?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val allGranted = permissions.all { permission ->
            result[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        val shouldShowRationale = permissions.any { permission ->
            (context as? Activity)?.shouldShowRequestPermissionRationale(permission) == true
        }
        hasRequested = true
        outcome = resolvePermissionGateOutcome(allGranted, shouldShowRationale, hasRequested)
        if (allGranted) {
            outcome = PermissionGateOutcome.GRANTED
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    fun requestPermission(onGranted: () -> Unit) {
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            onGranted()
            return
        }
        pendingAction = onGranted
        launcher.launch(permissions.toTypedArray())
    }

    content(::requestPermission)

    when (outcome) {
        PermissionGateOutcome.RATIONALE -> {
            AlertDialog(
                onDismissRequest = { outcome = null },
                title = { Text(rationale.title) },
                text = { Text(rationale.message) },
                confirmButton = {
                    Button(
                        onClick = {
                            outcome = null
                            launcher.launch(permissions.toTypedArray())
                        },
                    ) {
                        Text("Conceder permiso")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { outcome = null }) {
                        Text("Ahora no")
                    }
                },
            )
        }

        PermissionGateOutcome.SETTINGS -> {
            AlertDialog(
                onDismissRequest = { outcome = null },
                title = { Text("Permiso requerido") },
                text = { Text("Habilitalo desde Ajustes para continuar usando esta herramienta.") },
                confirmButton = {
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                            outcome = null
                        },
                    ) {
                        Text("Abrir ajustes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { outcome = null }) {
                        Text("Cerrar")
                    }
                },
            )
        }

        PermissionGateOutcome.GRANTED, null -> Unit
    }
}
