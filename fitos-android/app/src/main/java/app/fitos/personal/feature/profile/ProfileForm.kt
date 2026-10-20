package app.fitos.personal.feature.profile

import androidx.annotation.StringRes
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.profile.InputError
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.ProfileRules
import app.fitos.core.domain.profile.Sex
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** Rough time slots; the exact minute can be refined later from training history. */
enum class TrainingTime(val time: LocalTime?, @param:StringRes val label: Int) {
    MORNING(LocalTime.of(8, 0), R.string.time_morning),
    NOON(LocalTime.of(12, 30), R.string.time_noon),
    AFTERNOON(LocalTime.of(17, 0), R.string.time_afternoon),
    EVENING(LocalTime.of(20, 30), R.string.time_evening),
    VARIES(null, R.string.time_varies);

    companion object {
        fun of(time: LocalTime?): TrainingTime = if (time == null) {
            VARIES
        } else {
            entries.filter { it.time != null }.minBy { abs(it.time!!.toSecondOfDay() - time.toSecondOfDay()) }
        }
    }
}

enum class ProfileField { SEX, BIRTH_DATE, HEIGHT, WEIGHT, TARGET_WEIGHT, REVIEW_DATE }

val HomeMinuteOptions = listOf(10, 15, 20, 30)

/** Editable profile and goal, as typed. Numbers stay text until saved so partial input is never lost. */
data class ProfileForm(
    val name: String = "",
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val height: String = "",
    /** Only asked during first-run setup; afterwards weight is logged in the Body tab. */
    val weight: String = "",
    val trainingDays: Int = 4,
    /** Null = follow the suggestion for [trainingDays]. */
    val activity: ActivityLevel? = null,
    val trainingTime: TrainingTime = TrainingTime.VARIES,
    val homeMinutes: Int = 15,
    val equipment: Set<HomeEquipment> = emptySet(),
    val goal: PrimaryGoal = PrimaryGoal.FAT_LOSS,
    val pace: EnergyPace = EnergyPace.STANDARD,
    val targetWeight: String = "",
    val reviewDate: LocalDate? = null,
) {
    val effectiveActivity: ActivityLevel get() = activity ?: ActivityLevel.suggestedFor(trainingDays)
    val heightCm: Double? get() = PersianNumbers.parse(height)
    val weightKg: Double? get() = PersianNumbers.parse(weight)
    val targetWeightKg: Double? get() = PersianNumbers.parse(targetWeight)

    /** Target weight and review date only mean something when there is a weight to move towards. */
    val hasWeightGoal: Boolean get() = goal != PrimaryGoal.MAINTAIN

    fun aboutErrors(today: LocalDate, requireWeight: Boolean): Map<ProfileField, InputError> = buildMap {
        if (sex == null) put(ProfileField.SEX, InputError.REQUIRED)
        ProfileRules.checkBirthDate(birthDate, today)?.let { put(ProfileField.BIRTH_DATE, it) }
        ProfileRules.checkHeight(heightCm)?.let { put(ProfileField.HEIGHT, it) }
        if (requireWeight) ProfileRules.checkWeight(weightKg)?.let { put(ProfileField.WEIGHT, it) }
    }

    fun goalErrors(today: LocalDate, currentWeightKg: Double?): Map<ProfileField, InputError> = buildMap {
        if (!hasWeightGoal) return@buildMap
        if (targetWeight.isNotBlank() && targetWeightKg == null) put(ProfileField.TARGET_WEIGHT, InputError.TARGET_WEIGHT_RANGE)
        ProfileRules.checkTargetWeight(targetWeightKg, currentWeightKg, goal)?.let { put(ProfileField.TARGET_WEIGHT, it) }
        if (targetWeightKg != null) ProfileRules.checkReviewDate(reviewDate, today)?.let { put(ProfileField.REVIEW_DATE, it) }
    }

    /** Call only after the about-errors are empty. */
    fun toProfile(): Profile = Profile(
        displayName = name.trim().ifEmpty { null },
        sex = requireNotNull(sex),
        birthDate = requireNotNull(birthDate),
        heightCm = requireNotNull(heightCm),
        activityLevel = effectiveActivity,
        trainingDaysPerWeek = trainingDays,
        usualTrainingTime = trainingTime.time,
        homeWorkoutMinutes = homeMinutes,
        homeEquipment = equipment,
        primaryGoal = goal,
        energyPace = pace,
    )

    companion object {
        fun from(profile: Profile, goal: WeightGoal?): ProfileForm = ProfileForm(
            name = profile.displayName.orEmpty(),
            sex = profile.sex,
            birthDate = profile.birthDate,
            height = Fmt.field(profile.heightCm),
            trainingDays = profile.trainingDaysPerWeek,
            activity = profile.activityLevel,
            trainingTime = TrainingTime.of(profile.usualTrainingTime),
            homeMinutes = profile.homeWorkoutMinutes,
            equipment = profile.homeEquipment,
            goal = profile.primaryGoal,
            pace = profile.energyPace,
            targetWeight = goal?.let { Fmt.field(it.targetWeightKg) }.orEmpty(),
            reviewDate = goal?.reviewDate,
        )
    }
}
