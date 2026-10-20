package app.fitos.core.domain.habits

import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.ReturnEvent
import app.fitos.core.domain.training.ReturnPlan
import app.fitos.core.domain.training.Suggestion
import app.fitos.core.domain.training.SuggestionKind
import app.fitos.core.domain.training.SuggestionReason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRulesTest {

    // Tuesday 20 Oct 2026; the Persian week started on Saturday 17 Oct.
    private val today = LocalDate.of(2026, 10, 20)
    private val saturday = LocalDate.of(2026, 10, 17)

    private fun habit(
        kind: HabitKind = HabitKind.BOOLEAN,
        system: SystemHabit? = null,
        frequency: HabitFrequency = HabitFrequency.DAILY,
        target: Double? = null,
        unit: String? = null,
        weekdays: Set<DayOfWeek> = emptySet(),
        createdOn: LocalDate = today.minusDays(60),
    ) = Habit("h", "عادت", null, kind, system, frequency, target, unit, weekdays, 0, false, createdOn)

    private fun done() = DayResult(Outcome.DONE, null, null, auto = false)
    private fun missed() = DayResult(Outcome.NOT_DONE, null, null, auto = false)
    private fun skipped() = DayResult(Outcome.SKIPPED, null, null, auto = false)

    @Test fun dueDaysFollowWeekdaysCreationAndArchive() {
        val h = habit(weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY), createdOn = saturday)
        assertTrue(HabitRules.isDue(h, today))
        assertFalse(HabitRules.isDue(h, today.minusDays(1)))
        assertFalse(HabitRules.isDue(h, saturday.minusDays(4)))
        assertFalse(HabitRules.isDue(h.copy(archived = true), today))
        // Weekly habits may count on any day.
        assertTrue(HabitRules.isDue(habit(frequency = HabitFrequency.WEEKLY, target = 3.0), today.minusDays(1)))
    }

    @Test fun answersWinAndSystemHabitsReadTheLogs() {
        val water = habit(HabitKind.QUANTITY, SystemHabit.WATER)
        val facts = DayFacts(waterMl = 1500, waterTargetMl = 2500)
        val auto = HabitRules.day(water, null, facts)
        assertEquals(Outcome.PARTIAL, auto.outcome)
        assertTrue(auto.auto)
        assertEquals(0.6, auto.score!!, 1e-9)
        assertEquals(Outcome.DONE, HabitRules.day(water, null, facts.copy(waterMl = 2600)).outcome)
        assertEquals(Outcome.NOT_LOGGED, HabitRules.day(water, null, DayFacts(waterTargetMl = 2500)).outcome)
        // A skipped day by hand overrides the logs.
        assertEquals(Outcome.SKIPPED, HabitRules.day(water, HabitLog("h", today, HabitStatus.SKIPPED, null), facts).outcome)

        val steps = habit(HabitKind.QUANTITY, SystemHabit.ACTIVITY, target = 7000.0, unit = ActivityUnit.STEPS)
        assertEquals(Outcome.DONE, HabitRules.day(steps, null, DayFacts(steps = 8000, activityMinutes = 10)).outcome)
        assertEquals(Outcome.NOT_LOGGED, HabitRules.day(habit(), null, DayFacts(trained = true)).outcome)
        assertEquals(Outcome.DONE, HabitRules.day(habit(system = SystemHabit.WEIGH_IN), null, DayFacts(weighed = true)).outcome)
    }

    @Test fun nutritionNeedsCaloriesInTheBandAndEnoughProtein() {
        val h = habit(system = SystemHabit.NUTRITION)
        val base = DayFacts(kcal = 2100.0, proteinG = 160.0, kcalTarget = 2200, proteinTargetG = 170)
        assertEquals(Outcome.DONE, HabitRules.day(h, null, base).outcome)
        // 2450 is more than 10 % over.
        assertEquals(Outcome.NOT_DONE, HabitRules.day(h, null, base.copy(kcal = 2450.0)).outcome)
        // 150 g is below 90 % of 170 g.
        assertEquals(Outcome.NOT_DONE, HabitRules.day(h, null, base.copy(proteinG = 150.0)).outcome)
        assertEquals(Outcome.NOT_LOGGED, HabitRules.day(h, null, base.copy(kcal = null)).outcome)
    }

    @Test fun todayCountsOnlyOnceSettled() {
        assertTrue(HabitRules.counts(done(), isToday = true))
        assertTrue(HabitRules.counts(missed(), isToday = true))
        assertFalse(HabitRules.counts(DayResult(Outcome.NOT_LOGGED, null, null, false), isToday = true))
        assertFalse(HabitRules.counts(DayResult(Outcome.PARTIAL, 1.0, 2.0, true), isToday = true))
        assertTrue(HabitRules.counts(DayResult(Outcome.NOT_LOGGED, null, null, false), isToday = false))
        assertFalse(HabitRules.counts(skipped(), isToday = false))
    }

    @Test fun oneMissedDayWeighsOneDayNotTheWholeRun() {
        val h = habit(createdOn = today.minusDays(6))
        // Six days done, one missed day in the middle, today not answered yet.
        val results = (1L..6L).associate { today.minusDays(it) to done() } + (today.minusDays(3) to missed())
        val a = HabitRules.adherence(h, today.minusDays(27), today, today, results)
        assertEquals(6, a.counted)
        assertEquals(5.0 / 6, a.score!!, 1e-9)
        assertEquals(5, a.full)
        // A skipped day leaves the average.
        val withSkip = HabitRules.adherence(h, today.minusDays(27), today, today, results + (today.minusDays(3) to skipped()))
        assertEquals(1.0, withSkip.score!!, 1e-9)
        assertEquals(5, withSkip.counted)
        // Nothing to judge before the first day ends.
        assertNull(HabitRules.adherence(habit(createdOn = today), today, today, today, emptyMap()).score)
    }

    @Test fun partOfADailyAmountCountsAsPartOfTheDay() {
        val h = habit(HabitKind.QUANTITY, target = 10.0, createdOn = today.minusDays(2))
        val results = mapOf(
            today.minusDays(2) to HabitRules.day(h, HabitLog("h", today.minusDays(2), HabitStatus.NOT_DONE, 5.0), null),
            today.minusDays(1) to HabitRules.day(h, HabitLog("h", today.minusDays(1), HabitStatus.DONE, 10.0), null),
        )
        assertEquals(0.75, HabitRules.adherence(h, today.minusDays(6), today, today, results).score!!, 1e-9)
    }

    @Test fun weeklyHabitsAreJudgedPerWeek() {
        val h = habit(frequency = HabitFrequency.WEEKLY, target = 3.0, createdOn = saturday.minusWeeks(2))
        val lastWeek = saturday.minusWeeks(1)
        val results = mapOf(
            lastWeek to done(), lastWeek.plusDays(2) to done(),
            saturday.minusWeeks(2) to done(), saturday.minusWeeks(2).plusDays(1) to done(), saturday.minusWeeks(2).plusDays(3) to done(),
            saturday to done(),
        )
        val week = HabitRules.week(h, lastWeek, results, today)
        assertEquals(2, week.done)
        assertEquals(3, week.needed)
        assertTrue(week.complete)
        // This week: 1 of 3 so far, still running.
        assertFalse(HabitRules.week(h, saturday, results, today).complete)
        // Two finished weeks: 100 % and 67 %; the running week is left out until it is met.
        val a = HabitRules.adherence(h, saturday.minusWeeks(4), today, today, results)
        assertEquals(2, a.counted)
        assertEquals((1.0 + 2.0 / 3) / 2, a.score!!, 1e-9)

        // Five skipped days leave two days, so two times are enough.
        val sick = (0L..4L).associate { lastWeek.plusDays(it) to skipped() } + (lastWeek.plusDays(5) to done()) + (lastWeek.plusDays(6) to done())
        assertEquals(1.0, HabitRules.week(h, lastWeek, sick, today).score!!, 1e-9)
        // Started on Thursday with one day left: one time is enough that week.
        val late = h.copy(createdOn = lastWeek.plusDays(6))
        assertEquals(1, HabitRules.week(late, lastWeek, emptyMap(), today).needed)
    }

    @Test fun trendHasOnePointPerWeekWithTheRunningOneMarked() {
        val h = habit(createdOn = saturday.minusWeeks(1))
        val results = (1L..10L).associate { today.minusDays(it) to done() }
        val trend = HabitRules.trend(h, 8, today, results)
        assertEquals(8, trend.size)
        assertEquals(saturday, trend.last().weekStart)
        assertTrue(trend.last().inProgress)
        assertNull(trend.first().score)
        assertEquals(1.0, trend[6].score!!, 1e-9)
    }

    @Test fun weeklyHabitsLeaveTodaysListOnceMet() {
        val h = habit(frequency = HabitFrequency.WEEKLY, target = 2.0)
        val met = WeekResult(saturday, 2, 2, true)
        val notLogged = DayResult(Outcome.NOT_LOGGED, null, null, false)
        assertFalse(HabitRules.listed(h, today, notLogged, met))
        assertTrue(HabitRules.listed(h, today, done(), met))
        assertTrue(HabitRules.listed(h, today, notLogged, WeekResult(saturday, 1, 2, false)))
    }

    @Test fun habitsAreChecked() {
        val ok = HabitInput("کتاب", null, HabitKind.QUANTITY, null, HabitFrequency.DAILY, 10.0, "صفحه", emptySet())
        assertTrue(HabitRules.check(ok).isEmpty())
        assertTrue(HabitRules.Problem.NAME in HabitRules.check(ok.copy(name = " ")))
        assertTrue(HabitRules.Problem.TARGET in HabitRules.check(ok.copy(target = null)))
        assertTrue(HabitRules.Problem.WEEKLY_TIMES in HabitRules.check(ok.copy(kind = HabitKind.BOOLEAN, frequency = HabitFrequency.WEEKLY, target = 9.0)))
        // Water follows the daily target, so it needs none of its own.
        assertTrue(HabitRules.check(ok.copy(system = SystemHabit.WATER, target = null)).isEmpty())
    }

    @Test fun aNightCanStartTheEveningBefore() {
        val (bed, wake, minutes) = LifestyleRules.night(today, LocalTime.of(23, 30), LocalTime.of(7, 0))
        assertEquals(today.minusDays(1), bed.toLocalDate())
        assertEquals(today, wake.toLocalDate())
        assertEquals(450, minutes)
        assertEquals(90, LifestyleRules.night(today, LocalTime.of(14, 0), LocalTime.of(15, 30)).third)
    }

    @Test fun remindersFireOnTheirDaysOnly() {
        val zone = ZoneId.of("Asia/Tehran")
        val r = Reminder("r", ReminderType.WEIGH_IN, null, LocalTime.of(7, 30), setOf(DayOfWeek.SATURDAY, DayOfWeek.WEDNESDAY), true)
        val now = today.atTime(8, 0).atZone(zone)
        assertEquals(today.plusDays(1).atTime(7, 30).atZone(zone), ReminderRules.nextFire(r, now))
        assertEquals(today.plusDays(4).atTime(7, 30).atZone(zone), ReminderRules.nextFire(r, now.plusDays(1).withHour(7).withMinute(30)))
        val daily = r.copy(weekdays = emptySet())
        assertEquals(today.atTime(7, 30).atZone(zone), ReminderRules.nextFire(daily, today.atTime(6, 0).atZone(zone)))
    }

    @Test fun remindersStayQuietWhenThereIsNothingToDo() {
        val now = Instant.parse("2026-10-20T10:00:00Z")
        assertFalse(ReminderRules.shouldNotify(ReminderType.HABIT, ReminderFacts(now, habitToday = done())))
        assertFalse(ReminderRules.shouldNotify(ReminderType.HABIT, ReminderFacts(now, habitToday = null)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.HABIT, ReminderFacts(now, habitToday = missed(), habitWeekMet = true)))
        assertTrue(ReminderRules.shouldNotify(ReminderType.HABIT, ReminderFacts(now, habitToday = DayResult(Outcome.NOT_LOGGED, null, null, false))))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WATER, ReminderFacts(now, waterMl = 2500, waterTargetMl = 2500)))
        assertTrue(ReminderRules.shouldNotify(ReminderType.WATER, ReminderFacts(now, waterMl = null, waterTargetMl = 2500)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WEIGH_IN, ReminderFacts(now, weighedToday = true)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WORKOUT, ReminderFacts(now, hasActiveProgram = true, trainedToday = true)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.WORKOUT, ReminderFacts(now, hasActiveProgram = false)))
        assertTrue(ReminderRules.shouldNotify(ReminderType.WORKOUT, ReminderFacts(now, hasActiveProgram = true)))
        assertFalse(ReminderRules.shouldNotify(ReminderType.BACKUP, ReminderFacts(now, lastBackupAt = now.minusSeconds(3 * 86_400))))
        assertTrue(ReminderRules.shouldNotify(ReminderType.BACKUP, ReminderFacts(now, lastBackupAt = now.minusSeconds(8 * 86_400))))
        assertTrue(ReminderRules.shouldNotify(ReminderType.BACKUP, ReminderFacts(now)))
    }

    @Test fun aBreakIsAskedAboutOnce() {
        val lastStart = Instant.parse("2026-10-14T08:00:00Z")
        // Trained on the 14th: the 15th to the 19th are five days without a workout.
        assertEquals(5, ReturnPlan.detect(LocalDate.of(2026, 10, 14), lastStart, null, false, today, 4))
        assertNull(ReturnPlan.detect(LocalDate.of(2026, 10, 14), lastStart, null, false, today, 6))
        assertNull(ReturnPlan.detect(LocalDate.of(2026, 10, 14), lastStart, null, sessionOpen = true, today = today, threshold = 4))
        assertNull(ReturnPlan.detect(null, null, null, false, today, 4))
        val asked = ReturnEvent("e", Instant.parse("2026-10-19T08:00:00Z"), 4, ReturnChoice.DISMISSED, Instant.parse("2026-10-19T08:01:00Z"))
        assertNull(ReturnPlan.detect(LocalDate.of(2026, 10, 14), lastStart, asked, false, today, 4))
        // An event from an earlier break does not count for this one.
        val old = asked.copy(detectedAt = Instant.parse("2026-10-01T08:00:00Z"))
        assertEquals(5, ReturnPlan.detect(LocalDate.of(2026, 10, 14), lastStart, old, false, today, 4))
    }

    @Test fun theFirstTwoSessionsAfterTheChoiceAreEasy() {
        val chosen = Instant.parse("2026-10-20T07:00:00Z")
        val event = ReturnEvent("e", chosen, 5, ReturnChoice.LIGHTER, chosen)
        val first = chosen.plusSeconds(3600)
        val second = first.plusSeconds(86_400)
        val third = second.plusSeconds(86_400)
        val starts = listOf(chosen.minusSeconds(86_400 * 7), first, second, third)
        assertEquals(1, ReturnPlan.easy(event, first, starts)!!.number)
        assertEquals(2, ReturnPlan.easy(event, second, starts)!!.number)
        assertNull(ReturnPlan.easy(event, third, starts))
        assertNull(ReturnPlan.easy(event.copy(choice = ReturnChoice.CONTINUE), first, starts))
        assertNull(ReturnPlan.easy(event, chosen.minusSeconds(60), starts))
    }

    @Test fun easySessionsAreLighter() {
        assertEquals(2, ReturnPlan.lighterSets(3))
        assertEquals(2, ReturnPlan.lighterSets(2))
        val hold = Suggestion(SuggestionKind.INCREASE, SuggestionReason.TOP_OF_RANGE, 62.5, 8, 60.0, 3)
        val easy = ReturnPlan.lighten(hold, 2.5)
        assertEquals(SuggestionReason.EASY_RETURN, easy.reason)
        assertEquals(55.0, easy.weightKg!!, 1e-9)
        val already = Suggestion(SuggestionKind.DECREASE, SuggestionReason.PAIN, 50.0, 8, 60.0, 3)
        assertEquals(already, ReturnPlan.lighten(already, 2.5))
    }
}
