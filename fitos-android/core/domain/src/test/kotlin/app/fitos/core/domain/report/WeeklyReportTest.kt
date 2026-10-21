package app.fitos.core.domain.report

import app.fitos.core.domain.body.DailyWeight
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.TargetOrigin
import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.Sex
import app.fitos.core.domain.training.DayVariant
import app.fitos.core.domain.training.Equipment
import app.fitos.core.domain.training.Exercise
import app.fitos.core.domain.training.ExercisePerformance
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramDay
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.SessionExercise
import app.fitos.core.domain.training.SessionStatus
import app.fitos.core.domain.training.SessionSummary
import app.fitos.core.domain.training.SessionTargets
import app.fitos.core.domain.training.SetLog
import app.fitos.core.domain.training.SetType
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportTest {
    private val saturday = LocalDate.of(2026, 10, 17)
    private val friday = saturday.plusDays(6)
    private val profile = Profile(
        null, Sex.MALE, LocalDate.of(1999, 5, 1), 177.0, ActivityLevel.MODERATE, 5, null, 15, emptySet(),
        PrimaryGoal.FAT_LOSS, EnergyPace.STANDARD,
    )

    private fun target(kcal: Int, from: LocalDate = LocalDate.of(2026, 9, 1)) =
        NutritionTarget(from, kcal, 180, 285, 70, 35, 3000, 450, TargetOrigin.CALCULATED, null)

    private fun totals(kcal: Double, protein: Double = 180.0) =
        NutrientTotals(kcal, protein, 0.0, 0.0, 0.0, false, false, false, false, false, count = 3)

    private fun days(from: LocalDate, to: LocalDate) = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()

    /** A daily weigh-in on every day from [from] to [to], moving [perWeek] kg a week from [start] kg. */
    private fun weights(from: LocalDate, to: LocalDate, start: Double, perWeek: Double) =
        days(from, to).mapIndexed { i, d -> DailyWeight(d, start + perWeek / 7 * i) }

    private fun food(from: LocalDate, to: LocalDate, kcal: Double) = days(from, to).associateWith { totals(kcal) }

    private fun data(
        today: LocalDate = friday,
        weights: List<DailyWeight> = emptyList(),
        food: Map<LocalDate, NutrientTotals> = emptyMap(),
        targets: List<NutritionTarget> = listOf(target(2500)),
        sessions: List<SessionSummary> = emptyList(),
        planned: Int? = null,
        exercises: List<ExerciseWeek> = emptyList(),
        habits: HabitsWeek = HabitsWeek(null, 0, null),
        sleep: Map<LocalDate, Int> = emptyMap(),
        waist: WaistSeries? = null,
        check: Boolean = false,
    ) = WeekData(saturday, today, weights, waist, sessions, planned, exercises, food, targets, habits, sleep, profile, check)

    private fun session(day: LocalDate, kind: VariantKind = VariantKind.FULL) =
        SessionSummary(day.toString(), null, null, kind, day, Instant.EPOCH, 50, 5, 15, 4000.0)

    // ---- the week ----

    @Test fun latestWeekIsThisOneOnFridayAndTheLastOneOtherwise() {
        assertEquals(saturday, WeeklyReportRules.latestWeek(friday))
        assertEquals(saturday, WeeklyReportRules.latestWeek(friday.plusDays(1)))
        assertEquals(saturday.minusWeeks(1), WeeklyReportRules.latestWeek(friday.minusDays(1)))
    }

    @Test fun weightChangeNeedsTwoWeighInsInEachWeek() {
        val ws = listOf(
            DailyWeight(saturday.minusDays(5), 100.8), DailyWeight(saturday.minusDays(2), 100.4),
            DailyWeight(saturday, 100.1), DailyWeight(saturday.plusDays(2), 99.9), DailyWeight(saturday.plusDays(5), 100.0),
        )
        val r = WeeklyReportRules.build(data(weights = ws))
        assertEquals(3, r.weight.days)
        assertEquals(-0.6, r.weight.change!!, 1e-9)
        assertEquals(0.6, (r.highlight as Highlight.WeightDown).kg, 1e-9)
        val one = WeeklyReportRules.build(data(weights = ws.drop(1)))
        assertNull("one weigh-in last week is not enough", one.weight.change)
    }

    @Test fun nutritionAveragesOnlyLoggedDays() {
        val food = mapOf(saturday to totals(2000.0, 180.0), saturday.plusDays(1) to totals(2500.0, 150.0), saturday.plusDays(3) to totals(3000.0, 170.0))
        val n = WeeklyReportRules.build(data(food = food)).nutrition
        assertEquals(3, n.loggedDays)
        assertEquals(7, n.days)
        assertEquals(2500.0, n.meanKcal!!, 1e-9)
        assertEquals(1, n.daysInBand)
        assertEquals(2, n.daysProteinMet)
    }

    @Test fun aWeekInProgressCountsItsDaysSoFarAndGivesNoAdvice() {
        val r = WeeklyReportRules.build(data(today = saturday.plusDays(4), food = mapOf(saturday to totals(2400.0))))
        assertTrue(r.inProgress)
        assertEquals(5, r.nutrition.days)
        assertNull(r.highlight)
        assertTrue(r.advice.isEmpty())
    }

    @Test fun adviceFollowsTheNumbersAndStopsAtTwo() {
        val r = WeeklyReportRules.build(
            data(
                weights = listOf(DailyWeight(saturday, 100.0)),
                food = mapOf(saturday to totals(2400.0), saturday.plusDays(1) to totals(2400.0)),
                sessions = listOf(session(saturday), session(saturday.plusDays(2))),
                planned = 5,
            ),
        )
        assertEquals(listOf(Advice.LogFood(2, 7), Advice.WeighMore(1)), r.advice)
    }

    @Test fun lowProteinAndShortSleepBecomeAdviceWhenTheRestIsLogged() {
        val food = days(saturday, friday).associateWith { totals(2500.0, 120.0) }
        val weighs = days(saturday, friday).map { DailyWeight(it, 100.0) }
        val sleep = days(saturday, friday).associateWith { 360 }
        val r = WeeklyReportRules.build(data(weights = weighs, food = food, sleep = sleep))
        assertEquals(listOf(Advice.Protein(120.0, 180.0), Advice.Sleep(360.0, 450)), r.advice)
    }

    @Test fun nothingLoggedSaysSo() {
        val r = WeeklyReportRules.build(data())
        assertFalse(r.hasData)
        assertNull(r.highlight)
    }

    @Test fun waistComparesWithTheLastMeasurementBefore() {
        val waist = WaistSeries("دور شکم", listOf(saturday.minusDays(7) to 110.0, saturday.plusDays(3) to 109.2))
        val r = WeeklyReportRules.build(data(waist = waist))
        assertEquals(-0.8, r.waist!!.change!!, 1e-9)
        assertEquals(0.8, (r.highlight as Highlight.WaistDown).cm, 1e-9)
        val notThisWeek = WeeklyReportRules.build(data(waist = WaistSeries("دور شکم", listOf(saturday.minusDays(7) to 110.0))))
        assertNull(notThisWeek.waist!!.latest)
    }

    // ---- exercises ----

    private val bench = Exercise("bench", "barbell_bench_press", "پرس سینه با هالتر", null, Equipment.BARBELL, LogType.WEIGHT_REPS, emptySet(), emptySet(), 2.5, null, null, false, false)

    private fun perf(day: LocalDate, vararg sets: Pair<Double, Int>) = ExercisePerformance(
        day.toString(), day,
        sets.mapIndexed { i, (w, r) -> SetLog("$day-$i", i, SetType.WORKING, w, r, null, null, 2, null, false, Instant.EPOCH) },
        null,
    )

    @Test fun exerciseProgressComparesTheBestOfTheWeekWithTheBestBefore() {
        val history = listOf(perf(saturday.plusDays(2), 62.5 to 8, 60.0 to 8), perf(saturday.minusDays(5), 60.0 to 8))
        val w = ExerciseProgress.week(bench, history, saturday, friday)!!
        assertEquals(ProgressMetric.E1RM, w.metric)
        assertEquals(60.0 * (1 + 8 / 30.0), w.before!!, 1e-9)
        assertEquals(62.5 * (1 + 8 / 30.0), w.best, 1e-9)
        assertEquals(62.5, w.bestWeightKg!!, 0.0)
        assertTrue(w.improved)
        val first = ExerciseProgress.week(bench, history.take(1), saturday, friday)!!
        assertNull(first.before)
        assertFalse("a first time is a start, not progress", first.improved)
        assertNull(ExerciseProgress.week(bench, history.drop(1), saturday, friday))
    }

    @Test fun setsPerMuscleCountPrimaryOnceAndSecondaryHalf() {
        val press = bench.copy(primary = setOf(Muscle.CHEST), secondary = setOf(Muscle.DELTS, Muscle.TRICEPS, Muscle.CHEST))
        fun ex(sets: Int, warmups: Int = 0) = SessionExercise(
            "e$sets", press, 0, null, null, false, SessionTargets(sets, 6, 8, 2, null, null, null), null, null, null,
            List(warmups) { SetLog("w$it", it, SetType.WARMUP, 40.0, 8, null, null, null, null, false, Instant.EPOCH) } +
                List(sets) { SetLog("s$it", it, SetType.WORKING, 60.0, 8, null, null, 2, null, false, Instant.EPOCH) },
        )
        val session = WorkoutSession("s", null, null, null, VariantKind.FULL, saturday, java.time.ZoneOffset.UTC, Instant.EPOCH, Instant.EPOCH, null, 0, SessionStatus.FINISHED, null, null, listOf(ex(4, warmups = 2), ex(3)))
        val sets = WeeklyVolume.done(listOf(session))
        assertEquals(7.0, sets[Muscle.CHEST]!!, 0.0)
        assertEquals(3.5, sets[Muscle.DELTS]!!, 0.0)
        assertEquals(3.5, sets[Muscle.TRICEPS]!!, 0.0)
        val r = WeeklyReportRules.build(data(sessions = listOf(session(saturday))).copy(muscleSets = sets, plannedMuscleSets = mapOf(Muscle.CHEST to 10.0, Muscle.LATS to 8.0)))
        assertEquals(
            listOf(Triple(Muscle.CHEST, 7.0, 10.0), Triple(Muscle.LATS, 0.0, 8.0), Triple(Muscle.DELTS, 3.5, null), Triple(Muscle.TRICEPS, 3.5, null)),
            r.training.volume,
        )
    }

    @Test fun aStrengthRecordIsTheHighlight() {
        val record = ExerciseWeek("bench", "پرس سینه با هالتر", ProgressMetric.E1RM, 76.0, 79.2, 62.5, 8)
        val r = WeeklyReportRules.build(
            data(exercises = listOf(record), weights = listOf(DailyWeight(saturday.minusDays(3), 101.0), DailyWeight(saturday.minusDays(1), 101.0), DailyWeight(saturday, 100.0), DailyWeight(friday, 100.0))),
        )
        assertEquals(Highlight.Strength(record), r.highlight)
    }

    @Test fun plannedSessionsFromTheProgram() {
        fun day(i: Int, rest: Boolean, weekday: DayOfWeek? = null) =
            ProgramDay("d$i", i, "d$i", rest, weekday, null, if (rest) emptyList() else listOf(DayVariant("v$i", VariantKind.FULL, null, emptyList())))
        val weekdays = Program("p", "p", null, true, ScheduleMode.WEEKDAYS, DayOfWeek.entries.mapIndexed { i, d -> day(i, i >= 5, d) }, false)
        assertEquals(5, WeeklyReportSource.plannedPerWeek(weekdays))
        val sequence = Program("p", "p", null, true, ScheduleMode.SEQUENCE, listOf(day(0, false), day(1, false), day(2, false), day(3, true)), false)
        assertEquals(5, WeeklyReportSource.plannedPerWeek(sequence))
    }

    // ---- the calorie check ----

    private val window = friday.minusDays(AdaptiveTargets.WINDOW_DAYS - 1L)

    private fun check(
        perWeek: Double = -0.3,
        intake: Double = 2500.0,
        targets: List<NutritionTarget> = listOf(target(2500)),
        ws: List<DailyWeight> = weights(window, friday, 110.6, perWeek),
        food: Map<LocalDate, NutrientTotals> = food(window, friday, intake),
    ) = AdaptiveTargets.check(friday, ws, food, targets, profile)

    @Test fun slowerThanThePaceSuggestsOneSmallStepDown() {
        val r = check() as AdaptiveResult.Change
        // Maintenance ≈ 2500 + 0.3 / 7 × 7700 = 2830; 20% under it is 2264, so 2500 comes down one step of 150.
        assertEquals(2830.0, r.basis.tdeeKcal, 1.0)
        assertEquals(-0.3, r.basis.rateKgPerWeek, 1e-6)
        assertEquals(2350, r.newKcal)
        assertEquals(-AdaptiveTargets.MAX_STEP_KCAL, r.delta)
        assertEquals(ChangeReason.SLOWER_THAN_PACE, r.reason)
        // 25% under 2830 is 2122.5, above the BMR floor of 2100: rounded up, 2150.
        assertEquals(2150, r.basis.lowestKcal)
    }

    @Test fun onPaceKeepsTheTarget() {
        // Maintenance ≈ 3160, 20% under ≈ 2528: within the dead band of 2500.
        assertTrue(check(perWeek = -0.6) is AdaptiveResult.Keep)
    }

    @Test fun losingMoreThanOnePercentAWeekAsksForMoreEnergy() {
        val r = check(perWeek = -1.5) as AdaptiveResult.Change
        assertEquals(ChangeReason.LOSING_TOO_FAST, r.reason)
        assertEquals(2650, r.newKcal)
        // Eating well under the target: the advice is to eat up to it, not a new number.
        assertTrue(check(perWeek = -1.5, intake = 2000.0) is AdaptiveResult.EatUpToTarget)
    }

    @Test fun aTargetUnderTheSafeFloorIsRaised() {
        val r = check(intake = 2400.0, targets = listOf(target(1800))) as AdaptiveResult.Change
        assertEquals(ChangeReason.TOO_LOW, r.reason)
        assertEquals(1950, r.newKcal)
    }

    @Test fun neverSuggestsGoingUnderTheFloor() {
        // Maintenance ≈ 2230; 20% under is below BMR (≈2075), so the target cannot drop under 2100.
        val r = check(perWeek = -0.0, intake = 2230.0, targets = listOf(target(2200)))
        assertTrue(r is AdaptiveResult.Keep || (r is AdaptiveResult.Change && r.newKcal >= 2100))
    }

    @Test fun implausibleNumbersMeanIncompleteLogs() {
        assertTrue(check(perWeek = 0.0, intake = 1200.0, targets = listOf(target(2000))) is AdaptiveResult.NotPlausible)
    }

    @Test fun notReadySaysWhatIsMissing() {
        val sparse = weights(window, friday, 110.0, 0.0).filterIndexed { i, _ -> i % 4 == 0 }
        val someFood = food(window, window.plusDays(9), 2500.0)
        val r = check(ws = sparse, food = someFood, targets = listOf(target(2500, friday.minusDays(5)))) as AdaptiveResult.NotReady
        assertTrue(r.missing.any { it is Missing.WeighIns })
        assertTrue(r.missing.contains(Missing.FoodDays(10, 15)))
        assertTrue(r.missing.contains(Missing.RecentChange(5, 14)))
    }

    @Test fun aTargetChangedAfterTheWeekStillCountsAsRecent() {
        val r = AdaptiveTargets.check(
            friday, weights(window, friday, 110.6, -0.3), food(window, friday, 2500.0),
            listOf(target(2400, friday.plusDays(2)), target(2500)), profile, today = friday.plusDays(3),
        ) as AdaptiveResult.NotReady
        assertEquals(listOf(Missing.RecentChange(1, 14)), r.missing)
    }

    @Test fun daysUnderHalfTheTargetDoNotCountAsLogged() {
        val food = food(window, friday, 2500.0).toMutableMap()
        days(window, window.plusDays(9)).forEach { food[it] = totals(900.0) }
        val r = check(food = food) as AdaptiveResult.NotReady
        assertEquals(listOf(Missing.FoodDays(11, 15)), r.missing)
    }

    @Test fun acceptingKeepsProteinAndFatAndMovesCarbs() {
        val change = check() as AdaptiveResult.Change
        val next = AdaptiveTargets.apply(target(2500), change, profile, friday.plusDays(1))
        assertEquals(2350, next.kcal)
        assertEquals(180, next.proteinG)
        assertEquals(70, next.fatG)
        assertEquals(250, next.carbsG)
        assertEquals(33, next.fiberG)
        assertEquals(TargetOrigin.ADAPTIVE, next.origin)
        assertEquals(friday.plusDays(1), next.effectiveFrom)
        val note = next.assumptions!!.adaptive!!
        assertEquals(2500, note.previousKcal)
        assertEquals(21, note.days)
    }

    @Test fun onlyTheLatestWeekRunsTheCheck() {
        val ws = weights(window, friday, 110.6, -0.3)
        val food = food(window, friday, 2500.0)
        val latest = WeeklyReportRules.build(data(weights = ws, food = food, check = true))
        assertTrue(latest.adaptive is AdaptiveResult.Change)
        assertTrue(latest.advice.first() is Advice.Calories)
        assertNull(WeeklyReportRules.build(data(weights = ws, food = food)).adaptive)
    }
}
