package app.fitos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import app.fitos.core.data.db.entity.ExerciseEntity
import app.fitos.core.data.db.entity.PersonalRecordEntity
import app.fitos.core.data.db.entity.PlannedExerciseEntity
import app.fitos.core.data.db.entity.ProgramDayEntity
import app.fitos.core.data.db.entity.ProgressionRuleEntity
import app.fitos.core.data.db.entity.SessionExerciseEntity
import app.fitos.core.data.db.entity.SetLogEntity
import app.fitos.core.data.db.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

data class SessionExerciseTree(
    @Embedded val item: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id", entity = ExerciseEntity::class)
    val exercise: ExerciseWithMuscles,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetLogEntity>,
    /** The plan entry it came from, for the superset group (gone when the plan was edited since). */
    @Relation(parentColumn = "plannedExerciseId", entityColumn = "id")
    val planned: PlannedExerciseEntity?,
    @Relation(parentColumn = "plannedExerciseId", entityColumn = "plannedExerciseId")
    val rule: ProgressionRuleEntity?,
)

data class SessionTree(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "programDayId", entityColumn = "id")
    val day: ProgramDayEntity?,
    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = SessionExerciseEntity::class)
    val exercises: List<SessionExerciseTree>,
)

/** One exercise in one finished session, with the session's date. */
data class PerformanceRow(
    @Embedded val item: SessionExerciseEntity,
    val sessionDay: Long,
    val sessionStartedAt: Long,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<SetLogEntity>,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'in_progress' AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<SessionTree?>

    @Query("SELECT * FROM workout_session WHERE status = 'in_progress' AND deletedAt IS NULL LIMIT 1")
    suspend fun active(): WorkoutSessionEntity?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id AND deletedAt IS NULL")
    fun observeTree(id: String): Flow<SessionTree?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun tree(id: String): SessionTree?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE status = 'finished' AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<SessionTree>>

    @Transaction
    @Query(
        "SELECT * FROM workout_session WHERE status = 'finished' AND deletedAt IS NULL AND programDayId = :dayId AND startedAt < :before " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun previousOfDay(dayId: String, before: Long): SessionTree?

    @Query("SELECT * FROM workout_session WHERE status = 'finished' AND deletedAt IS NULL AND programId = :programId ORDER BY startedAt DESC LIMIT 1")
    fun observeLastFinished(programId: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun session(id: String): WorkoutSessionEntity?

    @Insert suspend fun insertSession(session: WorkoutSessionEntity)
    @Update suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("SELECT * FROM session_exercise WHERE id = :id")
    suspend fun item(id: String): SessionExerciseEntity?

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position")
    suspend fun items(sessionId: String): List<SessionExerciseEntity>

    @Insert suspend fun insertItems(items: List<SessionExerciseEntity>)
    @Update suspend fun updateItem(item: SessionExerciseEntity)

    @Query("SELECT * FROM set_log WHERE id = :id")
    suspend fun set(id: String): SetLogEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM set_log WHERE sessionExerciseId = :itemId")
    suspend fun maxSetPosition(itemId: String): Int

    @Query("SELECT COUNT(*) FROM set_log WHERE sessionExerciseId = :itemId")
    suspend fun setCount(itemId: String): Int

    @Insert suspend fun insertSet(set: SetLogEntity)
    @Update suspend fun updateSet(set: SetLogEntity)

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Transaction
    @Query(
        "SELECT se.*, s.day AS sessionDay, s.startedAt AS sessionStartedAt FROM session_exercise se " +
            "JOIN workout_session s ON s.id = se.sessionId " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'finished' AND s.deletedAt IS NULL " +
            "AND EXISTS (SELECT 1 FROM set_log l WHERE l.sessionExerciseId = se.id) " +
            "ORDER BY s.startedAt DESC",
    )
    fun observePerformances(exerciseId: String): Flow<List<PerformanceRow>>

    @Transaction
    @Query(
        "SELECT se.*, s.day AS sessionDay, s.startedAt AS sessionStartedAt FROM session_exercise se " +
            "JOIN workout_session s ON s.id = se.sessionId " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'finished' AND s.deletedAt IS NULL " +
            "AND EXISTS (SELECT 1 FROM set_log l WHERE l.sessionExerciseId = se.id) " +
            "ORDER BY s.startedAt DESC",
    )
    suspend fun performances(exerciseId: String): List<PerformanceRow>

    @Transaction
    @Query(
        "SELECT se.*, s.day AS sessionDay, s.startedAt AS sessionStartedAt FROM session_exercise se " +
            "JOIN workout_session s ON s.id = se.sessionId " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'finished' AND s.deletedAt IS NULL AND s.startedAt <= :before " +
            "AND EXISTS (SELECT 1 FROM set_log l WHERE l.sessionExerciseId = se.id) " +
            "ORDER BY s.startedAt DESC LIMIT 1",
    )
    suspend fun lastPerformance(exerciseId: String, before: Long): PerformanceRow?

    /** Latest body weight logged on or before [day], for body-weight load. */
    @Query("SELECT weightKg FROM weight_entry WHERE deletedAt IS NULL AND day <= :day ORDER BY day DESC, measuredAt DESC LIMIT 1")
    suspend fun bodyWeightOn(day: Long): Double?

    @Query("SELECT day, weightKg FROM weight_entry WHERE deletedAt IS NULL ORDER BY day, measuredAt")
    fun observeBodyWeights(): Flow<List<DayWeight>>

    @Query("SELECT * FROM personal_record WHERE exerciseId = :exerciseId ORDER BY type, reps")
    fun observeRecords(exerciseId: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_record WHERE exerciseId = :exerciseId")
    suspend fun records(exerciseId: String): List<PersonalRecordEntity>

    @Query("DELETE FROM personal_record WHERE exerciseId = :exerciseId")
    suspend fun deleteRecords(exerciseId: String)

    @Insert suspend fun insertRecords(records: List<PersonalRecordEntity>)
}

data class DayWeight(val day: Long, val weightKg: Double)
