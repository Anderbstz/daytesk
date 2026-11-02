package com.nuitcode.daytesk.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nuitcode.daytesk.theme.DayteskColors
import com.nuitcode.daytesk.theme.DayteskTheme
import com.nuitcode.daytesk.theme.DayteskTypography
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The screen a ringing alarm takes over, like the system clock: it shows over the
 * lock screen, wakes the display and stays until the user answers or the ring
 * stops. It never plays sound; the vibration belongs to [AlarmVibrationService].
 */
class AlarmActivity : ComponentActivity() {
    private var recordatorioId by mutableLongStateOf(NO_ID)
    private var texto by mutableStateOf("")
    private var ringingNow by mutableStateOf<Map<Long, String>>(emptyMap())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        readExtras(intent)
        closeWhenTheRingStops()
        setContent {
            DayteskTheme {
                AlarmScreen(
                    texto = ringingNow.values.distinct().joinToString("\n").ifBlank { texto },
                    hora = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                    onDismiss = ::dismiss,
                    onSnooze = ::snooze,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    /** The manifest attributes only exist from API 27; the flags cover API 26. */
    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
    }

    private fun readExtras(source: Intent) {
        recordatorioId = source.getLongExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, NO_ID)
        texto = source.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
    }

    /**
     * Timeout, a deleted reminder or the notification actions can stop the ring
     * from elsewhere. Only a ring this screen saw closes it: when the service
     * could not start, this screen is the fallback and waits for the user.
     */
    private fun closeWhenTheRingStops() {
        lifecycleScope.launch {
            var sawRing = false
            AlarmVibrationService.ringing.collect { current ->
                ringingNow = current
                if (current.isNotEmpty()) {
                    sawRing = true
                } else if (sawRing) {
                    finish()
                }
            }
        }
    }

    private fun dismiss() {
        AlarmVibrationService.markAnswered(ringingNow.values + texto)
        AlarmVibrationService.stopAnyRinging(this)
        cancelFallbackNotification()
        finish()
    }

    /** [SnoozeReceiver] postpones everything ringing along with this reminder. */
    private fun snooze() {
        ReminderScheduler.snoozeActionPendingIntent(
            this,
            recordatorioId,
            texto,
            ReminderScheduler.recordatorioRequestCode(recordatorioId, early = false),
            esAlarma = true,
        ).send()
        finish()
    }

    private fun cancelFallbackNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ReminderScheduler.recordatorioRequestCode(recordatorioId, early = false))
    }

    companion object {
        private const val NO_ID = -1L

        fun intent(context: Context, id: Long, texto: String): Intent =
            Intent(context, AlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data = Uri.parse("daytesk://recordatorio/$id/alarm-screen")
                putExtra(ReminderScheduler.EXTRA_RECORDATORIO_ID, id)
                putExtra(ReminderScheduler.EXTRA_TITLE, texto)
            }
    }
}

@androidx.compose.runtime.Composable
private fun AlarmScreen(
    texto: String,
    hora: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayteskColors.Background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(hora, style = DayteskTypography.display, color = DayteskColors.TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            texto.ifBlank { "Recordatorio" },
            style = DayteskTypography.bodyMd,
            color = DayteskColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = DayteskColors.Primary),
        ) {
            Text("Descartar", color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text("Posponer 10 min", color = DayteskColors.Primary)
        }
    }
}
