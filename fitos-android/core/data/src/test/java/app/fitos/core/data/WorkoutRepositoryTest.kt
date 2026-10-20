package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomExerciseRepository
import app.fitos.core.data.repository.RoomProgramRepository
import app.fitos.core.data.repository.RoomWeightRepository
import app.fitos.core.data.repository.RoomWorkoutRepository
import app.fitos.core.data.repository.SessionInProgressException
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.RecordType
import app.fitos.core.domain.training.SessionStatus
import app.fitos.core.domain.training.SetInput
import app.fitos.core.domain.training.SetType
import app.fitos.core.domain.training.VariantKind
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A clock the test moves forward. */
private class MovingClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MovingClock(now, zone)
    override fun instant(): Instant = now
    fun advance(minutes: Long) {
        now = now.plus(Duration.ofMinutes(minutes))
    }
    fun nextDays(days: Long) {
        now = now.plus(Duration.ofDays(days))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WorkoutRepositoryTest {

    private lateinit var db: FitDatabase
    private val clock = MovingClock(TestData.today.atTime(9, 0).atZone(TestData.zone).toInstant(), TestData.zone)
    private lateinit var programs: RoomProgramRepository
    private lateinit var workouts: RoomWorkoutRepository
    private lateinit var programId: String
    private lateinit var chestDayId: String

    @Before fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitDatabase::class.java).allowMainThreadQueries().build()
        programs = RoomProgramRepository(db, RoomExerciseRepository(db, clock), clock)
        workouts = RoomWorkoutRepository(db, clock)
        programId = programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet())
        chestDayId = programs.program(programId).first()!!.days.first().id
    }

    @After fun close() = db.close()

    private suspend fun startChest(kind: VariantKind = VariantKind.FULL) = workouts.start(programId, chestDayId, kind)
    private suspend fun session(id: String) = workouts.session(id).first()!!

    /** Logs [reps] at [weight] for every planned set of the first exercise (bench press). */
    private suspend fun benchSets(sessionId: String, weight: Double, vararg reps: Int) {
        val bench = session(sessionId).exercises.first()
        reps.forEach { r ->
            workouts.logSet(bench.id, SetInput(weightKg = weight, reps = r))
            clock.advance(3)
        }
    }

    @Test fun startCopiesThePlanAndAllowsOneOpenSession() = runBlocking {
        val id = startChest()
        val s = session(id)
        assertEquals(SessionStatus.IN_PROGRESS, s.status)
        assertEquals("سینه و پشت", s.dayName)
        assertEquals(6, s.exercises.size)
        val bench = s.exercises.first()
        assertEquals("barbell_bench_press", bench.exercise.builtinKey)
        assertEquals(4, bench.targets.sets)
        assertEquals(6, bench.targets.repMin)
        assertEquals(150, bench.targets.restSec)
        assertNotNull(bench.progression)
        try {
            startChest(VariantKind.HOME)
            fail("a second session must not start")
        } catch (_: SessionInProgressException) {
        }
        // Editing the program afterwards does not change the session.
        programs.removePlanned(programs.program(programId).first()!!.days.first().full.first().id)
        assertEquals(6, session(id).exercises.size)
    }

    @Test fun setsAreSavedAtOnceAndSurviveARestart() = runBlocking {
        val id = startChest()
        benchSets(id, 60.0, 8, 8)
        // A new repository on the same database = the app opened again after being killed.
        val reopened = RoomWorkoutRepository(db, clock)
        val active = reopened.active.first()!!
        assertEquals(id, active.id)
        assertEquals(listOf(8, 8), active.exercises.first().sets.map { it.reps })
        assertEquals(2, active.workingSets)
    }

    @Test fun pausesAreLeftOutOfTheTrainingTime() = runBlocking {
        val id = startChest()
        clock.advance(10)
        workouts.pause(id)
        clock.advance(5)
        assertTrue(session(id).isPaused)
        // Logging a set while paused resumes the session.
        workouts.logSet(session(id).exercises.first().id, SetInput(weightKg = 60.0, reps = 8))
        clock.advance(20)
        workouts.finish(id, perceivedEffort = 7, note = "  خوب بود ")
        val s = session(id)
        assertEquals(SessionStatus.FINISHED, s.status)
        assertEquals(30 * 60_000L, s.activeMillis(clock.now))
        assertEquals(7, s.perceivedEffort)
        assertEquals("خوب بود", s.note)
        val summary = workouts.history.first().single()
        assertEquals(30, summary.activeMinutes)
        assertEquals(480.0, summary.tonnageKg, 1e-9)
    }

    @Test fun recordsComeFromFinishedSessionsAndTheReportComparesWithLastTime() = runBlocking {
        val first = startChest()
        benchSets(first, 60.0, 10, 10, 9, 8)
        workouts.finish(first, null, null)
        val firstReport = workouts.report(first)!!
        val benchId = firstReport.session.exercises.first().exercise.id
        assertTrue(benchId in firstReport.firstTime)
        assertTrue(firstReport.records.isEmpty())

        clock.nextDays(3)
        val second = startChest()
        // Last time is the finished session only.
        val last = workouts.lastPerformance(benchId, clock.now)!!
        assertEquals(first, last.sessionId)
        assertEquals(listOf(10, 10, 9, 8), last.working.map { it.reps })
        benchSets(second, 62.5, 10, 9, 8, 8)
        workouts.finish(second, null, null)

        val report = workouts.report(second)!!
        assertEquals(first, report.previous!!.id)
        val records = report.records.getValue(benchId)
        assertTrue(records.any { it.type == RecordType.E1RM })
        assertEquals(62.5 * 10 + 62.5 * 9 + 62.5 * 8 * 2, report.tonnageKg, 1e-9)
        val e1rm = workouts.records(benchId).first().single { it.type == RecordType.E1RM }
        assertEquals(62.5 * (1 + 10 / 30.0), e1rm.value, 1e-9)
        assertEquals(chestDayId, workouts.lastCompleted(programId).first()?.dayId)
        assertEquals(2, workouts.exerciseHistory(benchId).first().size)
    }

    @Test fun fixingOrDeletingSetsAndSessionsRebuildsRecords() = runBlocking {
        val id = startChest()
        benchSets(id, 100.0, 5)
        workouts.finish(id, null, null)
        val benchId = session(id).exercises.first().exercise.id
        val setId = session(id).exercises.first().sets.single().id
        assertEquals(100.0, workouts.records(benchId).first().single { it.type == RecordType.MAX_WEIGHT_FOR_REPS }.value, 1e-9)

        // A typo: it was 80 kg.
        workouts.updateSet(setId, SetInput(weightKg = 80.0, reps = 5))
        assertEquals(80.0, workouts.records(benchId).first().single { it.type == RecordType.MAX_WEIGHT_FOR_REPS }.value, 1e-9)

        workouts.deleteSet(setId)
        assertTrue(workouts.records(benchId).first().isEmpty())

        workouts.delete(id)
        assertTrue(workouts.history.first().isEmpty())
        assertNull(workouts.session(id).first())
    }

    @Test fun warmUpsDoNotCountAndDiscardHidesTheSession() = runBlocking {
        val id = startChest()
        val bench = session(id).exercises.first()
        workouts.logSet(bench.id, SetInput(type = SetType.WARMUP, weightKg = 40.0, reps = 10))
        assertEquals(0, session(id).exercises.first().working.size)
        assertEquals(1, session(id).exercises.first().sets.size)
        workouts.discard(id)
        assertNull(workouts.active.first())
        assertNull(workouts.session(id).first())
        assertTrue(workouts.history.first().isEmpty())
        // A new session can start now.
        assertNotNull(startChest())
    }

    @Test fun exercisesCanBeAddedSwappedAndSkippedDuringTheSession() = runBlocking {
        val id = startChest()
        val lib = RoomExerciseRepository(db, clock)
        val curl = lib.idsByKey().getValue("barbell_curl")
        val added = workouts.addExercise(id, curl)
        val s = session(id)
        val addedItem = s.exercises.last()
        assertEquals(added, addedItem.id)
        assertEquals(3, addedItem.targets.sets)
        assertNull(addedItem.plannedExerciseId)

        val pulldown = s.exercises[1]
        workouts.replaceExercise(pulldown.id, lib.idsByKey().getValue("pull_up"))
        val swapped = session(id).exercises[1]
        assertEquals("pull_up", swapped.exercise.builtinKey)
        assertEquals(pulldown.exercise.id, swapped.replacedExerciseId)
        assertEquals(4, swapped.targets.sets)

        workouts.setSkipped(swapped.id, true)
        assertTrue(session(id).exercises[1].isDone)

        workouts.logSet(s.exercises.first().id, SetInput(weightKg = 60.0, reps = 8))
        try {
            workouts.replaceExercise(s.exercises.first().id, curl)
            fail("an exercise with sets cannot be swapped")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun bodyWeightMovesUseTheLatestWeighIn() = runBlocking {
        RoomWeightRepository(db, clock).add(100.0, TestData.today, LocalTime.of(7, 0), null, null)
        val id = startChest(VariantKind.MINIMAL)
        val pushUp = session(id).exercises.first()
        assertEquals("push_up", pushUp.exercise.builtinKey)
        workouts.logSet(pushUp.id, SetInput(reps = 10))
        workouts.finish(id, null, null)
        val pct = pushUp.exercise.bodyweightPercent!!
        assertEquals(100.0 * pct / 100 * 10, workouts.history.first().single().tonnageKg, 1e-9)
    }
}
