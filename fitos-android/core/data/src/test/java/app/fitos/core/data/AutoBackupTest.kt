package app.fitos.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.backup.AutoBackupRunner
import app.fitos.core.data.backup.BackupCrypto
import app.fitos.core.data.backup.FileBackupFolder
import app.fitos.core.data.backup.SecretStore
import app.fitos.core.domain.settings.AutoBackupError
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutoBackupTest {

    private val clock = TestData.clockAt(TestData.today)
    private val settings = FakeSettings()
    private val data = DataGraph.inMemory(ApplicationProvider.getApplicationContext<Context>(), clock, settings)
    private val dir: File = Files.createTempDirectory("auto").toFile()
    private val secret = object : SecretStore {
        var value: CharArray? = null
        override fun get() = value?.copyOf()
        override fun put(value: CharArray?) {
            this.value = value
        }
    }
    private val runner = AutoBackupRunner(data.backup, settings, clock, { FileBackupFolder(File(it)) }, secret)

    @After fun close() {
        data.close()
        dir.deleteRecursively()
    }

    @Test fun nothingHappensWhileOff() = runBlocking {
        settings.updateAutoBackup { it.copy(folder = dir.path) }
        assertNull(runner.run())
        assertEquals(0, dir.list()!!.size)
    }

    @Test fun writesADatedFileAndKeepsOnlyTheNewestAutomaticOnes() = runBlocking {
        data.setup.completeSetup(TestData.setup())
        listOf("FitOS-auto-1405-06-01.fitbak", "FitOS-auto-1405-06-08.fitbak", "FitOS-auto-1405-06-15.fitbak", "FitOS-auto-1405-06-22.fitbak", "FitOS-auto-1405-06-29.fitbak")
            .forEach { File(dir, it).writeText("old") }
        // Hand-made backups and other files are never touched.
        File(dir, "FitOS-1405-05-01.fitbak").writeText("manual")
        File(dir, "notes.txt").writeText("x")
        settings.updateAutoBackup { it.copy(enabled = true, folder = dir.path, keep = 4) }

        assertNull(runner.run())
        val name = AutoBackupRunner.fileName(TestData.today)
        assertEquals("FitOS-auto-1405-07-28.fitbak", name)
        assertTrue(File(dir, name).length() > 100)
        assertEquals(
            setOf("FitOS-auto-1405-07-28.fitbak", "FitOS-auto-1405-06-29.fitbak", "FitOS-auto-1405-06-22.fitbak", "FitOS-auto-1405-06-15.fitbak", "FitOS-1405-05-01.fitbak", "notes.txt"),
            dir.list()!!.toSet(),
        )
        assertEquals(clock.instant(), settings.autoBackup.value.lastRunAt)
        assertNull(settings.autoBackup.value.lastError)
        assertEquals(clock.instant(), settings.lastBackupAt.value)
        // The file is a real backup that can be checked.
        val staged = File(dir, name).inputStream().use { data.backup.stage(it, null) }
        assertEquals(1, staged.summary.counts["profile"])
    }

    @Test fun aLostFolderOrPasswordIsReported() = runBlocking {
        settings.updateAutoBackup { it.copy(enabled = true, folder = File(dir, "gone").path) }
        assertEquals(AutoBackupError.FOLDER, runner.run())
        assertEquals(AutoBackupError.FOLDER, settings.autoBackup.value.lastError)

        settings.updateAutoBackup { it.copy(folder = dir.path, encrypted = true) }
        assertEquals(AutoBackupError.PASSWORD, runner.run())

        secret.put("a long password".toCharArray())
        assertNull(runner.run(force = true))
        val bytes = File(dir, AutoBackupRunner.fileName(TestData.today)).readBytes()
        assertTrue(BackupCrypto.startsWithMagic(bytes.copyOf(BackupCrypto.MAGIC.size)))
        assertNotNull(settings.autoBackup.value.lastRunAt)
    }
}
