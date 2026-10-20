package app.fitos.core.data

import app.fitos.core.domain.settings.AutoBackupSettings
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.core.domain.training.ReturnPlan
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class FakeSettings(theme: ThemeMode = ThemeMode.LIGHT) : SettingsRepository {
    override val themeMode = MutableStateFlow(theme)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
    override val lastBackupAt = MutableStateFlow<Instant?>(null)
    override suspend fun setLastBackupAt(at: Instant) {
        lastBackupAt.value = at
    }
    override val autoBackup = MutableStateFlow(AutoBackupSettings())
    override suspend fun updateAutoBackup(change: (AutoBackupSettings) -> AutoBackupSettings) = autoBackup.update(change)
    override val returnThresholdDays = MutableStateFlow(ReturnPlan.DEFAULT_THRESHOLD_DAYS)
    override suspend fun setReturnThresholdDays(days: Int) {
        returnThresholdDays.value = days
    }
    override val returnNotify = MutableStateFlow(true)
    override suspend fun setReturnNotify(on: Boolean) {
        returnNotify.value = on
    }
}
