package app.fitos.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.habits.ActivityInput
import app.fitos.core.domain.habits.ActivityKind
import app.fitos.core.domain.habits.HabitBoard
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitInput
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.ReminderInput
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.VariantKind
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HabitRepositoriesTest {

    private val today = TestData.today
    private val data = DataGraph.inMemory(ApplicationProvider.getApplicationContext<Context>(), TestData.clockAt(today), FakeSettings())

    @After fun close() = data.close()

    private fun input(name: String, kind: HabitKind = HabitKind.BOOLEAN, system: SystemHabit? = null, frequency: HabitFrequency = HabitFrequency.DAILY, target: Double? = null) =
        HabitInput(name, null, kind, system, frequency, target, null, emptySet())

    @Test fun habitsKeepTheirOrderAndCanBeArchivedDeletedAndRestored() = runBlocking {
        val a = data.habits.add(input("  یک  "))
        val b = data.habits.add(input("دو").copy(weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)))
        val c = data.habits.add(input("سه"))
        assertEquals(listOf("یک", "دو", "سه"), data.habits.habits.first().map { it.name })
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), data.habits.habits.first()[1].weekdays)
        assertEquals(today, data.habits.habits.first()[0].createdOn)

        data.habits.move(c, -2)
        assertEquals(listOf(c, a, b), data.habits.habits.first().map { it.id })

        data.habits.setArchived(a, true)
        assertTrue(data.habits.habits.first().first { it.id == a }.archived)
        data.habits.update(a, input("یک تازه"))
        // Editing keeps the archive flag.
        assertTrue(data.habits.habits.first().first { it.id == a }.archived)

        data.habits.delete(b)
        assertEquals(2, data.habits.habits.first().size)
        data.habits.restore(b)
        assertEquals(3, data.habits.habits.first().size)
    }

    @Test fun oneAnswerPerHabitAndDay() = runBlocking {
        val id = data.habits.add(input("کتاب", HabitKind.QUANTITY, target = 10.0))
        data.habits.setLog(id, today, HabitStatus.NOT_DONE, 4.0)
        data.habits.setLog(id, today, HabitStatus.DONE, 12.0)
        data.habits.setLog(id, today.minusDays(1), HabitStatus.SKIPPED)
        val logs = data.habits.logs(today.minusDays(6), today).first()
        assertEquals(2, logs.size)
        assertEquals(12.0, logs.first { it.day == today }.value!!, 1e-9)
        data.habits.clearLog(id, today)
        assertEquals(listOf(today.minusDays(1)), data.habits.logs(today.minusDays(6), today).first().map { it.day })
    }

    @Test fun waterSleepAndActivity() = runBlocking {
        val glass = data.water.add(today, 250)
        data.water.add(today, 500)
        data.water.add(today.minusDays(1), 1000)
        assertEquals(mapOf(today to 750, today.minusDays(1) to 1000), data.water.totals(today.minusDays(6), today).first())
        data.water.delete(glass)
        assertEquals(500, data.water.totals(today, today).first()[today])
        data.water.restore(glass)
        assertEquals(2, data.water.entries(today).first().size)

        data.sleep.save(today, LocalTime.of(23, 0), LocalTime.of(6, 30), 3, null)
        data.sleep.save(today, LocalTime.of(23, 30), LocalTime.of(7, 0), 4, " خوب ")
        val nights = data.sleep.entries(today, today).first()
        assertEquals(1, nights.size)
        assertEquals(450, nights.single().durationMinutes)
        assertEquals(today.minusDays(1), nights.single().bedtime!!.toLocalDate())
        assertEquals("خوب", nights.single().note)

        val walk = data.activity.add(today, ActivityInput(ActivityKind.WALK, 4000, 30, null))
        data.activity.update(walk, ActivityInput(ActivityKind.WALK, 5000, 40, "پارک"))
        assertEquals(40, data.activity.entries(today, today).first().single().durationMinutes)
    }

    @Test fun remindersKeepTheirTimeAndDays() = runBlocking {
        val id = data.reminders.save(null, ReminderInput(ReminderType.WEIGH_IN, null, LocalTime.of(7, 15), setOf(DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY)))
        val everyDay = data.reminders.save(null, ReminderInput(ReminderType.WATER, null, LocalTime.of(10, 0), emptySet()))
        val saved = data.reminders.get(id)!!
        assertEquals(LocalTime.of(7, 15), saved.time)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY), saved.weekdays)
        assertTrue(data.reminders.get(everyDay)!!.weekdays.isEmpty())
        data.reminders.setEnabled(id, false)
        assertFalse(data.reminders.get(id)!!.enabled)
        data.reminders.save(id, ReminderInput(ReminderType.WEIGH_IN, null, LocalTime.of(8, 0), emptySet(), enabled = true))
        assertEquals(LocalTime.of(8, 0), data.reminders.get(id)!!.time)
        data.reminders.delete(id)
        assertNull(data.reminders.get(id))
        assertEquals(1, data.reminders.list().size)
    }

    @Test fun returnEventsRecordTheChoice() = runBlocking {
        val event = data.returnPlan.open(6)
        assertNull(data.returnPlan.latest.first()!!.choice)
        data.returnPlan.choose(event.id, ReturnChoice.LIGHTER)
        val latest = data.returnPlan.latest.first()!!
        assertEquals(ReturnChoice.LIGHTER, latest.choice)
        assertEquals(6, latest.gapDays)
    }

    @Test fun systemHabitsReadTheOtherLogs() = runBlocking {
        data.setup.completeSetup(TestData.setup())
        val target = data.targets.effectiveOn(today).first()!!
        data.water.add(today, 2000)
        data.sleep.save(today, LocalTime.of(23, 0), LocalTime.of(7, 0), null, null)
        data.activity.add(today, ActivityInput(ActivityKind.HOME_WORKOUT, null, 15, null))
        val programId = data.programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet())
        val day = data.programs.program(programId).first()!!.trainingDays.first()
        data.workouts.finish(data.workouts.start(programId, day.id, VariantKind.FULL), null, null)

        val facts = data.dayFacts.facts(today.minusDays(1), today).first()
        val f = facts.getValue(today)
        assertEquals(2000, f.waterMl)
        assertEquals(target.waterMl, f.waterTargetMl)
        assertEquals(480, f.sleepMinutes)
        assertTrue(f.trained)
        assertTrue(f.homeWorkout)
        // The setup weigh-in is today.
        assertTrue(f.weighed)
        assertNull(f.kcal)
        assertEquals(15, f.activityMinutes)
        assertNull(facts.getValue(today.minusDays(1)).waterMl)

        val water = data.habits.add(input("آب", HabitKind.QUANTITY, SystemHabit.WATER))
        val habit = data.habits.habits.first().single { it.id == water }
        val results = HabitBoard.results(habit, emptyList(), facts, today.minusDays(1), today)
        assertEquals(if (2000 >= target.waterMl) Outcome.DONE else Outcome.PARTIAL, results.getValue(today).outcome)
    }
}
