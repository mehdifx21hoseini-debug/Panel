package app.fitos.personal

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch

class FitOsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        // Alarms are lost on force-stop and some updates; setting them again on every start is cheap.
        container.scope.launch {
            runCatching { container.reminderScheduler.sync() }.onFailure { Log.w(TAG, "reminder sync failed", it) }
            runCatching { container.jobs.ensureScheduled() }.onFailure { Log.w(TAG, "background jobs not scheduled", it) }
        }
    }

    private companion object {
        const val TAG = "FitOS"
    }
}
