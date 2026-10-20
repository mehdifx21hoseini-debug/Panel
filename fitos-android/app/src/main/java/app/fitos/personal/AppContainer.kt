package app.fitos.personal

import android.content.Context
import app.fitos.core.data.DataGraph
import app.fitos.core.data.backup.AppInfo
import app.fitos.core.data.settings.DataStoreSettingsRepository
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.personal.feature.session.AndroidRestAlarm
import app.fitos.personal.feature.session.NoRestAlarm
import app.fitos.personal.feature.session.RestAlarm
import java.time.Clock

/** Manual dependency container: one instance per process, created in [FitOsApplication]. */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val data: DataGraph,
    val clock: Clock,
    val restAlarm: RestAlarm = NoRestAlarm,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val clock = Clock.systemDefaultZone()
            val settings = DataStoreSettingsRepository(context)
            val app = AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            return AppContainer(settings, DataGraph.open(context, clock, settings, app), clock, AndroidRestAlarm(context.applicationContext))
        }
    }
}
