package com.nuitcode.daytesk.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * What the system must allow for an alarm to fire on time and take the screen.
 *
 * Each grant lives on its own settings screen, so [openNextSetting] walks them one
 * at a time. Xiaomi adds per-app switches (autostart, pop-ups in the background,
 * show on lock screen) that no API can read, so its screen is always offered last.
 */
object AlarmPermissions {
    private const val MIUI_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun missingStandardGrant(context: Context): Intent? = when {
        !ReminderScheduler.canScheduleExactAlarms(context) -> exactAlarmIntent(context)
        !canUseFullScreen(context) -> fullScreenIntent(context)
        !ignoresBatteryOptimizations(context) -> batteryIntent(context)
        else -> null
    }

    /** Whether the setup prompt has anything to ask on this device. */
    fun needsAttention(context: Context): Boolean =
        missingStandardGrant(context) != null || isXiaomi()

    fun openNextSetting(context: Context) {
        val intent = missingStandardGrant(context) ?: vendorIntent(context)
        val opened = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        if (opened.isFailure) {
            context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.canUseFullScreenIntent()
    }

    private fun ignoresBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun exactAlarmIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))

    private fun fullScreenIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))

    @SuppressLint("BatteryLife") // An alarm app is the accepted use of this request.
    private fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    private fun vendorIntent(context: Context): Intent =
        if (isXiaomi()) {
            Intent(MIUI_PERMISSION_EDITOR).putExtra("extra_pkgname", context.packageName)
        } else {
            appDetailsIntent(context)
        }

    private fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
}
