package app.fitos.personal

import android.content.Context
import androidx.core.net.toUri
import app.fitos.core.data.DataGraph
import app.fitos.core.data.backup.AppInfo
import app.fitos.core.data.backup.AutoBackupRunner
import app.fitos.core.data.backup.BackupFolder
import app.fitos.core.data.backup.DocumentTreeFolder
import app.fitos.core.data.backup.FileBackupFolder
import app.fitos.core.data.backup.KeystoreSecretStore
import app.fitos.core.data.backup.SecretStore
import app.fitos.core.data.settings.DataStoreSettingsRepository
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.training.ReturnPlanChecker
import app.fitos.personal.feature.session.AndroidRestAlarm
import app.fitos.personal.feature.session.NoRestAlarm
import app.fitos.personal.feature.session.RestAlarm
import app.fitos.personal.notify.AlarmReminderScheduler
import app.fitos.personal.notify.BackgroundJobs
import app.fitos.personal.notify.NoBackgroundJobs
import app.fitos.personal.notify.NoReminderScheduler
import app.fitos.personal.notify.ReminderScheduler
import app.fitos.personal.notify.WorkManagerJobs
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container: one instance per process, created in [FitOsApplication]. */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val data: DataGraph,
    val clock: Clock,
    val restAlarm: RestAlarm = NoRestAlarm,
    val reminderScheduler: ReminderScheduler = NoReminderScheduler,
    val jobs: BackgroundJobs = NoBackgroundJobs,
    val secrets: SecretStore = MemorySecretStore(),
    folderFor: (String) -> BackupFolder = { FileBackupFolder(File(it)) },
) {
    /** For work that must outlive a screen: receivers, workers and the start-up sync. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val autoBackup = AutoBackupRunner(data.backup, settingsRepository, clock, folderFor, secrets)

    val returnChecker = ReturnPlanChecker(data.workouts, data.programs, data.returnPlan, settingsRepository, clock)

    companion object {
        fun create(context: Context): AppContainer {
            val app = context.applicationContext
            val clock = Clock.systemDefaultZone()
            val settings = DataStoreSettingsRepository(app)
            val data = DataGraph.open(app, clock, settings, AppInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            return AppContainer(
                settingsRepository = settings,
                data = data,
                clock = clock,
                restAlarm = AndroidRestAlarm(app),
                reminderScheduler = AlarmReminderScheduler(app, data.reminders, data.habits, clock),
                jobs = WorkManagerJobs(app),
                secrets = KeystoreSecretStore(app),
                folderFor = { DocumentTreeFolder(app, it.toUri()) },
            )
        }
    }
}

/** Keeps the secret in memory only; for tests. */
class MemorySecretStore : SecretStore {
    private var value: CharArray? = null
    override fun get(): CharArray? = value?.copyOf()
    override fun put(value: CharArray?) {
        this.value = value?.copyOf()
    }
}
