package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.fitos.core.data.db.DatabaseUpgradeGuard
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.Migrations
import app.fitos.core.data.repository.RoomProfileRepository
import app.fitos.core.data.repository.RoomSetupRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FitDatabase::class.java)

    @Test fun dataSurvivesClosingAndReopening() = runBlocking {
        context.deleteDatabase(FitDatabase.NAME)
        val clock = TestData.clockAt(TestData.today)

        val first = FitDatabase.open(context)
        RoomSetupRepository(first, clock).completeSetup(TestData.setup())
        first.close()

        val reopened = FitDatabase.open(context)
        assertTrue(RoomSetupRepository(reopened, clock).isSetupComplete.first())
        assertEquals(180.0, RoomProfileRepository(reopened, clock).profile.first()!!.heightCm, 1e-9)
        reopened.close()
    }

    @Test fun exportedSchemaV1MatchesTheCode() {
        // Creates v1 from schemas/…/1.json, then opens it with the real database class, which
        // verifies the schema hash. Later versions add runMigrationsAndValidate checks here.
        helper.createDatabase(TEST_DB, 1).close()
        Room.databaseBuilder(context, FitDatabase::class.java, TEST_DB)
            .addMigrations(*Migrations.ALL)
            .build()
            .apply { openHelper.writableDatabase; close() }
    }

    @Test fun upgradeGuardCopiesOnlyWhenAnUpgradeIsComing() {
        context.deleteDatabase(GUARD_DB)
        Room.databaseBuilder(context, FitDatabase::class.java, GUARD_DB).build().apply { openHelper.writableDatabase; close() }
        val file = context.getDatabasePath(GUARD_DB)
        assertEquals(FitDatabase.VERSION, DatabaseUpgradeGuard.readUserVersion(file))

        assertNull(DatabaseUpgradeGuard.backupBeforeUpgrade(file, FitDatabase.VERSION, now = 1L))
        val copy = DatabaseUpgradeGuard.backupBeforeUpgrade(file, FitDatabase.VERSION + 1, now = 2L)
        assertNotNull(copy)
        assertTrue(copy!!.exists())
        assertEquals(file.length(), copy.length())

        // Only the newest three copies are kept.
        (3L..6L).forEach { DatabaseUpgradeGuard.backupBeforeUpgrade(file, FitDatabase.VERSION + 1, now = it) }
        val kept = File(file.parentFile, "pre-upgrade").listFiles { f -> f.name.matches(Regex("$GUARD_DB-v\\d+-\\d+")) }!!
        assertEquals(listOf("4", "5", "6"), kept.map { it.name.substringAfterLast('-') }.sorted())

        assertNull(DatabaseUpgradeGuard.backupBeforeUpgrade(File(file.parentFile, "missing.db"), 2, now = 9L))
    }

    private companion object {
        const val TEST_DB = "schema-test.db"
        const val GUARD_DB = "guard-test.db"
    }
}
