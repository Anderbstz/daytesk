package com.nuitcode.daytesk.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nuitcode.daytesk.auth.AuthApi
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskSpacing
import com.nuitcode.daytesk.theme.DayteskTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    sessionStore: SessionStore,
    onLoggedIn: () -> Unit,
) {
    var registerMode by remember { mutableStateOf(false) }
    var identifier by remember { mutableStateOf("ander") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("12345678") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canSubmit = if (registerMode) {
        email.isNotBlank() && username.isNotBlank() && password.length >= 8
    } else {
        identifier.isNotBlank() && password.isNotBlank()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .systemBarsPadding()
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
            )
            Spacer(modifier = Modifier.height(DayteskSpacing.md))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Usuario") },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
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
            )
        }
        Spacer(modifier = Modifier.height(DayteskSpacing.md))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(if (registerMode) "Contraseña (mín. 8)" else "Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
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
                        sessionStore.save(session.token, session.displayName, session.email)
                        onLoggedIn()
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
            colors = ButtonDefaults.buttonColors(containerColor = DayteskColors.Primary),
        ) {
            val label = when {
                loading && registerMode -> "Creando…"
                loading -> "Entrando…"
                registerMode -> "Crear cuenta"
                else -> "Entrar"
            }
            Text(label, color = androidx.compose.ui.graphics.Color.White)
        }
        TextButton(
            onClick = {
                registerMode = !registerMode
                error = null
                if (registerMode) password = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (registerMode) "Ya tengo cuenta" else "Crear cuenta",
                color = DayteskColors.Primary,
            )
        }
        if (!registerMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Usuario default: ander  ·  contraseña: 12345678",
                style = DayteskTypography.caption,
                color = DayteskColors.TextDisabled,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DayteskColors.Primary,
    unfocusedBorderColor = DayteskColors.Border,
    focusedContainerColor = DayteskColors.Surface,
    unfocusedContainerColor = DayteskColors.Surface,
    cursorColor = DayteskColors.Primary,
)
