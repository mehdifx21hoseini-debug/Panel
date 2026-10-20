package app.fitos.core.domain.settings

import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** When the user last saved a backup file; null if never. */
    val lastBackupAt: Flow<Instant?>

    suspend fun setLastBackupAt(at: Instant)
}
