package app.fitos.personal

import android.content.Context
import app.fitos.core.data.DataGraph
import app.fitos.core.data.settings.DataStoreSettingsRepository
import app.fitos.core.domain.settings.SettingsRepository
import java.time.Clock

/** Manual dependency container: one instance per process, created in [FitOsApplication]. */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val data: DataGraph,
    val clock: Clock,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val clock = Clock.systemDefaultZone()
            return AppContainer(DataStoreSettingsRepository(context), DataGraph.open(context, clock), clock)
        }
    }
}
