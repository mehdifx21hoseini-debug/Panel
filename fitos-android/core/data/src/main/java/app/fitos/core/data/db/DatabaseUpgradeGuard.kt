package app.fitos.core.data.db

import java.io.File
import java.io.RandomAccessFile

/**
 * Before a start that will migrate the database, copies the database files to `pre-upgrade/`
 * next to it, so a failed or faulty migration never costs data. Keeps the newest [KEEP] copies.
 *
 * The schema version is read from the SQLite header (user_version, offset 60) without opening
 * the database, so nothing is changed before the copy is made.
 */
object DatabaseUpgradeGuard {
    private const val KEEP = 3
    private const val USER_VERSION_OFFSET = 60L
    private val SIDE_FILES = listOf("", "-wal", "-shm")

    /** Returns the copied main file, or null when there was nothing to upgrade. */
    fun backupBeforeUpgrade(database: File, targetVersion: Int, now: Long): File? {
        if (!database.exists() || database.length() < 100) return null
        val current = readUserVersion(database)
        if (current <= 0 || current >= targetVersion) return null

        val dir = File(database.parentFile, "pre-upgrade").apply { mkdirs() }
        val stamp = "${database.name}-v$current-$now"
        SIDE_FILES.forEach { suffix ->
            val source = File(database.path + suffix)
            if (source.exists()) source.copyTo(File(dir, stamp + suffix), overwrite = true)
        }
        prune(dir, database.name)
        return File(dir, stamp)
    }

    internal fun readUserVersion(database: File): Int = RandomAccessFile(database, "r").use { file ->
        file.seek(USER_VERSION_OFFSET)
        file.readInt()
    }

    private fun prune(dir: File, name: String) {
        val copies = dir.listFiles { f -> f.name.startsWith("$name-v") && !f.name.endsWith("-wal") && !f.name.endsWith("-shm") }
            ?.sortedByDescending { it.name.substringAfterLast('-').toLongOrNull() ?: 0L }
            ?: return
        copies.drop(KEEP).forEach { old ->
            SIDE_FILES.forEach { suffix -> File(old.path + suffix).delete() }
        }
    }
}
