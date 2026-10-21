package app.fitos.personal

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitInput
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.ReminderInput
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.personal.notify.AlarmReminderScheduler
import app.fitos.personal.notify.Notifications
import app.fitos.personal.notify.ReminderAction
import app.fitos.personal.notify.Destination
import app.fitos.personal.notify.ReminderDelivery
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RemindersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = TestApp.container()
    private val data get() = container.data
    private val today = TestApp.today
    private val delivery = ReminderDelivery(context, container)

    @After fun close() = data.close()

    private fun reminder(type: ReminderType, habitId: String? = null, days: Set<DayOfWeek> = emptySet(), enabled: Boolean = true) =
        runBlocking { data.reminders.save(null, ReminderInput(type, habitId, LocalTime.of(20, 0), days, enabled)) }

    @Test
    fun remindersSpeakOnlyWhenSomethingIsLeftToDo() = runBlocking {
        TestApp.completeSetup(container)
        // The setup weigh-in was today.
        assertNull(delivery.message(reminder(ReminderType.WEIGH_IN)))

        val water = reminder(ReminderType.WATER)
        val none = delivery.message(water)!!
        assertEquals("امروز هنوز آبی ثبت نشده.", none.text)
        assertEquals(ReminderAction.Water(250), none.action)
        val target = data.targets.effectiveOn(today).first()!!.waterMl
        data.water.add(today, 1000)
        assertTrue(delivery.message(water)!!.text.startsWith("تا الان ۱ از"))
        data.water.add(today, target - 1000)
        assertNull(delivery.message(water))

        val readId = data.habits.add(HabitInput("مطالعه", null, HabitKind.BOOLEAN, null, HabitFrequency.DAILY, null, null, emptySet()))
        val read = reminder(ReminderType.HABIT, readId)
        val m = delivery.message(read)!!
        assertEquals("مطالعه", m.title)
        assertEquals(ReminderAction.HabitDone(readId), m.action)
        data.habits.setLog(readId, today, HabitStatus.SKIPPED)
        assertNull(delivery.message(read))
        data.habits.setLog(readId, today, HabitStatus.DONE)
        assertNull(delivery.message(read))

        // Not today's weekday (Tuesday), or switched off: quiet.
        assertNull(delivery.message(reminder(ReminderType.WEIGH_IN, days = setOf(DayOfWeek.FRIDAY))))
        assertNull(delivery.message(reminder(ReminderType.BACKUP, enabled = false)))

        val backup = reminder(ReminderType.BACKUP)
        assertEquals("هنوز از اطلاعاتت پشتیبان نگرفته‌ای.", delivery.message(backup)!!.text)
        container.settingsRepository.setLastBackupAt(TestApp.clock.instant())
        assertNull(delivery.message(backup))

        // No program, no workout reminder; with one, it names the next session.
        val workout = reminder(ReminderType.WORKOUT)
        assertNull(delivery.message(workout))
        data.programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet())
        assertTrue(delivery.message(workout)!!.text.startsWith("جلسه‌ی بعد:"))
    }

    @Test
    fun theWeeklyReportReminderSpeaksOnlyWhenTheWeekHasData() = runBlocking {
        TestApp.completeSetup(container)
        val report = reminder(ReminderType.WEEKLY_REPORT)
        // Last week (Saturday 10 to Friday 16 October) has nothing yet; the setup weigh-in is today.
        assertNull(delivery.message(report))
        data.weights.add(90.4, today.minusDays(8), LocalTime.of(7, 30), null, null)
        val m = delivery.message(report)!!
        assertEquals("گزارش هفته آماده است", m.title)
        assertEquals(Destination.REPORT, m.destination)
    }

    @Test
    fun aDeliveredReminderIsANotification() = runBlocking {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TestApp.completeSetup(container)
        val id = reminder(ReminderType.WATER)
        assertTrue(delivery.deliver(id))
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        assertEquals(1, posted.size)
        assertEquals(Notifications.CHANNEL_REMINDERS, posted.single().channelId)
    }

    @Test
    fun theSchedulerKeepsOneAlarmPerActiveReminder() = runBlocking {
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val scheduler = AlarmReminderScheduler(context, data.reminders, data.habits, TestApp.clock)
        val weigh = reminder(ReminderType.WEIGH_IN)
        reminder(ReminderType.WATER, enabled = false)
        val habit = data.habits.add(HabitInput("مدیتیشن", null, HabitKind.BOOLEAN, null, HabitFrequency.DAILY, null, null, emptySet()))
        reminder(ReminderType.HABIT, habit)
        scheduler.sync()
        assertEquals(2, alarms.scheduledAlarms.size)
        // 20:00 today, Tehran time.
        assertEquals(today.atTime(20, 0).atZone(TestApp.zone).toInstant().toEpochMilli(), alarms.scheduledAlarms.minOf { it.triggerAtTime })

        // Archived habits and switched-off reminders lose their alarm.
        data.habits.setArchived(habit, true)
        data.reminders.setEnabled(weigh, false)
        scheduler.sync()
        assertEquals(0, alarms.scheduledAlarms.size)
    }
}
