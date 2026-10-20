package app.fitos.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A habit. System habits ([systemType]) are filled from their own logs (water, sleep, training...). */
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String?,
    /** boolean, quantity or duration. */
    val kind: String,
    val systemType: String?,
    /** daily or weekly. */
    val frequency: String,
    val target: Double?,
    val unit: String?,
    /** Bit mask of ISO weekdays (bit 0 = Monday) when the habit is due; null = every day. */
    val weekdaysMask: Int?,
    val position: Int,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** One answer per habit per day. No row means "not logged", which is never shown as done. */
@Entity(
    tableName = "habit_log",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habitId", "day"], unique = true)],
)
data class HabitLogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val day: Long,
    /** done, not_done or skipped. */
    val status: String,
    val value: Double?,
    val note: String?,
    val loggedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "water_log", indices = [Index("day")])
data class WaterLogEntity(
    @PrimaryKey val id: String,
    val day: Long,
    val loggedAt: Long,
    val zoneId: String,
    val amountMl: Int,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Sleep is filed under the day of waking up. */
@Entity(tableName = "sleep_log", indices = [Index("day")])
data class SleepLogEntity(
    @PrimaryKey val id: String,
    val day: Long,
    val bedtimeAt: Long?,
    val wakeAt: Long?,
    val durationMinutes: Int,
    /** 1..5, or null when not rated. */
    val quality: Int?,
    val note: String?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "activity_log", indices = [Index("day")])
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val day: Long,
    /** walk, steps, home_workout or other. */
    val kind: String,
    val steps: Int?,
    val durationMinutes: Int?,
    val distanceM: Double?,
    val note: String?,
    val loggedAt: Long,
    val zoneId: String,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "reminder",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    /** habit, weigh_in, water, workout, backup or weekly_report. */
    val type: String,
    val habitId: String?,
    val minuteOfDay: Int,
    /** Bit mask of ISO weekdays (bit 0 = Monday). */
    val weekdaysMask: Int,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
