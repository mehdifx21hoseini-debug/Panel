package app.fitos.personal

import android.content.Context
import app.fitos.core.data.settings.DataStoreSettingsRepository
import app.fitos.core.domain.settings.SettingsRepository
import java.time.Clock

/** Manual dependency container: one instance per process, created in [FitOsApplication]. */
class AppContainer(context: Context) {
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context)
    val clock: Clock = Clock.systemDefaultZone()
}
