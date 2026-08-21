package com.nuitcode.daytesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        enableEdgeToEdge()
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
}
