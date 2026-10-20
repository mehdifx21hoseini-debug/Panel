package app.fitos.core.domain.training

import app.fitos.core.domain.profile.HomeEquipment
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {

    private fun exercise(key: String): Exercise {
        val lib = ExerciseLibrary.byKey.getValue(key)
        return Exercise(key, key, lib.nameFa, lib.nameEn, lib.equipment, lib.logType, lib.primary, lib.secondary, lib.incrementKg, lib.bodyweightPercent, lib.instructions, false, false)
    }

    private fun planned(key: String, sets: Int) = PlannedExercise("p-$key", exercise(key), 0, sets, 8, 12, 2, null, null, null, 90, null, null, null)

    private fun day(id: String, rest: Boolean = false, weekday: DayOfWeek? = null, vararg exercises: PlannedExercise) =
        ProgramDay(id, 0, id, rest, weekday, null, if (rest) emptyList() else listOf(DayVariant("v-$id", VariantKind.FULL, null, exercises.toList())))

    private val program = Program(
        "p", "test", null, true, ScheduleMode.SEQUENCE,
        listOf(
            day("a", weekday = DayOfWeek.SATURDAY, exercises = arrayOf(planned("barbell_bench_press", 4))),
            day("b", weekday = DayOfWeek.SUNDAY, exercises = arrayOf(planned("back_squat", 4))),
            day("rest", rest = true, weekday = DayOfWeek.MONDAY),
            day("c", weekday = DayOfWeek.TUESDAY, exercises = arrayOf(planned("lat_pulldown", 3))),
        ),
        archived = false,
    )

    @Test fun libraryKeysAreUniqueAndComplete() {
        val keys = ExerciseLibrary.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(ExerciseLibrary.all.size >= 100)
        ExerciseLibrary.all.forEach {
            assertTrue("${it.key} needs a cue", it.instructions.length > 20)
            assertTrue("${it.key} primary and secondary overlap", (it.primary intersect it.secondary).isEmpty())
            if (it.logType == LogType.WEIGHT_REPS) assertTrue("${it.key} needs an increment", it.incrementKg != null)
        }
    }

    @Test fun everyTemplateOptionExistsInTheLibrary() {
        ProgramTemplates.all.forEach { template ->
            template.days.flatMap { it.full + it.home + it.minimal }.flatMap { it.options }.forEach { option ->
                assertTrue("missing ${option.key}", option.key in ExerciseLibrary.byKey)
            }
        }
        assertEquals(5, ProgramTemplates.upperFocus5.days.count { !it.rest })
    }

    @Test fun homeVersionsFollowTheEquipment() {
        val day = ProgramTemplates.upperFocus5.days.first()
        val none = ProgramTemplates.resolve(day.home, emptySet()).map { it.key }
        assertEquals(listOf("push_up", "inverted_row", "pike_push_up", "bench_dip"), none)
        val withDumbbells = ProgramTemplates.resolve(day.home, setOf(HomeEquipment.DUMBBELLS)).map { it.key }
        assertEquals(listOf("db_floor_press", "one_arm_db_row", "lateral_raise", "overhead_db_extension"), withDumbbells)
        val bandsOnly = ProgramTemplates.resolve(day.home, setOf(HomeEquipment.BANDS)).map { it.key }
        assertEquals(listOf("push_up", "band_row", "band_lateral_raise", "band_triceps_extension"), bandsOnly)
        // No duplicates, and a slot with no option for the equipment is left out rather than guessed.
        val tuesday = ProgramTemplates.upperFocus5.days.first { it.weekday == DayOfWeek.TUESDAY }
        assertEquals(listOf("pike_push_up", "bench_dip"), ProgramTemplates.resolve(tuesday.home, emptySet()).map { it.key })
    }

    @Test fun sequenceNeverSkipsAMissedDay() {
        val today = LocalDate.of(2026, 10, 20)
        assertEquals("a", Schedule.next(program, null, today)!!.id)
        assertEquals("b", Schedule.next(program, "a", today)!!.id)
        assertEquals("c", Schedule.next(program, "b", today)!!.id) // rest day is passed over
        assertEquals("a", Schedule.next(program, "c", today)!!.id) // wraps around
        assertNull(Schedule.next(program.copy(days = listOf(day("r", rest = true))), null, today))
    }

    @Test fun weekdayModeTakesTodayOrTheNextPlannedDay() {
        val weekly = program.copy(scheduleMode = ScheduleMode.WEEKDAYS)
        val tuesday = LocalDate.of(2026, 10, 20)
        assertEquals("c", Schedule.next(weekly, null, tuesday)!!.id)
        assertEquals("a", Schedule.next(weekly, null, tuesday.plusDays(1))!!.id) // Wednesday → next is Saturday
        assertEquals("rest", Schedule.dayOn(weekly, DayOfWeek.MONDAY)!!.id)
    }

    @Test fun plannedVolumeCountsSecondaryAsHalf() {
        val volume = PlannedVolume.perCycle(program)
        assertEquals(4.0, volume[Muscle.CHEST]!!, 1e-9)
        assertEquals(2.0, volume[Muscle.TRICEPS]!!, 1e-9) // bench 4 × 0.5
        assertEquals(3.0, volume[Muscle.LATS]!!, 1e-9)
        assertEquals(4.0, volume[Muscle.QUADS]!!, 1e-9)
        val (primary, secondary) = PlannedVolume.muscles(program.days.first().full)
        assertEquals(setOf(Muscle.CHEST), primary)
        assertEquals(setOf(Muscle.DELTS, Muscle.TRICEPS), secondary)
    }

    @Test fun rulesAndDefaults() {
        val bench = exercise("barbell_bench_press")
        val defaults = TrainingRules.defaults(bench)
        assertTrue(TrainingRules.check(defaults, bench.logType).isEmpty())
        assertEquals(2.5, defaults.progression!!.incrementKg!!, 1e-9)
        assertEquals(setOf(TrainingRules.Problem.REP_ORDER), TrainingRules.check(defaults.copy(repMin = 12, repMax = 8), bench.logType))
        assertEquals(setOf(TrainingRules.Problem.SETS), TrainingRules.check(defaults.copy(sets = 0), bench.logType))
        val plank = exercise("plank")
        assertTrue(TrainingRules.check(TrainingRules.defaults(plank), plank.logType).isEmpty())
        assertEquals(setOf(TrainingRules.Problem.DURATION), TrainingRules.check(TrainingRules.defaults(plank).copy(durationSec = 2), plank.logType))
        assertFalse(TrainingRules.defaults(exercise("push_up")).progression != null)
    }

    @Test fun sessionEstimateIsPlausible() {
        val minutes = SessionEstimate.minutes(listOf(planned("barbell_bench_press", 4), planned("lat_pulldown", 3)))
        // 7 sets × 40 s + 5 rests × 90 s + 2 × 60 s = 850 s → 15 min
        assertEquals(15, minutes)
        assertEquals(0, SessionEstimate.minutes(emptyList()))
    }
    @Test fun librarySearchFoldsArabicLettersAndFilters() {
        val bench = exercise("barbell_bench_press")
        val pulldown = exercise("lat_pulldown")
        val mine = bench.copy(id = "mine", builtinKey = null, nameFa = "پرس سینه دستگاه همر", isCustom = true, isFavorite = true)
        val all = listOf(pulldown, bench, mine)

        // Arabic yeh/kaf and a zero-width non-joiner still find the Persian name.
        assertEquals(listOf("mine"), ExerciseFilter(query = "همر").apply(all).map { it.id })
        assertTrue(ExerciseFilter(query = bench.nameFa.replace('ی', 'ي')).apply(all).any { it.id == bench.id })
        assertEquals(ExerciseFilter.fold("زیر\u200cبغل"), ExerciseFilter.fold("زير  بغل"))
        assertTrue(ExerciseFilter(query = "BENCH").apply(all).any { it.id == bench.id })

        assertEquals(listOf(pulldown.id), ExerciseFilter(muscle = Muscle.LATS).apply(all).map { it.id })
        assertEquals(listOf("mine"), ExerciseFilter(mineOnly = true).apply(all).map { it.id })
        assertEquals(listOf("mine"), ExerciseFilter(favoritesOnly = true).apply(all).map { it.id })
        assertEquals(all, ExerciseFilter().apply(all))
        assertTrue(ExerciseFilter().isEmpty)
    }

    @Test fun namesSortInPersianAlphabetOrder() {
        val names = listOf("نشر از جانب", "پرس سینه", "گود صبح بخیر", "بارفیکس", "چرخش روسی", "اسکات با هالتر", "اسکات")
        assertEquals(
            listOf("اسکات", "اسکات با هالتر", "بارفیکس", "پرس سینه", "چرخش روسی", "گود صبح بخیر", "نشر از جانب"),
            names.sortedWith(ExerciseFilter.nameOrder),
        )
    }
}
