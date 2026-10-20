package app.fitos.core.domain.profile

import app.fitos.core.domain.nutrition.NutritionTarget
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    /** Null until first-run setup is finished. */
    val profile: Flow<Profile?>

    suspend fun save(profile: Profile)
}

interface WeightGoalRepository {
    val activeGoal: Flow<WeightGoal?>

    /** Replaces the active goal (the previous one is kept with status REPLACED). */
    suspend fun setActiveGoal(targetWeightKg: Double, reviewDate: LocalDate?, startWeightKg: Double?, startDate: LocalDate)

    /** Ends the active goal without a new one. */
    suspend fun clearActiveGoal()
}

/** Everything first-run setup collects, written in one transaction. */
data class InitialSetup(
    val profile: Profile,
    val currentWeightKg: Double,
    val targetWeightKg: Double?,
    val goalReviewDate: LocalDate?,
    val target: NutritionTarget,
)

interface SetupRepository {
    val isSetupComplete: Flow<Boolean>

    suspend fun completeSetup(setup: InitialSetup)
}
