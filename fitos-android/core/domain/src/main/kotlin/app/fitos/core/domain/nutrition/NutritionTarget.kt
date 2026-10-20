package app.fitos.core.domain.nutrition

import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Sex
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

enum class TargetOrigin(val code: String) {
    /** Suggested by [TargetCalculator] and accepted as is. */
    CALCULATED("calculated"),
    /** Edited by the user. */
    MANUAL("manual"),
    /** Accepted from a weekly-report suggestion (stage 8). */
    ADAPTIVE("adaptive");

    companion object {
        fun fromCode(code: String): TargetOrigin = entries.firstOrNull { it.code == code } ?: MANUAL
    }
}

/**
 * Daily targets, valid from [effectiveFrom] until the next target starts.
 * Past days are always judged against the target that applied on that day.
 */
data class NutritionTarget(
    val effectiveFrom: LocalDate,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int?,
    val waterMl: Int,
    val sleepMinutes: Int,
    val origin: TargetOrigin,
    /** How the numbers were derived; null for fully manual targets. */
    val assumptions: TargetAssumptions?,
)

/** Inputs and intermediate values behind a calculated target, shown to the user as "assumptions". */
data class TargetAssumptions(
    val formulaVersion: Int,
    val sex: Sex,
    val ageYears: Int,
    val weightKg: Double,
    val heightCm: Double,
    val activityLevel: ActivityLevel,
    val bmr: Double,
    val tdee: Double,
    val goal: PrimaryGoal,
    /** Negative = deficit, positive = surplus, in % of TDEE. */
    val adjustmentPercent: Int,
    val referenceWeightKg: Double,
    val proteinPerKg: Double,
    val fatPerKg: Double,
    /** The deficit would have gone below BMR or the absolute minimum, so energy was raised to that floor. */
    val raisedToFloor: Boolean,
    /** Fat per kg gave less than the minimum share of energy, so fat was raised. */
    val fatRaisedToMinimum: Boolean,
)

interface NutritionTargetRepository {
    /** The target that applies on [date], or null before any target exists. */
    fun effectiveOn(date: LocalDate): Flow<NutritionTarget?>

    /** Saves a target; a target with the same [NutritionTarget.effectiveFrom] is replaced, older ones stay as history. */
    suspend fun save(target: NutritionTarget)
}
