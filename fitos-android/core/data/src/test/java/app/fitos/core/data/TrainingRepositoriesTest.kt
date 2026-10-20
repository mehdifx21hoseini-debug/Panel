package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomExerciseRepository
import app.fitos.core.data.repository.RoomProgramRepository
import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.training.Equipment
import app.fitos.core.domain.training.ExerciseInput
import app.fitos.core.domain.training.ExerciseLibrary
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.ProgressionType
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.targets
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrainingRepositoriesTest {

    private lateinit var db: FitDatabase
    private lateinit var exercises: RoomExerciseRepository
    private lateinit var programs: RoomProgramRepository
    private val clock = TestData.clockAt(TestData.today)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitDatabase::class.java).allowMainThreadQueries().build()
        exercises = RoomExerciseRepository(db, clock)
        programs = RoomProgramRepository(db, exercises, clock)
    }

    @After fun close() = db.close()

    private suspend fun id(key: String) = exercises.idsByKey().getValue(key)

    @Test fun libraryIsSeededOnceAndKeepsFavourites() = runBlocking {
        val first = exercises.all.first()
        assertEquals(ExerciseLibrary.all.size, first.size)
        val bench = first.first { it.builtinKey == "barbell_bench_press" }
        assertEquals(setOf(Muscle.CHEST), bench.primary)
        assertEquals(setOf(Muscle.DELTS, Muscle.TRICEPS), bench.secondary)
        exercises.setFavorite(bench.id, true)
        // A new repository (next app start) re-checks the library without duplicating or resetting.
        val again = RoomExerciseRepository(db, clock).all.first()
        assertEquals(ExerciseLibrary.all.size, again.size)
        assertTrue(again.first { it.id == bench.id }.isFavorite)
    }

    @Test fun customExercisesCanBeEditedAndHidden() = runBlocking {
        val input = ExerciseInput("  پرس سینه دستگاه همر  ", null, Equipment.MACHINE, LogType.WEIGHT_REPS, setOf(Muscle.CHEST), setOf(Muscle.TRICEPS, Muscle.CHEST), 5.0, null)
        val custom = exercises.addCustom(input)
        assertEquals("پرس سینه دستگاه همر", custom.nameFa)
        assertEquals(setOf(Muscle.TRICEPS), custom.secondary) // a primary muscle is not also secondary
        exercises.updateCustom(custom.id, input.copy(nameFa = "همر", primary = setOf(Muscle.CHEST, Muscle.DELTS)))
        assertEquals("همر", exercises.exercise(custom.id).first()!!.nameFa)

        val programId = programs.createEmpty("آزمایشی")
        val dayId = programs.addDay(programId, "روز ۱", isRest = false)
        programs.addExercise(dayId, VariantKind.FULL, custom.id)
        assertEquals(1, exercises.usageCount(custom.id))
        exercises.deleteCustom(custom.id)
        assertFalse(exercises.all.first().any { it.id == custom.id })
        // The program keeps its exercise.
        assertEquals("همر", programs.program(programId).first()!!.days.first().full.first().exercise.nameFa)
    }

    @Test fun templateBuildsFiveDaysWithHomeVersionsForTheEquipment() = runBlocking {
        val programId = programs.createFromTemplate(ProgramTemplates.upperFocus5, setOf(HomeEquipment.DUMBBELLS))
        val program = programs.program(programId).first()!!
        assertTrue(program.isActive) // first program becomes active
        assertEquals(7, program.days.size)
        assertEquals(5, program.trainingDays.size)
        assertEquals(DayOfWeek.SATURDAY, program.days.first().preferredWeekday)
        val chestBack = program.days.first()
        assertEquals("barbell_bench_press", chestBack.full.first().exercise.builtinKey)
        assertEquals(4, chestBack.full.first().sets)
        assertEquals(6, chestBack.full.first().repMin)
        assertEquals(ProgressionType.DOUBLE_PROGRESSION, chestBack.full.first().progression!!.type)
        assertEquals("db_floor_press", chestBack.variant(VariantKind.HOME)!!.exercises.first().exercise.builtinKey)
        assertEquals(2, chestBack.variant(VariantKind.MINIMAL)!!.exercises.size)
        assertEquals(1, programs.changes(programId).first().size)
    }

    @Test fun onlyOneProgramIsActive() = runBlocking {
        val a = programs.createEmpty("الف")
        val b = programs.createEmpty("ب")
        assertEquals(a, programs.active.first()!!.id)
        programs.setActive(b)
        assertEquals(b, programs.active.first()!!.id)
        assertEquals(1, programs.programs.first().count { it.isActive })
        programs.archive(b, true)
        assertNull(programs.active.first())
        assertTrue(programs.programs.first().first { it.id == b }.archived)
    }

    @Test fun daysCanBeAddedMovedChangedAndRemoved() = runBlocking {
        val p = programs.createEmpty("برنامه")
        val d1 = programs.addDay(p, "بالاتنه", false)
        val d2 = programs.addDay(p, "", true)
        val d3 = programs.addDay(p, "پایین‌تنه", false)
        programs.moveDay(d3, -2)
        assertEquals(listOf(d3, d1, d2), programs.program(p).first()!!.days.map { it.id })
        programs.updateDay(d1, "بالاتنه A", false, DayOfWeek.SATURDAY, "سنگین")
        val day = programs.program(p).first()!!.days.first { it.id == d1 }
        assertEquals("بالاتنه A", day.name)
        assertEquals(DayOfWeek.SATURDAY, day.preferredWeekday)
        programs.deleteDay(d2)
        assertEquals(listOf(0, 1), programs.program(p).first()!!.days.map { it.position })
        val log = programs.changes(p).first().map { it.summary }
        assertTrue(log.first().contains("حذف"))
        assertTrue(log.any { it.contains("بالاتنه A") && it.contains("شنبه") })
    }

    @Test fun plannedExercisesCanBeEditedReorderedReplacedAndRemoved() = runBlocking {
        val p = programs.createEmpty("برنامه")
        val day = programs.addDay(p, "سینه", false)
        val bench = programs.addExercise(day, VariantKind.FULL, id("barbell_bench_press"))
        val fly = programs.addExercise(day, VariantKind.FULL, id("dumbbell_fly"))
        val planned = programs.program(p).first()!!.days.first().full.first()
        assertEquals(3, planned.sets) // defaults
        assertEquals(2.5, planned.progression!!.incrementKg!!, 1e-9)

        programs.updatePlanned(bench, planned.targets().copy(sets = 4, repMin = 6, repMax = 8, restSec = 150, startWeightKg = 60.0))
        val edited = programs.program(p).first()!!.days.first().full.first()
        assertEquals(4, edited.sets)
        assertEquals(60.0, edited.startWeightKg!!, 1e-9)
        assertTrue(programs.changes(p).first().first().summary.contains("ست ۳ → ۴"))

        programs.movePlanned(fly, -1)
        assertEquals(listOf(fly, bench), programs.program(p).first()!!.days.first().full.map { it.id })

        // Same kind of exercise: targets stay. Different kind (time): targets reset to sensible ones.
        programs.replaceExercise(bench, id("dumbbell_bench_press"))
        val replaced = programs.program(p).first()!!.days.first().full.first { it.id == bench }
        assertEquals("dumbbell_bench_press", replaced.exercise.builtinKey)
        assertEquals(4, replaced.sets)
        assertEquals(2.0, replaced.progression!!.incrementKg!!, 1e-9)
        programs.replaceExercise(fly, id("plank"))
        val plank = programs.program(p).first()!!.days.first().full.first { it.id == fly }
        assertEquals(30, plank.durationSec)
        assertNull(plank.repMin)
        assertNull(plank.progression)

        programs.removePlanned(fly)
        val left = programs.program(p).first()!!.days.first().full
        assertEquals(listOf(bench), left.map { it.id })
        assertEquals(0, left.first().position)
    }

    @Test fun variantsCopyAndProgramsDuplicate() = runBlocking {
        val p = programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet())
        val day = programs.program(p).first()!!.days.first()
        programs.copyVariant(day.id, VariantKind.FULL, VariantKind.MINIMAL)
        assertEquals(day.full.map { it.exercise.id }, programs.program(p).first()!!.days.first().variant(VariantKind.MINIMAL)!!.exercises.map { it.exercise.id })

        val copy = programs.duplicate(p, "نسخه‌ی دوم")
        val original = programs.program(p).first()!!
        val duplicated = programs.program(copy).first()!!
        assertFalse(duplicated.isActive)
        assertEquals(original.days.map { it.name }, duplicated.days.map { it.name })
        assertEquals(original.days.flatMap { d -> d.variants.flatMap { v -> v.exercises.map { it.exercise.id to it.sets } } },
            duplicated.days.flatMap { d -> d.variants.flatMap { v -> v.exercises.map { it.exercise.id to it.sets } } })
        assertNotEquals(original.days.first().id, duplicated.days.first().id)
        // Editing the copy leaves the original as it was.
        programs.removePlanned(duplicated.days.first().full.first().id)
        assertEquals(original.days.first().full.size, programs.program(p).first()!!.days.first().full.size)
    }
    @Test fun restoringABackupWithoutExercisesBringsTheLibraryBack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A backup made before the library existed (nothing ever read the exercises).
        val old = DataGraph.inMemory(context, clock, FakeSettings())
        val bytes = java.io.ByteArrayOutputStream().also { old.backup.export(it, null) }.toByteArray()
        old.close()

        val graph = DataGraph.inMemory(context, clock, FakeSettings())
        assertEquals(ExerciseLibrary.all.size, graph.exercises.all.first().size)
        graph.backup.restore(graph.backup.stage(java.io.ByteArrayInputStream(bytes), null))
        assertEquals(ExerciseLibrary.all.size, graph.exercises.all.first().size)
        graph.close()
    }
}
