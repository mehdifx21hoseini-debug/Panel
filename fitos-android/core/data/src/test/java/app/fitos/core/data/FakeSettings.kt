package app.fitos.core.data

import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeSettings(theme: ThemeMode = ThemeMode.LIGHT) : SettingsRepository {
    override val themeMode = MutableStateFlow(theme)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
    override val lastBackupAt = MutableStateFlow<Instant?>(null)
    override suspend fun setLastBackupAt(at: Instant) {
        lastBackupAt.value = at
    }
}
