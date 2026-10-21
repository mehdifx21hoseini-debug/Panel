package app.fitos.personal.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.fitos.personal.R
import app.fitos.personal.feature.session.AndroidRestAlarm

/** Where tapping a notification opens the app. */
enum class Destination { HOME, HABITS, TRAINING, BACKUP, REPORT }

/** Channels, ids and the "open the app at…" intent shared by every non-timer notification. */
object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    /** Return to plan and automatic backup problems: rare and quiet. */
    const val CHANNEL_SYSTEM = "system"
    const val EXTRA_OPEN = "app.fitos.personal.OPEN"

    const val ID_RETURN = 201
    const val ID_AUTO_BACKUP = 202

    /** A stable id per reminder, apart from the timer's 101/102 and the ids above. */
    fun reminderId(reminderId: String): Int = 1_000 + (reminderId.hashCode() and 0xFFFF)

    fun canNotify(context: Context): Boolean = AndroidRestAlarm.canNotify(context)

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SYSTEM, context.getString(R.string.channel_system), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    fun open(context: Context, destination: Destination): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN, destination.name)
        return PendingIntent.getActivity(context, destination.ordinal + 10, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun destinationOf(intent: Intent?): Destination? =
        intent?.getStringExtra(EXTRA_OPEN)?.let { name -> Destination.entries.firstOrNull { it.name == name } }
}
