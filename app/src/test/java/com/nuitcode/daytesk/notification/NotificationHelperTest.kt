package com.nuitcode.daytesk.notification

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Strict TDD — RED tests for the alarm channel, written before
 * [NotificationHelper.CHANNEL_ID_ALARM] exists.
 *
 * Contract (see sdd/alarm-reminders/spec, "Alarm channel for recordatorio
 * reminders"): a NEW channel id is required because notification channels are
 * immutable after creation; the task channel must survive untouched.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("daytesk_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun createNotificationChannel_registersTheAlarmChannelWithAlarmSoundAndVibration() {
        NotificationHelper.createNotificationChannel(context)

        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID_ALARM)

        assertNotNull("the alarm channel must be created", channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue("the alarm channel must vibrate", channel.shouldVibrate())
        assertNotNull("the alarm channel must own an alarm sound", channel.sound)
        assertEquals(
            "the channel sound must use alarm audio attributes",
            AudioAttributes.USAGE_ALARM,
            channel.audioAttributes.usage,
        )
    }

    @Test
    fun createNotificationChannel_keepsTheTaskChannel() {
        NotificationHelper.createNotificationChannel(context)

        assertNotNull(
            "task reminders must keep their channel",
            manager.getNotificationChannel(NotificationHelper.CHANNEL_ID),
        )
    }

    @Test
    fun channelIds_areDistinct() {
        assertEquals("daytesk_alarm_reminders", NotificationHelper.CHANNEL_ID_ALARM)
        assertNotEquals(NotificationHelper.CHANNEL_ID, NotificationHelper.CHANNEL_ID_ALARM)
    }
}
