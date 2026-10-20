package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.backup.AppInfo
import app.fitos.core.data.backup.RoomBackupService
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomMeasurementRepository
import app.fitos.core.data.repository.RoomSetupRepository
import app.fitos.core.data.repository.RoomWeightRepository
import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.backup.BackupException
import app.fitos.core.domain.settings.ThemeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestData.clockAt(TestData.today)
    private val opened = mutableListOf<FitDatabase>()

    private fun newDb() = Room.inMemoryDatabaseBuilder(context, FitDatabase::class.java).allowMainThreadQueries().build().also { opened += it }

    private fun service(db: FitDatabase, settings: FakeSettings = FakeSettings()) =
        RoomBackupService(context, db, settings, clock, AppInfo("0.3.0", 3), iterations = 10_000)

    @Before fun clean() {
        File(context.filesDir, "safety-backups").deleteRecursively()
        File(context.filesDir, "photos").deleteRecursively()
    }

    @After fun close() = opened.forEach { it.close() }

    /** A database with a bit of everything stage 1 and 2 write. */
    private fun filledDb(): FitDatabase = newDb().also { db ->
        runBlocking {
            RoomSetupRepository(db, clock).completeSetup(TestData.setup())
            val weights = RoomWeightRepository(db, clock)
            (1..40).forEach { weights.add(90.0 - it * 0.05, TestData.today.minusDays(it.toLong()), LocalTime.of(7, 15), null, if (it % 7 == 0) "یادداشت «$it»\nخط دوم" else null) }
            weights.delete(weights.all.first().first().id) // soft-deleted rows are data too
            val measurements = RoomMeasurementRepository(db, clock)
            val sites = measurements.sites.first()
            measurements.add(TestData.today, sites.take(3).associate { it.id to 100.5 }, null)
            measurements.addSite("ساق")
        }
    }

    /** Every row of every table, as text, for exact comparison. */
    private fun dump(db: FitDatabase): Map<String, List<String>> {
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('android_metadata','sqlite_sequence','room_master_table')").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        return tables.associateWith { table ->
            db.openHelper.readableDatabase.query("SELECT * FROM `$table` ORDER BY 1").use { c ->
                buildList {
                    while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { i -> "${c.getType(i)}:${c.getString(i)}" })
                }
            }
        }
    }

    private fun export(db: FitDatabase, password: String? = null, settings: FakeSettings = FakeSettings()): ByteArray {
        val out = ByteArrayOutputStream()
        runBlocking { service(db, settings).export(out, password?.toCharArray()) }
        return out.toByteArray()
    }

    private fun expectError(error: BackupError, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected $error")
        } catch (e: BackupException) {
            assertEquals(error, e.error)
        }
    }

    @Test fun plainRoundTripRestoresEveryRowExactly() = runBlocking {
        val source = filledDb()
        val bytes = export(source)
        val target = newDb()
        RoomSetupRepository(target, clock).completeSetup(TestData.setup().copy(currentWeightKg = 70.0)) // different data, must be replaced
        val restore = service(target)
        val staged = restore.stage(ByteArrayInputStream(bytes), null)
        assertEquals(41, staged.summary.counts["weight_entry"]) // first weigh-in from setup + 40
        assertEquals(1, staged.summary.counts["profile"])
        assertFalse(staged.summary.encrypted)
        assertEquals(FitDatabase.VERSION, staged.summary.schemaVersion)
        restore.restore(staged)
        assertEquals(dump(source), dump(target))
    }

    @Test fun encryptedRoundTripNeedsTheRightPassword() = runBlocking {
        val source = filledDb()
        val bytes = export(source, password = "یک رمز طولانی")
        assertTrue(service(newDb()).isEncrypted(ByteArrayInputStream(bytes)))
        val target = newDb()
        val restore = service(target)
        expectError(BackupError.PASSWORD_NEEDED) { restore.stage(ByteArrayInputStream(bytes), null) }
        expectError(BackupError.WRONG_PASSWORD_OR_DAMAGED) { restore.stage(ByteArrayInputStream(bytes), "رمز اشتباه".toCharArray()) }
        val staged = restore.stage(ByteArrayInputStream(bytes), "یک رمز طولانی".toCharArray())
        assertTrue(staged.summary.encrypted)
        restore.restore(staged)
        assertEquals(dump(source), dump(target))
        expectError(BackupError.DAMAGED) { restore.stage(ByteArrayInputStream(bytes.copyOf(bytes.size - 30)), "یک رمز طولانی".toCharArray()) }
    }

    @Test fun exportRemembersTheTimeAndThemeTravelsAlong() = runBlocking {
        val settings = FakeSettings(ThemeMode.DARK)
        val bytes = export(filledDb(), settings = settings)
        assertEquals(clock.instant(), settings.lastBackupAt.value)
        val targetSettings = FakeSettings(ThemeMode.LIGHT)
        val restore = service(newDb(), targetSettings)
        restore.restore(restore.stage(ByteArrayInputStream(bytes), null))
        assertEquals(ThemeMode.DARK, targetSettings.themeMode.value)
    }

    @Test fun restoreKeepsASafetyCopyOfTheReplacedData() = runBlocking {
        val before = filledDb()
        val beforeDump = dump(before)
        val other = newDb().also { runBlocking { RoomSetupRepository(it, clock).completeSetup(TestData.setup().copy(currentWeightKg = 70.0)) } }
        val restore = service(before)
        restore.restore(restore.stage(ByteArrayInputStream(export(other)), null))
        assertNotEquals(beforeDump, dump(before))

        val safety = restore.safetyBackups()
        assertEquals(1, safety.size)
        restore.restore(restore.stageSafetyBackup(safety.first().id))
        assertEquals(beforeDump, dump(before))
        // Restoring the safety copy itself left a safety copy of the replaced state.
        assertEquals(2, restore.safetyBackups().size)
    }

    @Test fun noSafetyCopyWhenNothingWouldBeLost() = runBlocking {
        val bytes = export(filledDb())
        val restore = service(newDb())
        restore.restore(restore.stage(ByteArrayInputStream(bytes), null))
        assertTrue(restore.safetyBackups().isEmpty())
    }

    @Test fun changedContentFailsTheChecksum() {
        val bytes = rewrite(export(filledDb())) { name, content ->
            if (name == "data/weight_entry.json") String(content).replace("89.9", "79.9").toByteArray() else content
        }
        expectError(BackupError.DAMAGED) { service(newDb()).stage(ByteArrayInputStream(bytes), null) }
    }

    @Test fun wrongRowCountIsDamage() {
        val bytes = rewrite(export(filledDb())) { name, content ->
            if (name == "manifest.json") String(content).replaceFirst("\"rows\": 41", "\"rows\": 42").toByteArray() else content
        }
        expectError(BackupError.DAMAGED) { service(newDb()).stage(ByteArrayInputStream(bytes), null) }
    }

    @Test fun newerBackupsAreRefusedWithAClearReason() {
        val bytes = rewrite(export(filledDb())) { name, content ->
            if (name == "manifest.json") String(content).replace("\"schemaVersion\": ${FitDatabase.VERSION}", "\"schemaVersion\": 99").toByteArray() else content
        }
        expectError(BackupError.NEWER_APP) { service(newDb()).stage(ByteArrayInputStream(bytes), null) }
    }

    @Test fun otherFilesAreNotBackups() {
        expectError(BackupError.NOT_A_BACKUP) { service(newDb()).stage(ByteArrayInputStream("hello".toByteArray()), null) }
        val zipWithoutManifest = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("data/profile.json")); it.write("{}".toByteArray()); it.closeEntry() }
        }.toByteArray()
        expectError(BackupError.NOT_A_BACKUP) { service(newDb()).stage(ByteArrayInputStream(zipWithoutManifest), null) }
    }

    @Test fun pathTricksInsideTheZipAreRejected() {
        val evil = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("../../evil.txt")); it.write(1); it.closeEntry() }
        }.toByteArray()
        expectError(BackupError.DAMAGED) { service(newDb()).stage(ByteArrayInputStream(evil), null) }
        assertFalse(File(context.cacheDir.parentFile, "evil.txt").exists())
    }

    @Test fun cutShortPlainFileIsDamage() {
        val bytes = export(filledDb())
        expectError(BackupError.DAMAGED) { service(newDb()).stage(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)), null) }
    }

    @Test fun failedStagingLeavesCurrentDataUntouched() = runBlocking {
        val db = filledDb()
        val before = dump(db)
        val bad = rewrite(export(newDb().also { runBlocking { RoomSetupRepository(it, clock).completeSetup(TestData.setup()) } })) { name, content ->
            if (name == "data/profile.json") content.copyOf(content.size - 3) else content
        }
        expectError(BackupError.DAMAGED) { service(db).stage(ByteArrayInputStream(bad), null) }
        assertEquals(before, dump(db))
        assertNotNull(db)
    }

    /** Re-packs a backup ZIP, letting [change] alter entries (the manifest is not updated). */
    private fun rewrite(bytes: ByteArray, change: (String, ByteArray) -> ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val content = input.readBytes()
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(change(entry.name, content))
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

}
