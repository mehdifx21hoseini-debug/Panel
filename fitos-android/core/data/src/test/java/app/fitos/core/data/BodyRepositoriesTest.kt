package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomMeasurementRepository
import app.fitos.core.data.repository.RoomWeightRepository
import app.fitos.core.domain.body.BuiltinSite
import app.fitos.core.domain.body.WeightCondition
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BodyRepositoriesTest {

    private lateinit var db: FitDatabase
    private val clock = TestData.clockAt(TestData.today)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun close() = db.close()

    @Test fun weightsCanBeBackdatedEditedDeletedAndRestored() = runBlocking {
        val repo = RoomWeightRepository(db, clock)
        val today = repo.add(90.0, WeightCondition.FASTED, null)
        val earlier = repo.add(91.2, TestData.today.minusDays(3), LocalTime.of(7, 30), null, "  ")
        assertEquals(listOf(earlier.id, today.id), repo.all.first().map { it.id }) // oldest first
        assertEquals(TestData.today.minusDays(3), earlier.day)
        assertEquals(null, earlier.note)

        repo.update(earlier.id, 91.0, TestData.today.minusDays(2), LocalTime.of(8, 0), WeightCondition.AFTER_MEAL, "بعد از صبحانه")
        val edited = repo.all.first().first()
        assertEquals(91.0, edited.weightKg, 1e-9)
        assertEquals(TestData.today.minusDays(2), edited.day)
        assertEquals(WeightCondition.AFTER_MEAL, edited.condition)

        repo.delete(today.id)
        assertEquals(listOf(earlier.id), repo.all.first().map { it.id })
        assertEquals(earlier.id, repo.latest.first()!!.id)
        repo.restore(today.id)
        assertEquals(today.id, repo.latest.first()!!.id)
    }

    @Test fun builtinSitesAreCreatedOnceInOrder() = runBlocking {
        val repo = RoomMeasurementRepository(db, clock)
        val first = repo.sites.first()
        assertEquals(BuiltinSite.entries.map { it.key }, first.map { it.builtinKey })
        repo.sites.first() // second read must not duplicate
        assertEquals(BuiltinSite.entries.size, repo.sites.first().size)
    }

    @Test fun measurementsAddEditDeleteAndCustomSites() = runBlocking {
        val repo = RoomMeasurementRepository(db, clock)
        val sites = repo.sites.first()
        val abdomen = sites.first { it.builtinKey == BuiltinSite.ABDOMEN.key }
        val arm = sites.first { it.builtinKey == BuiltinSite.ARM.key }
        repo.add(TestData.today, mapOf(abdomen.id to 110.0, arm.id to 38.5), "صبح")
        assertEquals(2, repo.all.first().size)

        val waist = repo.all.first().first { it.siteId == abdomen.id }
        repo.update(waist.id, TestData.today.minusDays(1), 109.5, null)
        assertEquals(109.5, repo.all.first().first { it.id == waist.id }.valueCm, 1e-9)
        repo.delete(waist.id)
        assertEquals(1, repo.all.first().size)
        repo.restore(waist.id)
        assertEquals(2, repo.all.first().size)

        val calf = repo.addSite(" ساق پا ")
        assertEquals("ساق پا", calf.name)
        assertTrue(repo.sites.first().last().id == calf.id)
        repo.setHidden(arm.id, true)
        assertTrue(repo.sites.first().first { it.id == arm.id }.isHidden)
    }
}
