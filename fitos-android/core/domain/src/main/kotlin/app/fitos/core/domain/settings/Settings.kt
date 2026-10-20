package app.fitos.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** How the app picks light or dark. Light is the default. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Small user preferences kept outside the database. */
interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
