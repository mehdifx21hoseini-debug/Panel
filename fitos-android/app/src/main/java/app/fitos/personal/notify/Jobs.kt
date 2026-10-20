package app.fitos.personal.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.fitos.core.domain.settings.AutoBackupError
import app.fitos.personal.FitOsApplication
import app.fitos.personal.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Work that may run at a flexible time: a daily check (return to plan, alarms re-set) and the
 * weekly automatic backup. Exact clock times are the reminders' job (AlarmManager), not this.
 */
interface BackgroundJobs {
    /** Idempotent: registers the daily check, and the weekly backup when it is on. */
    suspend fun ensureScheduled()

    /** Starts or stops the weekly backup after the user changed the setting. */
    fun autoBackupChanged(enabled: Boolean)
}

/** For tests and previews. */
object NoBackgroundJobs : BackgroundJobs {
    override suspend fun ensureScheduled() = Unit
    override fun autoBackupChanged(enabled: Boolean) = Unit
}

class WorkManagerJobs(private val context: Context) : BackgroundJobs {
    private val work get() = WorkManager.getInstance(context)

    override suspend fun ensureScheduled() {
        work.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS).build())
        val container = (context.applicationContext as FitOsApplication).container
        if (container.settingsRepository.autoBackup.first().enabled) schedule(ExistingPeriodicWorkPolicy.KEEP)
    }

    override fun autoBackupChanged(enabled: Boolean) {
        if (enabled) schedule(ExistingPeriodicWorkPolicy.UPDATE) else work.cancelUniqueWork(BACKUP)
    }

    private fun schedule(policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(BACKUP, policy, request)
    }

    private companion object {
        const val DAILY = "daily_check"
        const val BACKUP = "auto_backup"
    }
}

/** Once a day: re-set the alarms, and notice a break in training (one notification per break). */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FitOsApplication).container
        container.reminderScheduler.sync()
        val event = container.returnChecker.check()
        if (event != null && container.settingsRepository.returnNotify.first()) {
            Notifications.ensureChannels(applicationContext)
            val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_SYSTEM)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(applicationContext.getString(R.string.return_notification_title))
                .setContentText(applicationContext.getString(R.string.return_notification_body))
                .setAutoCancel(true)
                .setContentIntent(Notifications.open(applicationContext, Destination.HOME))
                .build()
            Notifications.post(applicationContext, Notifications.ID_RETURN, notification)
        }
        return Result.success()
    }
}

/** The weekly backup into the chosen folder; a failure is shown once, with what to do. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FitOsApplication).container
        val error = container.autoBackup.run() ?: return Result.success().also { Notifications.cancel(applicationContext, Notifications.ID_AUTO_BACKUP) }
        Notifications.ensureChannels(applicationContext)
        val reason = when (error) {
            AutoBackupError.FOLDER -> R.string.auto_backup_error_folder
            AutoBackupError.WRITE -> R.string.auto_backup_error_write
            AutoBackupError.PASSWORD -> R.string.auto_backup_error_password
        }
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_SYSTEM)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(applicationContext.getString(R.string.auto_backup_failed_title))
            .setContentText(applicationContext.getString(reason))
            .setStyle(NotificationCompat.BigTextStyle().bigText(applicationContext.getString(reason)))
            .setAutoCancel(true)
            .setContentIntent(Notifications.open(applicationContext, Destination.BACKUP))
            .build()
        Notifications.post(applicationContext, Notifications.ID_AUTO_BACKUP, notification)
        // A write error may pass (storage freed); a lost folder or password needs the user.
        return if (error == AutoBackupError.WRITE && runAttemptCount < 2) Result.retry() else Result.success()
    }
}
