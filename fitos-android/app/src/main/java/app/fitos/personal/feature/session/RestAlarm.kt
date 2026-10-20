package app.fitos.personal.feature.session

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fitos.personal.R
import java.time.Instant

/** Tells the user when rest is over, also with the screen off or the app in the background. */
interface RestAlarm {
    fun schedule(endsAt: Instant, next: String)
    fun cancel()
}

/** For tests and previews. */
object NoRestAlarm : RestAlarm {
    override fun schedule(endsAt: Instant, next: String) = Unit
    override fun cancel() = Unit
}

/**
 * A silent notification counts the rest down (system chronometer, so it keeps going while the
 * app sleeps) and an alarm at the end posts a second one that sounds and vibrates. Exact alarms
 * are used when Android allows them; otherwise Android may deliver the end a little late, and the
 * countdown in the app and in the notification stays exact either way. Without the notification
 * permission only the in-app timer works.
 */
class AndroidRestAlarm(private val context: Context) : RestAlarm {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Instant, next: String) {
        ensureChannels(context)
        val remaining = endsAt.toEpochMilli() - System.currentTimeMillis()
        if (remaining <= 0) return
        if (canNotify(context)) {
            val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(context.getString(R.string.rest_notification_title))
                .setContentText(context.getString(R.string.rest_notification_next, next))
                .setWhen(endsAt.toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(true)
                .setTimeoutAfter(remaining)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openApp(context))
                .build()
            try {
                NotificationManagerCompat.from(context).notify(ID_RUNNING, notification)
            } catch (_: SecurityException) {
                // Permission withdrawn between the check and the post: the in-app timer still runs.
            }
        }
        val done = PendingIntent.getBroadcast(
            context, 0, Intent(context, RestDoneReceiver::class.java).putExtra(EXTRA_NEXT, next),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), done)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), done)
        }
    }

    override fun cancel() {
        val done = PendingIntent.getBroadcast(
            context, 0, Intent(context, RestDoneReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        done?.let { alarms.cancel(it) }
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        NotificationManagerCompat.from(context).cancel(ID_DONE)
    }

    companion object {
        const val CHANNEL_RUNNING = "rest_running"
        const val CHANNEL_DONE = "rest_done"
        const val ID_RUNNING = 101
        const val ID_DONE = 102
        const val EXTRA_NEXT = "next"

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.channel_rest_running), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_rest_done), NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                    setShowBadge(false)
                },
            )
        }

        /** Brings the app back as it was (the session screen when that was open). */
        fun openApp(context: Context): PendingIntent {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** Fires at the end of the rest and posts the alert. */
class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(AndroidRestAlarm.ID_RUNNING)
        if (!AndroidRestAlarm.canNotify(context)) return
        AndroidRestAlarm.ensureChannels(context)
        val next = intent.getStringExtra(AndroidRestAlarm.EXTRA_NEXT).orEmpty()
        val notification = NotificationCompat.Builder(context, AndroidRestAlarm.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(context.getString(R.string.rest_done_title))
            .setContentText(context.getString(R.string.rest_notification_next, next))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60_000L)
            .setContentIntent(AndroidRestAlarm.openApp(context))
            .build()
        try {
            manager.notify(AndroidRestAlarm.ID_DONE, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post.
        }
    }
}
