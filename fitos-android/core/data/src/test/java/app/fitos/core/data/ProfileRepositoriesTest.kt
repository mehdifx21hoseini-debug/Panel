package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomNutritionTargetRepository
import app.fitos.core.data.repository.RoomProfileRepository
import app.fitos.core.data.repository.RoomSetupRepository
import app.fitos.core.data.repository.RoomWeightGoalRepository
import app.fitos.core.data.repository.RoomWeightRepository
import app.fitos.core.domain.body.WeightCondition
import app.fitos.core.domain.nutrition.AdaptiveBasisNote
import app.fitos.core.domain.nutrition.TargetOrigin
import app.fitos.core.domain.profile.GoalStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProfileRepositoriesTest {

    private lateinit var db: FitDatabase
    private val clock = TestData.clockAt(TestData.today)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    @Test fun setupWritesEverythingInOneGo() = runBlocking {
        val setup = RoomSetupRepository(db, clock)
        assertFalse(setup.isSetupComplete.first())

        setup.completeSetup(TestData.setup())

        assertTrue(setup.isSetupComplete.first())
        val profile = RoomProfileRepository(db, clock).profile.first()!!
        assertEquals("تست", profile.displayName) // trimmed
        assertEquals(TestData.profile.copy(displayName = "تست"), profile)

        val weight = RoomWeightRepository(db, clock).latest.first()!!
        assertEquals(90.0, weight.weightKg, 1e-9)
        assertEquals(TestData.today, weight.day)
        assertEquals("Asia/Tehran", weight.zone.id)

        val goal = RoomWeightGoalRepository(db, clock).activeGoal.first()!!
        assertEquals(80.0, goal.targetWeightKg, 1e-9)
        assertEquals(90.0, goal.startWeightKg!!, 1e-9)
        assertEquals(TestData.today.plusWeeks(12), goal.reviewDate)

        val target = RoomNutritionTargetRepository(db, clock).effectiveOn(TestData.today).first()!!
        assertEquals(TestData.setup().target, target) // assumptions survive the JSON round trip
    }

    @Test fun targetsAreDatedAndKeepHistory() = runBlocking {
        val repo = RoomNutritionTargetRepository(db, clock)
        val first = TestData.setup().target
        repo.save(first)
        assertNull(repo.effectiveOn(TestData.today.minusDays(1)).first())
        assertEquals(first, repo.effectiveOn(TestData.today.plusDays(30)).first())

        // Same day: replaced. Later day: new period, older days keep the old target.
        repo.save(first.copy(kcal = 2400, origin = TargetOrigin.MANUAL, assumptions = null))
        assertEquals(2400, repo.effectiveOn(TestData.today).first()!!.kcal)
        val later = TestData.today.plusDays(14)
        repo.save(first.copy(effectiveFrom = later, kcal = 2300))
        assertEquals(2400, repo.effectiveOn(later.minusDays(1)).first()!!.kcal)
        assertEquals(2300, repo.effectiveOn(later).first()!!.kcal)
        assertEquals(2, db.nutritionTargetDao().observeHistory().first().size)
    }

    @Test fun anAcceptedAdjustmentKeepsItsBasis() = runBlocking {
        val repo = RoomNutritionTargetRepository(db, clock)
        val base = TestData.setup().target
        val note = AdaptiveBasisNote(days = 21, meanIntakeKcal = 2512.4, rateKgPerWeek = -0.31, previousKcal = 2500)
        val adaptive = base.copy(kcal = 2350, origin = TargetOrigin.ADAPTIVE, assumptions = base.assumptions!!.copy(tdee = 2830.0, adaptive = note))
        repo.save(adaptive)
        assertEquals(adaptive, repo.effectiveOn(TestData.today).first())
    }

    @Test fun newGoalReplacesTheActiveOne() = runBlocking {
        val repo = RoomWeightGoalRepository(db, clock)
        repo.setActiveGoal(85.0, null, 90.0, TestData.today)
        repo.setActiveGoal(80.0, TestData.today.plusWeeks(8), 90.0, TestData.today)
        assertEquals(80.0, repo.activeGoal.first()!!.targetWeightKg, 1e-9)
        val statuses = db.query("SELECT status FROM weight_goal ORDER BY targetWeightKg", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals(listOf(GoalStatus.ACTIVE.code, GoalStatus.REPLACED.code), statuses)
        repo.clearActiveGoal()
        assertNull(repo.activeGoal.first())
    }

    @Test fun profileUpdateKeepsCreationTime() = runBlocking {
        val repo = RoomProfileRepository(db, clock)
        repo.save(TestData.profile)
        val created = db.profileDao().get()!!.createdAt
        RoomProfileRepository(db, TestData.clockAt(TestData.today.plusDays(3))).save(TestData.profile.copy(heightCm = 181.0))
        val row = db.profileDao().get()!!
        assertEquals(created, row.createdAt)
        assertTrue(row.updatedAt > created)
        assertEquals(181.0, row.heightCm, 1e-9)
    }

    @Test fun latestWeightIsTheNewest() = runBlocking {
        RoomWeightRepository(db, TestData.clockAt(TestData.today, hour = 7)).add(91.0, WeightCondition.FASTED, " ")
        RoomWeightRepository(db, TestData.clockAt(TestData.today.plusDays(1), hour = 7)).add(90.4, null, "بعد از سفر")
        val latest = RoomWeightRepository(db, clock).latest.first()
        assertNotNull(latest)
        assertEquals(90.4, latest!!.weightKg, 1e-9)
        assertEquals("بعد از سفر", latest.note)
    }

    @Test fun lateNightEntryKeepsItsLocalDay() = runBlocking {
        // 23:30 Tehran is still the same calendar day even though UTC has moved on.
        val lateClock = java.time.Clock.fixed(TestData.today.atTime(23, 30).atZone(TestData.zone).toInstant(), TestData.zone)
        val entry = RoomWeightRepository(db, lateClock).add(90.0, null, null)
        assertEquals(TestData.today, entry.day)
    }
}
