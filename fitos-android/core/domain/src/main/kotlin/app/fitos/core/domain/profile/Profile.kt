package app.fitos.core.domain.profile

import java.time.LocalDate
import java.time.LocalTime
import java.time.Period

/** Biological sex, used only by the energy formula. */
enum class Sex(val code: String) {
    MALE("male"),
    FEMALE("female");

    companion object {
        fun fromCode(code: String): Sex = entries.first { it.code == code }
    }
}

/** Standard activity factors for TDEE = BMR × factor. Training sessions are part of the level. */
enum class ActivityLevel(val code: String, val factor: Double) {
    SEDENTARY("sedentary", 1.2),
    LIGHT("light", 1.375),
    MODERATE("moderate", 1.55),
    ACTIVE("active", 1.725),
    VERY_ACTIVE("very_active", 1.9);

    companion object {
        fun fromCode(code: String): ActivityLevel = entries.first { it.code == code }

        /** A starting suggestion from weekly training sessions; the user always confirms it. */
        fun suggestedFor(trainingDaysPerWeek: Int): ActivityLevel = when {
            trainingDaysPerWeek <= 0 -> SEDENTARY
            trainingDaysPerWeek <= 3 -> LIGHT
            trainingDaysPerWeek <= 5 -> MODERATE
            else -> ACTIVE
        }
    }
}

enum class PrimaryGoal(val code: String) {
    FAT_LOSS("fat_loss"),
    MAINTAIN("maintain"),
    MUSCLE_GAIN("muscle_gain");

    companion object {
        fun fromCode(code: String): PrimaryGoal = entries.first { it.code == code }
    }
}

/** How hard to push the energy deficit during fat loss, as a share of TDEE. */
enum class EnergyPace(val code: String, val deficitPercent: Int) {
    GENTLE("gentle", 10),
    STANDARD("standard", 20),
    FIRM("firm", 25);

    companion object {
        fun fromCode(code: String): EnergyPace = entries.first { it.code == code }
    }
}

enum class HomeEquipment(val code: String) {
    DUMBBELLS("dumbbells"),
    BANDS("bands"),
    PULL_UP_BAR("pull_up_bar"),
    KETTLEBELL("kettlebell"),
    BENCH("bench");

    companion object {
        fun fromCode(code: String): HomeEquipment? = entries.firstOrNull { it.code == code }
    }
}

data class Profile(
    val displayName: String?,
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val activityLevel: ActivityLevel,
    val trainingDaysPerWeek: Int,
    val usualTrainingTime: LocalTime?,
    val homeWorkoutMinutes: Int,
    val homeEquipment: Set<HomeEquipment>,
    val primaryGoal: PrimaryGoal,
    val energyPace: EnergyPace,
) {
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years
}

enum class GoalStatus(val code: String) {
    ACTIVE("active"),
    ACHIEVED("achieved"),
    REPLACED("replaced"),
    DROPPED("dropped");

    companion object {
        fun fromCode(code: String): GoalStatus = entries.first { it.code == code }
    }
}

data class WeightGoal(
    val id: String,
    val targetWeightKg: Double,
    val startWeightKg: Double?,
    val startDate: LocalDate,
    val reviewDate: LocalDate?,
    val status: GoalStatus,
)
