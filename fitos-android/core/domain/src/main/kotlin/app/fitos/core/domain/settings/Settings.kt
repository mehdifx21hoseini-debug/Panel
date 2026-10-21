package app.fitos.core.domain.settings

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Why the last automatic backup failed. */
enum class AutoBackupError {
    /** The chosen folder is gone or the app lost access to it. */
    FOLDER,
    /** Writing failed (storage full, file locked). */
    WRITE,
    /** The saved password could not be read back (e.g. after a restore on another phone). */
    PASSWORD,
}

/**
 * Weekly automatic backup into a folder the user picked. The password itself is never stored
 * here; [encrypted] only says one is kept (in the Android Keystore-protected store).
 */
data class AutoBackupSettings(
    val enabled: Boolean = false,
    /** Content URI of the folder; null until one is picked. */
    val folder: String? = null,
    val folderName: String? = null,
    val encrypted: Boolean = false,
    /** How many automatic backups stay in the folder; older ones made by the app are removed. */
    val keep: Int = DEFAULT_KEEP,
    val lastRunAt: Instant? = null,
    val lastError: AutoBackupError? = null,
) {
    companion object {
        const val DEFAULT_KEEP = 4
        val KEEP = 1..12
    }
}

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** When the user last saved a backup file (by hand or automatically); null if never. */
    val lastBackupAt: Flow<Instant?>

    suspend fun setLastBackupAt(at: Instant)

    val autoBackup: Flow<AutoBackupSettings>

    suspend fun updateAutoBackup(change: (AutoBackupSettings) -> AutoBackupSettings)

    /** Days without a workout before the app asks how to get back on track. */
    val returnThresholdDays: Flow<Int>

    suspend fun setReturnThresholdDays(days: Int)

    /** Whether a noticed break may send its one notification. */
    val returnNotify: Flow<Boolean>

    suspend fun setReturnNotify(on: Boolean)

    /** Saturday of the newest weekly report the user has opened; null if none. */
    val reportSeenWeek: Flow<LocalDate?>

    suspend fun setReportSeenWeek(weekStart: LocalDate)

    /** Saturday of the week whose calorie suggestion was set aside, so it is not offered again that week. */
    val adaptiveDismissedWeek: Flow<LocalDate?>

    suspend fun setAdaptiveDismissedWeek(weekStart: LocalDate)
}
