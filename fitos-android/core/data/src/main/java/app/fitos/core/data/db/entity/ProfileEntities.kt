package app.fitos.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Conventions for every table (docs/database.md):
// - ids are text UUIDs; times are epoch millis (UTC) with the zone id where the local day matters;
// - calendar days are LocalDate.toEpochDay(), stored at logging time;
// - storage units are fixed: kg, cm, g, ml, seconds; enums are stored as stable lowercase codes;
// - unknown values are null, never 0; important rows are soft-deleted with deletedAt.

/** Single row ([SINGLE_ID]): the person this app belongs to. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String?,
    val sex: String,
    val birthDate: Long,
    val heightCm: Double,
    val activityLevel: String,
    val trainingDaysPerWeek: Int,
    /** Minutes after midnight, or null when there is no usual time. */
    val usualTrainingMinute: Int?,
    val homeWorkoutMinutes: Int,
    /** Comma-separated equipment codes; empty when none. */
    val homeEquipment: String,
    val primaryGoal: String,
    val energyPace: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLE_ID = "self"
    }
}

/** Dated daily targets. A day is judged against the latest target with effectiveFrom ≤ that day. */
@Entity(tableName = "nutrition_target", indices = [Index(value = ["effectiveFrom"], unique = true)])
data class NutritionTargetEntity(
    @PrimaryKey val id: String,
    val effectiveFrom: Long,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int?,
    val waterMl: Int,
    val sleepMinutes: Int,
    val origin: String,
    /** Versioned JSON of the calculation inputs, null for manual targets. */
    val assumptionsJson: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "weight_goal", indices = [Index("status")])
data class WeightGoalEntity(
    @PrimaryKey val id: String,
    val targetWeightKg: Double,
    val startWeightKg: Double?,
    val startDate: Long,
    val reviewDate: Long?,
    val status: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
