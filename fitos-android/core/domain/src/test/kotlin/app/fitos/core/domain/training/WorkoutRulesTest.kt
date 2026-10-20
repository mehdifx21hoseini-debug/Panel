package app.fitos.core.domain.training

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRulesTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val t0 = Instant.parse("2026-10-20T06:00:00Z")

    private fun exercise(key: String): Exercise {
        val lib = ExerciseLibrary.byKey.getValue(key)
        return Exercise(key, key, lib.nameFa, lib.nameEn, lib.equipment, lib.logType, lib.primary, lib.secondary, lib.incrementKg, lib.bodyweightPercent, lib.instructions, false, false)
    }

    private val bench = exercise("barbell_bench_press")
    private val pullUp = exercise("pull_up")
    private val targets = SessionTargets(sets = 3, repMin = 8, repMax = 12, rir = 2, weightKg = 60.0, durationSec = null, restSec = 120)
    private val rule = ProgressionRule(ProgressionType.DOUBLE_PROGRESSION, 2.5, 10, requireGoodQuality = true)

    private var counter = 0
    private fun set(
        weight: Double?,
        reps: Int,
        type: SetType = SetType.WORKING,
        rir: Int? = null,
        quality: SetQuality? = null,
        pain: Boolean = false,
        at: Instant = t0.plusSeconds((counter++) * 60L),
    ) = SetLog("s${counter}", counter, type, weight, reps, null, null, rir, quality, pain, at)

    private fun performance(daysAgo: Long, vararg sets: SetLog, id: String = "p$daysAgo", body: Double? = null) =
        ExercisePerformance(id, today.minusDays(daysAgo), sets.toList(), body)

    // ---- strength ----

    @Test fun epleyHoldsForOneToTwelveReps() {
        assertEquals(116.67, Strength.e1rm(100.0, 5)!!, 0.01)
        assertEquals(100.0, Strength.e1rm(100.0, 1)!!, 1e-9)
        assertNull(Strength.e1rm(100.0, 13))
        assertNull(Strength.e1rm(0.0, 5))
        assertNull(Strength.e1rm(null, 5))
    }

    @Test fun tonnageSkipsWarmUpsAndCountsBodyWeightOnlyWhenKnown() {
        val sets = listOf(set(40.0, 10, SetType.WARMUP), set(60.0, 10), set(60.0, 8))
        assertEquals(1080.0, Strength.tonnage(bench, sets, null), 1e-9)

        val pct = pullUp.bodyweightPercent!!
        val pulls = listOf(set(null, 8), set(5.0, 6))
        // Share of body weight × reps, plus the added 5 kg on the second set.
        assertEquals(100.0 * pct / 100 * 14 + 5 * 6, Strength.tonnage(pullUp, pulls, 100.0), 1e-9)
        // Without a known body weight only the added weight counts.
        assertEquals(30.0, Strength.tonnage(pullUp, pulls, null), 1e-9)
    }

    // ---- records ----

    @Test fun recordsKeepTheFirstTimeOnTiesAndIgnoreTheFirstSession() {
        val first = performance(14, set(60.0, 10), set(60.0, 10), id = "a")
        val second = performance(7, set(60.0, 10), set(62.5, 8), id = "b")
        val third = performance(0, set(65.0, 8), set(40.0, 20, SetType.WARMUP), id = "c")
        val history = listOf(third, first, second)
        val records = Records.compute(bench, history)

        val tenReps = records.single { it.type == RecordType.MAX_WEIGHT_FOR_REPS && it.reps == 10 }
        assertEquals("a", tenReps.sessionId) // tie in session b keeps session a
        assertEquals(65.0, records.single { it.type == RecordType.MAX_WEIGHT_FOR_REPS && it.reps == 8 }.value, 1e-9)
        assertEquals("c", records.single { it.type == RecordType.E1RM }.sessionId)
        // Warm-up with 20 reps is not a record.
        assertTrue(records.none { it.reps == 20 })

        assertEquals(emptyList<PersonalRecord>(), Records.setIn("a", records, history))
        assertTrue(Records.setIn("c", records, history).any { it.type == RecordType.E1RM })
    }

    @Test fun bodyWeightRecordsAreReps() {
        val records = Records.compute(pullUp, listOf(performance(3, set(null, 7), set(null, 9))))
        assertEquals(9.0, records.single { it.type == RecordType.MAX_REPS_BODYWEIGHT }.value, 1e-9)
    }

    // ---- progression ----

    private fun suggest(vararg sets: SetLog, daysAgo: Long = 3, r: ProgressionRule? = rule, t: SessionTargets = targets, e: Exercise = bench) =
        Progression.suggest(e, t, r, performance(daysAgo, *sets), today)

    @Test fun firstTimeUsesThePlannedStartWeight() {
        val s = Progression.suggest(bench, targets, rule, null, today)
        assertEquals(SuggestionKind.FIRST_TIME, s.kind)
        assertEquals(60.0, s.weightKg)
        assertEquals(8, s.reps)
    }

    @Test fun allSetsAtTheTopWithReserveAddOneStep() {
        val s = suggest(set(60.0, 12, rir = 2), set(60.0, 12, rir = 3), set(60.0, 12))
        assertEquals(SuggestionKind.INCREASE, s.kind)
        assertEquals(62.5, s.weightKg)
        assertEquals(8, s.reps)
        assertEquals(60.0, s.previousWeightKg)
    }

    @Test fun topRepsAtFailureHoldTheWeight() {
        val s = suggest(set(60.0, 12, rir = 0), set(60.0, 12, rir = 2), set(60.0, 12, rir = 2))
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertEquals(SuggestionReason.RIR, s.reason)
        assertEquals(60.0, s.weightKg)
    }

    @Test fun twoSetsBelowTheRangeLowerTheWeight() {
        val s = suggest(set(100.0, 8), set(100.0, 6), set(100.0, 5))
        assertEquals(SuggestionKind.DECREASE, s.kind)
        assertEquals(SuggestionReason.BELOW_RANGE, s.reason)
        assertEquals(90.0, s.weightKg)
    }

    @Test fun painLowersAndPoorQualityHolds() {
        assertEquals(SuggestionReason.PAIN, suggest(set(60.0, 12), set(60.0, 12, pain = true), set(60.0, 12)).reason)
        val poor = suggest(set(60.0, 12), set(60.0, 12, quality = SetQuality.POOR), set(60.0, 12))
        assertEquals(SuggestionKind.HOLD, poor.kind)
        assertEquals(SuggestionReason.QUALITY, poor.reason)
        // "OK" blocks the increase only when the rule asks for good quality.
        val ok = arrayOf(set(60.0, 12, quality = SetQuality.OK), set(60.0, 12), set(60.0, 12))
        assertEquals(SuggestionReason.QUALITY, suggest(*ok).reason)
        assertEquals(SuggestionKind.INCREASE, suggest(*ok, r = rule.copy(requireGoodQuality = false)).kind)
    }

    @Test fun insideTheRangeKeepsTheWeightAndAimsHigher() {
        val s = suggest(set(60.0, 12), set(60.0, 10), set(60.0, 9))
        assertEquals(SuggestionKind.HOLD, s.kind)
        assertEquals(SuggestionReason.IN_RANGE, s.reason)
        assertEquals(60.0, s.weightKg)
        assertEquals(12, s.reps)
    }

    @Test fun afterALongBreakStartAboutTenPercentLighter() {
        val s = suggest(set(80.0, 12), set(80.0, 12), set(80.0, 12), daysAgo = 14)
        assertEquals(SuggestionKind.RETURN, s.kind)
        assertEquals(72.5, s.weightKg)
        assertEquals(14L, s.daysSince)
    }

    @Test fun withoutARuleRepeatLastTime() {
        val s = suggest(set(60.0, 12), set(60.0, 12), set(60.0, 12), r = null)
        assertEquals(SuggestionKind.REPEAT, s.kind)
        assertEquals(60.0, s.weightKg)
    }

    @Test fun bodyWeightMovesProgressInReps() {
        val t = targets.copy(weightKg = null)
        assertEquals(SuggestionKind.INCREASE, suggest(set(null, 12), set(null, 12), set(null, 12), t = t, e = pullUp).kind)
        val hold = suggest(set(null, 10), set(null, 9), set(null, 8), t = t, e = pullUp)
        assertEquals(SuggestionKind.HOLD, hold.kind)
        assertNull(hold.weightKg)
    }

    @Test fun reductionsLandOnTheStep() {
        assertEquals(90.0, Progression.reduce(100.0, 10, 2.5), 1e-9)
        assertEquals(17.5, Progression.reduce(20.0, 5, 2.5), 1e-9)
        assertEquals(2.5, Progression.reduce(5.0, 10, 2.5), 1e-9)
        assertEquals(0.0, Progression.reduce(2.0, 10, 2.5), 1e-9)
    }

    // ---- session flow ----

    private fun sessionExercise(id: String, sets: Int, logged: Int, group: Int? = null, rest: Int = 90, base: Long = 0) = SessionExercise(
        id, bench, 0, null, null, false,
        SessionTargets(sets, 8, 12, 2, 60.0, null, rest), group, rule, null,
        (0 until logged).map { SetLog("$id-$it", it, SetType.WORKING, 60.0, 10, null, null, null, null, false, t0.plusSeconds(base + it * 200L)) },
    )

    private fun session(vararg exercises: SessionExercise) = WorkoutSession(
        "w", null, null, null, VariantKind.FULL, today, ZoneId.of("Asia/Tehran"), t0, null, null, 0, SessionStatus.IN_PROGRESS, null, null, exercises.toList(),
    )

    @Test fun plainExercisesRestBetweenSetsThenMoveOn() {
        val s = session(sessionExercise("a", 3, 1), sessionExercise("b", 3, 0))
        assertEquals(SessionFlow.Step(0, rest = true), SessionFlow.after(s, 0))
        val done = session(sessionExercise("a", 3, 3), sessionExercise("b", 3, 0))
        assertEquals(SessionFlow.Step(1, rest = true), SessionFlow.after(done, 0))
        val all = session(sessionExercise("a", 1, 1), sessionExercise("b", 1, 1, base = 1000))
        assertEquals(SessionFlow.Step(null, rest = false), SessionFlow.after(all, 1))
        assertNull(SessionFlow.restEndsAt(all))
    }

    @Test fun supersetsAlternateAndRestAfterTheLastMember() {
        // a1 logged, b not yet: go to b without rest.
        val s1 = session(sessionExercise("a", 3, 1, group = 1), sessionExercise("b", 3, 0, group = 1), sessionExercise("c", 3, 0))
        assertEquals(SessionFlow.Step(1, rest = false), SessionFlow.after(s1, 0))
        assertNull(SessionFlow.restEndsAt(s1))
        // a1 and b1 logged: rest, then a again.
        val s2 = session(sessionExercise("a", 3, 1, group = 1), sessionExercise("b", 3, 1, group = 1, base = 100), sessionExercise("c", 3, 0))
        assertEquals(SessionFlow.Step(0, rest = true), SessionFlow.after(s2, 1))
        assertEquals(t0.plusSeconds(100 + 90), SessionFlow.restEndsAt(s2))
        assertEquals(0, SessionFlow.resumeAt(s2))
        // Both finished: on to c.
        val s3 = session(sessionExercise("a", 1, 1, group = 1), sessionExercise("b", 1, 1, group = 1, base = 100), sessionExercise("c", 3, 0))
        assertEquals(SessionFlow.Step(2, rest = true), SessionFlow.after(s3, 1))
    }

    @Test fun skippedExercisesAreDoneAndActiveTimeExcludesPauses() {
        val skipped = sessionExercise("a", 3, 0).copy(skipped = true)
        val s = session(skipped, sessionExercise("b", 3, 0)).copy(pausedMillis = 60_000)
        assertEquals(1, SessionFlow.firstOpen(s))
        assertEquals(29 * 60_000L, s.activeMillis(t0.plusSeconds(30 * 60)))
        val paused = s.copy(pausedAt = t0.plusSeconds(10 * 60))
        assertEquals(9 * 60_000L, paused.activeMillis(t0.plusSeconds(30 * 60)))
    }

    @Test fun weekdayScheduleMovesOnOnceTodayIsDone() {
        fun day(id: String, weekday: DayOfWeek) = ProgramDay(id, 0, id, false, weekday, null, emptyList())
        val program = Program("p", "p", null, true, ScheduleMode.WEEKDAYS, listOf(day("tue", DayOfWeek.TUESDAY), day("thu", DayOfWeek.THURSDAY)), false)
        assertEquals("tue", Schedule.next(program, null, today)!!.id) // today is a Tuesday
        assertEquals("thu", Schedule.nextAfter(program, CompletedDay("tue", today), today)!!.id)
        assertEquals("tue", Schedule.nextAfter(program, CompletedDay("thu", today.minusDays(5)), today)!!.id)
    }
}
