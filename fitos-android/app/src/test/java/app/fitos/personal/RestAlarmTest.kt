package app.fitos.personal

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.personal.feature.session.AndroidRestAlarm
import app.fitos.personal.feature.session.RestDoneReceiver
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RestAlarmTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)

    @Test fun countdownNotificationAndAlarmAtTheEndOfRest() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val end = Instant.now().plusSeconds(90)
        AndroidRestAlarm(context).schedule(end, "پرس سینه با هالتر")

        val running = shadowOf(notifications).getNotification(AndroidRestAlarm.ID_RUNNING)
        assertNotNull(running)
        assertEquals(AndroidRestAlarm.CHANNEL_RUNNING, running.channelId)
        assertEquals(end.toEpochMilli(), running.`when`)
        val alarm = shadowOf(alarms).nextScheduledAlarm!!
        assertEquals(end.toEpochMilli(), alarm.triggerAtTime)

        // The alarm fires: the countdown goes, the alert comes.
        RestDoneReceiver().onReceive(context, Intent(context, RestDoneReceiver::class.java).putExtra(AndroidRestAlarm.EXTRA_NEXT, "پرس سینه با هالتر"))
        assertNull(shadowOf(notifications).getNotification(AndroidRestAlarm.ID_RUNNING))
        val done = shadowOf(notifications).getNotification(AndroidRestAlarm.ID_DONE)
        assertEquals(AndroidRestAlarm.CHANNEL_DONE, done.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notifications.getNotificationChannel(AndroidRestAlarm.CHANNEL_DONE).importance)
    }

    @Test fun cancelRemovesTheAlarmAndTheNotifications() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val alarm = AndroidRestAlarm(context)
        alarm.schedule(Instant.now().plusSeconds(60), "اسکات")
        alarm.cancel()
        assertNull(shadowOf(alarms).nextScheduledAlarm)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test fun withoutPermissionOnlyTheAlarmIsSet() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidRestAlarm(context).schedule(Instant.now().plusSeconds(60), "اسکات")
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        assertNotNull(shadowOf(alarms).nextScheduledAlarm)
    }
}
