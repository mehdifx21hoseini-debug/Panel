package app.fitos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.fitos.core.data.db.entity.DayVariantEntity
import app.fitos.core.data.db.entity.ExerciseEntity
import app.fitos.core.data.db.entity.ExerciseMuscleEntity
import app.fitos.core.data.db.entity.PlannedExerciseEntity
import app.fitos.core.data.db.entity.ProgramChangeLogEntity
import app.fitos.core.data.db.entity.ProgramDayEntity
import app.fitos.core.data.db.entity.ProgramEntity
import app.fitos.core.data.db.entity.ProgressionRuleEntity
import kotlinx.coroutines.flow.Flow

data class ExerciseWithMuscles(
    @Embedded val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val muscles: List<ExerciseMuscleEntity>,
)

data class PlannedWithDetails(
    @Embedded val planned: PlannedExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id", entity = ExerciseEntity::class)
    val exercise: ExerciseWithMuscles,
    @Relation(parentColumn = "id", entityColumn = "plannedExerciseId")
    val rule: ProgressionRuleEntity?,
)

data class VariantWithPlanned(
    @Embedded val variant: DayVariantEntity,
    @Relation(parentColumn = "id", entityColumn = "variantId", entity = PlannedExerciseEntity::class)
    val planned: List<PlannedWithDetails>,
)

data class DayWithVariants(
    @Embedded val day: ProgramDayEntity,
    @Relation(parentColumn = "id", entityColumn = "programDayId", entity = DayVariantEntity::class)
    val variants: List<VariantWithPlanned>,
)

data class ProgramTree(
    @Embedded val program: ProgramEntity,
    @Relation(parentColumn = "id", entityColumn = "programId", entity = ProgramDayEntity::class)
    val days: List<DayWithVariants>,
)

data class ProgramSummaryRow(val id: String, val name: String, val isActive: Boolean, val trainingDays: Int, val archived: Boolean)

@Dao
interface ExerciseDao {
    @Transaction
    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL ORDER BY nameFa")
    fun observeAll(): Flow<List<ExerciseWithMuscles>>

    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observe(id: String): Flow<ExerciseWithMuscles?>

    @Transaction
    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun get(id: String): ExerciseWithMuscles?

    @Transaction
    @Query("SELECT * FROM exercise WHERE builtinKey IS NOT NULL")
    suspend fun builtins(): List<ExerciseWithMuscles>

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Insert
    suspend fun insertMuscles(muscles: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_muscle WHERE exerciseId = :exerciseId")
    suspend fun deleteMuscles(exerciseId: String)

    @Query("UPDATE exercise SET isFavorite = :favorite, updatedAt = :at WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, at: Long)

    @Query("UPDATE exercise SET deletedAt = :at, updatedAt = :at WHERE id = :id AND isCustom = 1")
    suspend fun softDeleteCustom(id: String, at: Long)

    @Query("SELECT COUNT(DISTINCT v.programDayId) FROM planned_exercise pe JOIN day_variant v ON v.id = pe.variantId WHERE pe.exerciseId = :id")
    suspend fun usageCount(id: String): Int
}

@Dao
interface ProgramDao {
    @Query(
        """SELECT p.id, p.name, p.isActive,
           (SELECT COUNT(*) FROM program_day d WHERE d.programId = p.id AND d.isRest = 0) AS trainingDays,
           (p.archivedAt IS NOT NULL) AS archived
           FROM program p WHERE p.deletedAt IS NULL ORDER BY p.isActive DESC, p.archivedAt IS NOT NULL, p.updatedAt DESC""",
    )
    fun observeSummaries(): Flow<List<ProgramSummaryRow>>

    @Transaction
    @Query("SELECT * FROM program WHERE id = :id AND deletedAt IS NULL")
    fun observeTree(id: String): Flow<ProgramTree?>

    @Transaction
    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun tree(id: String): ProgramTree?

    @Transaction
    @Query("SELECT * FROM program WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    fun observeActiveTree(): Flow<ProgramTree?>

    @Query("SELECT COUNT(*) FROM program WHERE isActive = 1 AND deletedAt IS NULL")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM program WHERE id = :id")
    suspend fun program(id: String): ProgramEntity?

    @Insert suspend fun insertProgram(program: ProgramEntity)
    @Update suspend fun updateProgram(program: ProgramEntity)

    @Query("UPDATE program SET isActive = 0, updatedAt = :at WHERE isActive = 1")
    suspend fun deactivateAll(at: Long)

    @Query("UPDATE program SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("SELECT * FROM program_day WHERE id = :id")
    suspend fun day(id: String): ProgramDayEntity?

    @Query("SELECT * FROM program_day WHERE programId = :programId ORDER BY position")
    suspend fun days(programId: String): List<ProgramDayEntity>

    @Insert suspend fun insertDay(day: ProgramDayEntity)
    @Update suspend fun updateDay(day: ProgramDayEntity)
    @Update suspend fun updateDays(days: List<ProgramDayEntity>)

    @Query("DELETE FROM program_day WHERE id = :id")
    suspend fun deleteDay(id: String)

    @Query("SELECT * FROM day_variant WHERE programDayId = :dayId AND kind = :kind")
    suspend fun variant(dayId: String, kind: String): DayVariantEntity?

    @Query("SELECT * FROM day_variant WHERE id = :id")
    suspend fun variantById(id: String): DayVariantEntity?

    @Insert suspend fun insertVariant(variant: DayVariantEntity)

    @Query("SELECT * FROM planned_exercise WHERE id = :id")
    suspend fun planned(id: String): PlannedExerciseEntity?

    @Query("SELECT * FROM planned_exercise WHERE variantId = :variantId ORDER BY position")
    suspend fun plannedIn(variantId: String): List<PlannedExerciseEntity>

    @Insert suspend fun insertPlanned(planned: PlannedExerciseEntity)
    @Update suspend fun updatePlanned(planned: PlannedExerciseEntity)
    @Update suspend fun updatePlannedList(planned: List<PlannedExerciseEntity>)

    @Query("DELETE FROM planned_exercise WHERE id = :id")
    suspend fun deletePlanned(id: String)

    @Query("DELETE FROM planned_exercise WHERE variantId = :variantId")
    suspend fun deletePlannedIn(variantId: String)

    @Query("SELECT * FROM progression_rule WHERE plannedExerciseId = :plannedId")
    suspend fun rule(plannedId: String): ProgressionRuleEntity?

    @Upsert suspend fun upsertRule(rule: ProgressionRuleEntity)

    @Query("DELETE FROM progression_rule WHERE plannedExerciseId = :plannedId")
    suspend fun deleteRule(plannedId: String)

    @Insert suspend fun insertChange(change: ProgramChangeLogEntity)

    @Query("SELECT * FROM program_change_log WHERE programId = :programId ORDER BY at DESC, rowid DESC")
    fun observeChanges(programId: String): Flow<List<ProgramChangeLogEntity>>
}
