package app.fitos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.fitos.core.data.db.entity.ActivityLogEntity
import app.fitos.core.data.db.entity.HabitEntity
import app.fitos.core.data.db.entity.HabitLogEntity
import app.fitos.core.data.db.entity.ReminderEntity
import app.fitos.core.data.db.entity.ReturnPlanEventEntity
import app.fitos.core.data.db.entity.SleepLogEntity
import app.fitos.core.data.db.entity.WaterLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit WHERE deletedAt IS NULL ORDER BY position, createdAt")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habit WHERE deletedAt IS NULL ORDER BY position, createdAt")
    suspend fun all(): List<HabitEntity>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun get(id: String): HabitEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM habit")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(habit: HabitEntity)

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("UPDATE habit SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setPosition(id: String, position: Int, now: Long)

    @Query("UPDATE habit SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("UPDATE habit SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE habit SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long)

    @Query("SELECT * FROM habit_log WHERE day BETWEEN :from AND :to")
    fun observeLogs(from: Long, to: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_log WHERE habitId = :habitId AND day = :day")
    suspend fun getLog(habitId: String, day: Long): HabitLogEntity?

    @Upsert
    suspend fun upsertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_log WHERE habitId = :habitId AND day = :day")
    suspend fun deleteLog(habitId: String, day: Long)
}

/** Sum of one day's drinks. */
data class WaterDayRow(val day: Long, val total: Int)

@Dao
interface LifestyleDao {
    @Query("SELECT * FROM water_log WHERE day = :day AND deletedAt IS NULL ORDER BY loggedAt, createdAt")
    fun observeWater(day: Long): Flow<List<WaterLogEntity>>

    @Query("SELECT day, SUM(amountMl) AS total FROM water_log WHERE day BETWEEN :from AND :to AND deletedAt IS NULL GROUP BY day")
    fun observeWaterTotals(from: Long, to: Long): Flow<List<WaterDayRow>>

    @Insert
    suspend fun insertWater(entry: WaterLogEntity)

    @Query("UPDATE water_log SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun deleteWater(id: String, at: Long)

    @Query("UPDATE water_log SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restoreWater(id: String, at: Long)

    @Query("SELECT * FROM sleep_log WHERE day BETWEEN :from AND :to AND deletedAt IS NULL ORDER BY day, createdAt")
    fun observeSleep(from: Long, to: Long): Flow<List<SleepLogEntity>>

    @Query("SELECT * FROM sleep_log WHERE day = :day AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun sleepOn(day: Long): SleepLogEntity?

    @Insert
    suspend fun insertSleep(entry: SleepLogEntity)

    @Update
    suspend fun updateSleep(entry: SleepLogEntity)

    @Query("UPDATE sleep_log SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun deleteSleep(id: String, at: Long)

    @Query("UPDATE sleep_log SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restoreSleep(id: String, at: Long)

    @Query("SELECT * FROM activity_log WHERE day BETWEEN :from AND :to AND deletedAt IS NULL ORDER BY day, loggedAt")
    fun observeActivity(from: Long, to: Long): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_log WHERE id = :id")
    suspend fun getActivity(id: String): ActivityLogEntity?

    @Insert
    suspend fun insertActivity(entry: ActivityLogEntity)

    @Update
    suspend fun updateActivity(entry: ActivityLogEntity)

    @Query("UPDATE activity_log SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun deleteActivity(id: String, at: Long)

    @Query("UPDATE activity_log SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restoreActivity(id: String, at: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder ORDER BY minuteOfDay, createdAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder ORDER BY minuteOfDay, createdAt")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminder SET isEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReturnPlanDao {
    @Query("SELECT * FROM return_plan_event ORDER BY detectedAt DESC, createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<ReturnPlanEventEntity?>

    @Insert
    suspend fun insert(event: ReturnPlanEventEntity)

    @Query("UPDATE return_plan_event SET choice = :choice, chosenAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun choose(id: String, choice: String, at: Long)
}
