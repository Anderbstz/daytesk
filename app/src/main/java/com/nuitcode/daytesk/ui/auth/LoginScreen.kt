package com.nuitcode.daytesk.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.R
import com.nuitcode.daytesk.auth.AuthApi
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.data.local.AppDatabase
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    sessionStore: SessionStore,
    database: AppDatabase,
    onLoggedIn: () -> Unit,
) {
    var registerMode by remember { mutableStateOf(false) }
    var identifier by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var pendingSession by remember { mutableStateOf<AuthApi.LoginResult?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = if (registerMode) {
        email.isNotBlank() && username.length >= 3 && password.length >= 8
    } else {
        identifier.isNotBlank() && password.isNotBlank()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Daytesk", style = DayteskTypography.display, color = DayteskColors.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (registerMode) "Creá una cuenta para sincronizar tus tareas" else "Iniciá sesión para continuar",
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextSecondary,
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (registerMode) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
            )
            Spacer(modifier = Modifier.height(DayteskSpacing.md))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Usuario (mín. 3)") },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
            )
        } else {
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Usuario o email") },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
            )
        }
        Spacer(modifier = Modifier.height(DayteskSpacing.md))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (registerMode) "Contraseña (mín. 8)" else "Contraseña") },
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painter = painterResource(
                            if (showPassword) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                        ),
                        contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = DayteskColors.Primary,
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            textStyle = DayteskTypography.bodyMd.copy(color = DayteskColors.TextPrimary),
        )
        error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = DayteskColors.Urgent, style = DayteskTypography.bodySm)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (loading) return@Button
                loading = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        if (registerMode) {
                            AuthApi.register(email, username, password, username)
                        } else {
                            AuthApi.login(identifier, password)
                        }
                    }
                    loading = false
                    result.onSuccess { session ->
                        val askTransfer = registerMode && sessionStore.hadPreviousAccount
                        if (askTransfer) {
                            pendingSession = session
                        } else {
                            if (registerMode) {
                                withContext(Dispatchers.IO) {
                                    database.tareaDao().deleteAll()
                                    database.inboxItemDao().deleteAll()
                                }
                            }
                            sessionStore.save(
                                session.token,
                                session.displayName,
                                session.email,
                                newAccount = registerMode,
                                keepLocal = false,
                            )
                            onLoggedIn()
                        }
                    }.onFailure { failure ->
                        error = failure.message ?: "No se pudo continuar."
                    }
                }
            },
            enabled = !loading && canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = DayteskColors.Primary,
                disabledContainerColor = DayteskColors.PrimaryLight,
            ),
        ) {
            val label = when {
                loading && registerMode -> "Creando…"
                loading -> "Entrando…"
                registerMode -> "Crear cuenta"
                else -> "Entrar"
            }
            Text(label, color = androidx.compose.ui.graphics.Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                registerMode = !registerMode
                error = null
                showPassword = false
                password = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (registerMode) "Ya tengo cuenta" else "Crear cuenta",
                color = DayteskColors.Primary,
                style = DayteskTypography.bodyMd,
            )
        }
    }
    pendingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Pasar tareas") },
            text = {
                Text("¿Querés pasar las tareas de tu cuenta anterior a esta cuenta nueva?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionStore.save(
                            session.token,
                            session.displayName,
                            session.email,
                            newAccount = true,
                            keepLocal = true,
                        )
                        pendingSession = null
                        onLoggedIn()
                    },
                ) {
                    Text("Sí, pasarlas")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                database.tareaDao().deleteAll()
                                database.inboxItemDao().deleteAll()
                            }
                            sessionStore.save(
                                session.token,
                                session.displayName,
                                session.email,
                                newAccount = true,
                                keepLocal = false,
                            )
                            pendingSession = null
                            onLoggedIn()
                        }
                    },
                ) {
                    Text("No, empezar vacío")
                }
            },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DayteskColors.TextPrimary,
    unfocusedTextColor = DayteskColors.TextPrimary,
    disabledTextColor = DayteskColors.TextSecondary,
    focusedBorderColor = DayteskColors.Primary,
    unfocusedBorderColor = DayteskColors.TextSecondary,
    focusedContainerColor = DayteskColors.Surface,
    unfocusedContainerColor = DayteskColors.Surface,
    focusedLabelColor = DayteskColors.Primary,
    unfocusedLabelColor = DayteskColors.TextSecondary,
    cursorColor = DayteskColors.Primary,
    focusedTrailingIconColor = DayteskColors.Primary,
    unfocusedTrailingIconColor = DayteskColors.Primary,
)
