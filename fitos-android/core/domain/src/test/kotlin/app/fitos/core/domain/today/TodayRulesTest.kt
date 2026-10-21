package app.fitos.core.domain.today

import app.fitos.core.domain.habits.DayResult
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.training.CompletedDay
import app.fitos.core.domain.training.DayVariant
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramDay
import app.fitos.core.domain.training.Schedule
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.VariantKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayRulesTest {
    // Wednesday 21 Oct 2026.
    private val today = LocalDate.of(2026, 10, 21)

    private fun habit(id: String, system: SystemHabit? = null, frequency: HabitFrequency = HabitFrequency.DAILY, kind: HabitKind = HabitKind.BOOLEAN) =
        Habit(id, id, null, kind, system, frequency, null, null, emptySet(), 0, false, today.minusDays(30))
    private fun result(outcome: Outcome, value: Double? = null, target: Double? = null) = DayResult(outcome, value, target, auto = false)

    private val input = DayScoreInput(
        hasProgram = true, trainingPlanned = true, trainedVariant = null, trained = false,
        kcal = 2500.0, kcalTarget = 2500, proteinG = 180.0, proteinTargetG = 180,
        waterMl = 3000, waterTargetMl = 3000, sleepMinutes = 450, sleepTargetMinutes = 450, habits = emptyList(),
    )

    @Test fun caloriesFullInsideTheBandProportionalBelowFallingAbove() {
        assertEquals(1.0, DayScoreRules.calories(2250.0, 2500), 1e-9) // exactly -10%
        assertEquals(1.0, DayScoreRules.calories(2750.0, 2500), 1e-9) // exactly +10%
        assertEquals(1125.0 / 2250.0, DayScoreRules.calories(1125.0, 2500), 1e-9)
        assertEquals(0.5, DayScoreRules.calories(3250.0, 2500), 1e-9) // halfway from +10% to +50%
        assertEquals(0.0, DayScoreRules.calories(4000.0, 2500), 1e-9)
    }

    @Test fun averageOfItemsThatCountAndUnloggedIsZero() {
        // Everything on target except training (planned, not done) and no habits.
        val s = DayScoreRules.score(input)
        assertEquals(5, s.counted.size)
        assertEquals(80, s.percent)
        val training = s.items.first { it.item == DayItem.TRAINING }
        assertEquals(0.0, training.score!!, 0.0)
        assertFalse(training.logged)
        val noWater = DayScoreRules.score(input.copy(waterMl = null)).items.first { it.item == DayItem.WATER }
        assertEquals(0.0, noWater.score!!, 0.0)
        assertFalse(noWater.logged)
    }

    @Test fun restDayLeavesTrainingOutAndAnyVersionCountsAsDone() {
        val rest = DayScoreRules.score(input.copy(trainingPlanned = false))
        assertEquals(NotCounted.REST_DAY, rest.items.first { it.item == DayItem.TRAINING }.notCounted)
        assertEquals(100, rest.percent)
        val noProgram = DayScoreRules.score(input.copy(hasProgram = false, trainingPlanned = false))
        assertEquals(NotCounted.NO_PROGRAM, noProgram.items.first { it.item == DayItem.TRAINING }.notCounted)
        assertEquals(100, noProgram.percent)
        val minimal = DayScoreRules.score(input.copy(trained = true, trainedVariant = VariantKind.MINIMAL)).items.first { it.item == DayItem.TRAINING }
        assertEquals(1.0, minimal.score!!, 0.0)
        assertEquals(VariantKind.MINIMAL, minimal.variant)
        // Training on a rest day still counts, as done.
        assertEquals(1.0, DayScoreRules.score(input.copy(trainingPlanned = false, trained = true)).items.first { it.item == DayItem.TRAINING }.score!!, 0.0)
    }

    @Test fun missingTargetsDoNotCount() {
        val s = DayScoreRules.score(input.copy(kcalTarget = null, waterTargetMl = null))
        assertNull(s.items.first { it.item == DayItem.CALORIES }.score)
        assertEquals(NotCounted.NO_TARGET, s.items.first { it.item == DayItem.WATER }.notCounted)
        assertEquals(3, s.counted.size)
    }

    @Test fun habitsAverageOwnDailyHabitsOnly() {
        val habits = listOf(
            habit("read") to result(Outcome.DONE),
            habit("pages", kind = HabitKind.QUANTITY) to result(Outcome.PARTIAL, 4.0, 10.0),
            habit("stretch") to result(Outcome.NOT_LOGGED),
            habit("off") to result(Outcome.SKIPPED),
            habit("water", SystemHabit.WATER, kind = HabitKind.QUANTITY) to result(Outcome.NOT_LOGGED),
            habit("weekly", frequency = HabitFrequency.WEEKLY) to result(Outcome.NOT_LOGGED),
        )
        val h = DayScoreRules.score(input.copy(habits = habits)).items.first { it.item == DayItem.HABITS }
        assertEquals(3, h.total)
        assertEquals(1, h.done)
        assertEquals((1.0 + 0.4 + 0.0) / 3, h.score!!, 1e-9)
        assertEquals(NotCounted.NO_HABITS, DayScoreRules.score(input).items.first { it.item == DayItem.HABITS }.notCounted)
    }

    @Test fun nothingCountsGivesZero() {
        val empty = DayScoreInput(false, false, null, false, null, null, null, null, null, null, null, null, emptyList())
        assertEquals(0, DayScoreRules.score(empty).percent)
    }

    // ---- training day ----

    private fun day(id: String, rest: Boolean = false, weekday: DayOfWeek? = null) =
        ProgramDay(id, 0, id, rest, weekday, null, if (rest) emptyList() else listOf(DayVariant("v-$id", VariantKind.FULL, null, emptyList())))

    @Test fun weekdayProgramsTrainOnTheirWeekdays() {
        val p = Program("p", "p", null, true, ScheduleMode.WEEKDAYS, listOf(day("a", weekday = DayOfWeek.WEDNESDAY), day("b", weekday = DayOfWeek.SATURDAY), day("r", rest = true, weekday = DayOfWeek.THURSDAY)), false)
        assertTrue(Schedule.isTrainingDay(p, null, today))
        assertFalse(Schedule.isTrainingDay(p, null, today.plusDays(1)))
        assertTrue(Schedule.isTrainingDay(p, null, today.plusDays(3)))
    }

    @Test fun sequenceRestsOnlyRightAfterASessionAndNeverSkipsAMissedOne() {
        val p = Program("p", "p", null, true, ScheduleMode.SEQUENCE, listOf(day("a"), day("r1", rest = true), day("b"), day("r2", rest = true), day("r3", rest = true)), false)
        assertTrue(Schedule.isTrainingDay(p, null, today))
        // A done yesterday: one rest day follows, so today rests and tomorrow trains.
        assertFalse(Schedule.isTrainingDay(p, CompletedDay("a", today.minusDays(1)), today))
        assertTrue(Schedule.isTrainingDay(p, CompletedDay("a", today.minusDays(2)), today))
        // B was done two days ago and two rest days follow it.
        assertFalse(Schedule.isTrainingDay(p, CompletedDay("b", today.minusDays(2)), today))
        assertTrue(Schedule.isTrainingDay(p, CompletedDay("b", today.minusDays(3)), today))
        // Trained today: today counts as a training day.
        assertTrue(Schedule.isTrainingDay(p, CompletedDay("a", today), today))
    }

    // ---- next action ----

    private val facts = NextFacts(
        time = LocalTime.of(8, 0), sessionOpen = false, tracksWeight = true, weighedToday = false, sleepLogged = false,
        trainingPlanned = true, trainedToday = false, foodEntries = 0, proteinG = null, proteinTargetG = 180,
        waterMl = 0, waterTargetMl = 3000, openHabits = emptyList(),
    )

    @Test fun morningGoesWeightThenSleep() {
        assertEquals(NextAction.LogWeight, NextActionRules.pick(facts))
        assertEquals(NextAction.LogSleep, NextActionRules.pick(facts.copy(weighedToday = true)))
        // Without a weight routine the weigh-in is not pushed.
        assertEquals(NextAction.LogSleep, NextActionRules.pick(facts.copy(tracksWeight = false)))
        assertEquals(NextAction.ResumeSession, NextActionRules.pick(facts.copy(sessionOpen = true)))
    }

    @Test fun thenWorkoutFoodWaterHabitsAndDone() {
        val done = facts.copy(weighedToday = true, sleepLogged = true)
        assertEquals(NextAction.StartWorkout, NextActionRules.pick(done.copy(time = LocalTime.of(10, 0))))
        val trained = done.copy(trainedToday = true, time = LocalTime.of(10, 0))
        assertEquals(NextAction.LogFood(proteinBehind = false), NextActionRules.pick(trained))
        val ate = trained.copy(foodEntries = 2, proteinG = 60.0, time = LocalTime.of(16, 0))
        assertEquals(NextAction.LogFood(proteinBehind = true), NextActionRules.pick(ate))
        val fed = ate.copy(proteinG = 120.0)
        // By 4 pm, 9 of the 14 waking hours have passed: 1928 ml expected.
        assertEquals(NextAction.DrinkWater(1928), NextActionRules.pick(fed))
        val habit = habit("read")
        val drank = fed.copy(waterMl = 1800, openHabits = listOf(habit))
        assertEquals(NextAction.DoHabit(habit), NextActionRules.pick(drank))
        assertEquals(NextAction.AllDone, NextActionRules.pick(drank.copy(openHabits = emptyList())))
        // Too late to start a workout.
        assertEquals(NextAction.AllDone, NextActionRules.pick(drank.copy(openHabits = emptyList(), trainedToday = false, time = LocalTime.of(22, 0), waterMl = 3000)))
    }

    @Test fun waterExpectationSpreadsOverTheWakingDay() {
        assertEquals(0, NextActionRules.waterExpected(LocalTime.of(6, 0), 3000))
        assertEquals(1500, NextActionRules.waterExpected(LocalTime.of(14, 0), 3000))
        assertEquals(3000, NextActionRules.waterExpected(LocalTime.of(23, 0), 3000))
    }
}
