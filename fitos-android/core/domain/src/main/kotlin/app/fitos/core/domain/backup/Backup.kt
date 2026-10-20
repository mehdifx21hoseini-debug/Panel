package app.fitos.core.domain.backup

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId

/** What a backup file contains, read from its manifest before anything is replaced. */
data class BackupSummary(
    val createdAt: Instant,
    val zone: ZoneId,
    val appVersionName: String,
    val formatVersion: Int,
    val schemaVersion: Int,
    /** Row count per table. */
    val counts: Map<String, Int>,
    val photoCount: Int,
    val encrypted: Boolean,
)

/** A checked backup unpacked into private storage, ready to restore. */
data class StagedBackup(val token: String, val summary: BackupSummary)

data class SafetyBackup(val id: String, val createdAt: Instant, val sizeBytes: Long)

enum class BackupError {
    /** Not a FitOS backup at all. */
    NOT_A_BACKUP,
    /** Encrypted and no password was given. */
    PASSWORD_NEEDED,
    /** Encrypted, and the password is wrong or the file was changed. */
    WRONG_PASSWORD_OR_DAMAGED,
    /** A checksum or count does not match, or the file is cut short. */
    DAMAGED,
    /** Made by a newer app version; update the app first. */
    NEWER_APP,
    /** Reading or writing failed (storage full, file moved). */
    IO,
}

class BackupException(val error: BackupError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Backup and restore of all app data (docs/backup-format.md). Nothing leaves the phone unless
 * the user saves the file somewhere; an optional password encrypts it with AES-256-GCM.
 */
interface BackupService {
    /** Writes a complete backup to [output] and returns what it contains. */
    suspend fun export(output: OutputStream, password: CharArray?): BackupSummary

    /** True when the file is an encrypted FitOS backup (a password will be needed). */
    suspend fun isEncrypted(input: InputStream): Boolean

    /** Decrypts (if needed), unpacks and verifies a backup without touching current data. */
    suspend fun stage(input: InputStream, password: CharArray?): StagedBackup

    /**
     * Replaces all current data with [staged], after saving the current data as a safety backup.
     * Either everything is replaced or nothing is.
     */
    suspend fun restore(staged: StagedBackup)

    suspend fun safetyBackups(): List<SafetyBackup>

    suspend fun stageSafetyBackup(id: String): StagedBackup
}
