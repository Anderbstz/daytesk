package com.nuitcode.daytesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nuitcode.daytesk.theme.DayteskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DayteskApplication
        enableEdgeToEdge()
        setContent {
            DayteskTheme {
                DayteskApp(database = app.database)
            }
        }
    }
}
