package app.fitos.personal

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitInput
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.Schedule
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.SetInput
import app.fitos.core.domain.training.VariantKind
import app.fitos.personal.notify.ReminderScheduler
import app.fitos.personal.ui.FitOsRoot
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class HabitsUiTest {

    @get:Rule val compose = createComposeRule()

    private class CountingScheduler : ReminderScheduler {
        var syncs = 0
        override suspend fun sync() {
            syncs++
        }
    }

    private val clock = TestApp.SettableClock()
    private val scheduler = CountingScheduler()
    private val container = TestApp.container(clock = clock, reminderScheduler = scheduler)
    private val data get() = container.data
    private val today = TestApp.today

    private fun launch() {
        TestApp.completeSetup(container)
        clock.now = TestApp.clock.instant()
        compose.setContent { FitOsRoot(themeMode = ThemeMode.LIGHT, container = container, startWithSetup = false) }
        compose.waitForText("سلام، آرش")
    }

    private fun openHabits() {
        compose.onNode(hasText("عادت‌ها") and isSelectable()).performClick()
        compose.waitForText("آب")
    }

    /** The last match, e.g. the "save" of a sheet above a screen that has its own. */
    private fun tapLast(text: String) {
        compose.waitForText(text)
        compose.onAllNodes(hasText(text)).onLast().scrollIfNeeded().performClick()
    }

    @Test
    fun quickAddedHabitsFillThemselvesFromTheLogs() {
        launch()
        openHabits()
        compose.waitForText("هنوز عادتی نساخته‌ای")
        compose.tapText("+ نوشیدن آب")
        compose.waitForText("فهرست روز")
        compose.waitForDescription("افزودن ۲۵۰ میلی‌لیتر آب").performClick()
        compose.waitFor(hasText("خودکار · ۰٫۲۵ از", substring = true))
        runBlocking {
            assertEquals(250, data.water.totals(today, today).first()[today])
            assertEquals(SystemHabit.WATER, data.habits.habits.first().single().system)
        }
        // Home counts it too.
        compose.onNode(hasText("خانه") and isSelectable()).performClick()
        compose.waitForText("عادت‌های امروز")
        compose.waitForText("۰٫۲۵ لیتر")
    }

    @Test
    fun aMissedDayLowersTheAverageByOneDayOnly() {
        // A habit started ten days ago, done every day except one.
        clock.now = today.minusDays(9).atTime(9, 0).atZone(TestApp.zone).toInstant()
        val id = runBlocking { data.habits.add(HabitInput("مطالعه", null, HabitKind.BOOLEAN, null, HabitFrequency.DAILY, null, null, emptySet())) }
        runBlocking {
            (1L..9L).filter { it != 5L }.forEach { data.habits.setLog(id, today.minusDays(it), HabitStatus.DONE) }
        }
        launch()
        openHabits()
        // 8 of 9 finished days; today is not held against you yet.
        compose.waitForText("پایبندی ۸۹٪")
        compose.waitForDescription("علامت انجام «مطالعه»").performClick()
        compose.waitForText("پایبندی ۹۰٪")
        runBlocking { assertEquals(HabitStatus.DONE, data.habits.logs(today, today).first().single().status) }
        // A second tap takes the answer back.
        compose.waitForDescription("برداشتن علامت «مطالعه»").performClick()
        compose.waitForDescription("علامت انجام «مطالعه»")
        runBlocking { assertTrue(data.habits.logs(today, today).first().isEmpty()) }
    }

    @Test
    fun anOwnAmountHabitWithAReminder() {
        launch()
        openHabits()
        compose.tapText("عادت جدید")
        compose.field("نام عادت").performTextInput("مطالعه")
        compose.tapText("مقدار")
        compose.field("هدف روزانه").performTextInput("10")
        compose.field("واحد (مثلاً صفحه)").performTextInput("صفحه")
        compose.tapText("افزودن زمان")
        compose.waitForText("زمان یادآور")
        tapLast("ذخیره")
        compose.waitForText("۲۰:۰۰ · هر روز")
        tapLast("ذخیره")

        compose.waitForText("فهرست روز")
        compose.waitForText("مطالعه")
        runBlocking {
            val habit = data.habits.habits.first().single()
            assertEquals(10.0, habit.target!!, 1e-9)
            val reminder = data.reminders.list().single()
            assertEquals(ReminderType.HABIT, reminder.type)
            assertEquals(habit.id, reminder.habitId)
            assertEquals(LocalTime.of(20, 0), reminder.time)
        }
        assertTrue(scheduler.syncs > 0)

        compose.tapText("مطالعه")
        compose.field("مقدار (صفحه)").performTextInput("4")
        tapLast("ذخیره")
        compose.waitForText("۴ از ۱۰ صفحه")
    }

    @Test
    fun sleepAndActivityAreLogged() {
        launch()
        openHabits()
        compose.tapText("ثبت خواب")
        compose.waitForText("مدت: ۸ ساعت")
        tapLast("ذخیره")
        compose.waitFor(hasText("از ۲۳:۰۰ تا ۷:۰۰", substring = true))
        runBlocking { assertEquals(480, data.sleep.entries(today, today).first().single().durationMinutes) }

        compose.tapText("ثبت فعالیت")
        compose.field("مدت (دقیقه)").performTextInput("30")
        tapLast("ذخیره")
        compose.waitForText("پیاده‌روی · ۳۰ دقیقه")
    }

    @Test
    fun archiveKeepsHistoryAndDeleteAsksFirst() {
        runBlocking { data.habits.add(HabitInput("مدیتیشن", null, HabitKind.BOOLEAN, null, HabitFrequency.DAILY, null, null, emptySet())) }
        launch()
        openHabits()
        compose.tapText("مدیتیشن")
        compose.tapText("جزئیات و سابقه")
        compose.waitForText("۵ هفته‌ی اخیر")
        compose.tapText("ویرایش")
        compose.tapText("بایگانی")
        compose.waitForText("خروج از بایگانی")
        runBlocking { assertTrue(data.habits.habits.first().single().archived) }

        compose.tapText("حذف عادت")
        compose.waitForText("«مدیتیشن» حذف شود؟")
        tapLast("حذف")
        compose.waitForText("هنوز عادتی نساخته‌ای")
        runBlocking { assertTrue(data.habits.habits.first().isEmpty()) }
    }

    @Test
    fun remindersAreSetPerTypeAndTheBreakDaysCanChange() {
        launch()
        compose.waitForDescription("تنظیمات").performClick()
        compose.tapText("یادآورها و برگشت به برنامه")
        compose.waitForText("وزن‌کشی")
        compose.onAllNodes(hasText("افزودن زمان"))[0].performClick()
        compose.waitForText("زمان یادآور")
        tapLast("ذخیره")
        compose.waitForText("۷:۳۰ · هر روز")
        runBlocking {
            val r = data.reminders.list().single()
            assertEquals(ReminderType.WEIGH_IN, r.type)
            assertTrue(r.enabled)
        }
        compose.waitFor(hasContentDescription("یادآور ۷:۳۰ · هر روز")).performClick()
        compose.waitUntil(5_000) { runBlocking { !data.reminders.list().single().enabled } }

        compose.waitForDescription("زیاد کردن").scrollIfNeeded().performClick()
        compose.waitForText("۵ روز")
        runBlocking { assertEquals(5, container.settingsRepository.returnThresholdDays.first()) }
    }

    @Test
    fun afterABreakTheNextSessionsCanBeLighter() {
        // Today's weekday session was last done six days ago, with one logged set to compare against.
        val programId = runBlocking { data.programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet()) }
        runBlocking { data.programs.setScheduleMode(programId, ScheduleMode.WEEKDAYS) }
        clock.now = today.minusDays(6).atTime(12, 0).atZone(TestApp.zone).toInstant()
        runBlocking {
            val day = Schedule.next(data.programs.program(programId).first()!!, null, today)!!
            val id = data.workouts.start(programId, day.id, VariantKind.FULL)
            val first = data.workouts.session(id).first()!!.exercises.first()
            data.workouts.logSet(first.id, SetInput(weightKg = 20.0, reps = 10))
            data.workouts.finish(id, null, null)
        }
        launch()
        compose.waitForText("۵ روز تمرینی ثبت نشده")
        compose.tapText("دو جلسه‌ی سبک‌تر")
        compose.waitUntil(5_000) { runBlocking { data.returnPlan.latest.first()?.choice == ReturnChoice.LIGHTER } }

        compose.onNode(hasText("تمرین") and isSelectable()).performClick()
        compose.waitFor(hasContentDescription("برگشت آرام · جلسه‌ی ۱ از ۲", substring = true))
        compose.tapText("شروع جلسه‌ی سبک‌تر")
        compose.waitForText("برگشت آرام بعد از وقفه: حدود ۱۰٪ سبک‌تر از معمول.")
        runBlocking {
            val session = data.workouts.active.first()!!
            val planned = data.programs.program(programId).first()!!.days.first { it.id == session.programDayId }.full
            session.exercises.zip(planned).forEach { (e, p) -> assertEquals(if (p.sets >= 3) p.sets - 1 else p.sets, e.targets.sets) }
            assertFalse(session.exercises.isEmpty())
        }
    }
}
