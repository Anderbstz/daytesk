package com.nuitcode.daytesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.navigation3.runtime.NavKey
import com.nuitcode.daytesk.auth.SessionStore
import com.nuitcode.daytesk.sync.CloudSync
import com.nuitcode.daytesk.theme.DayteskTheme
import com.nuitcode.daytesk.ui.auth.LoginScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DayteskApplication
        val sessionStore = SessionStore(this)
        sessionStore.applyToUser()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.White.toArgb(),
                Color.White.toArgb(),
            ),
        )
        setContent {
            DayteskTheme {
                var loggedIn by remember { mutableStateOf(sessionStore.isLoggedIn) }
                if (!loggedIn) {
                    LoginScreen(
                        sessionStore = sessionStore,
                        database = app.database,
                        onLoggedIn = { loggedIn = true },
                    )
                } else {
                    DayteskApp(
                        database = app.database,
                        initialTab = requestedTab(),
                        onLogout = {
                            CloudSync.cancelScheduled()
                            sessionStore.clear()
                            loggedIn = false
                        },
                    )
                }
            }
        }
    }

    /**
     * Resolves the tab the launcher/widget asked for. The recordatorio widget
     * opens the app on Recordatorios; every other entry point (launcher icon,
     * task widget) falls back to Inicio.
     */
    private fun requestedTab(): NavKey =
        if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_RECORDATORIOS) Recordatorios else Inicio

    companion object {
        /** Extra set by the recordatorio widget to select the initial tab. */
        const val EXTRA_OPEN_TAB = "com.nuitcode.daytesk.EXTRA_OPEN_TAB"

        /** [EXTRA_OPEN_TAB] value that opens the Recordatorios screen. */
        const val TAB_RECORDATORIOS = "recordatorios"
    }
}
