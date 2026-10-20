package app.fitos.core.domain.profile

import java.time.LocalDate

/** Why an input was rejected. The UI maps each reason to a Persian message. */
enum class InputError {
    REQUIRED,
    HEIGHT_RANGE,
    WEIGHT_RANGE,
    AGE_RANGE,
    TARGET_WEIGHT_RANGE,
    TARGET_NOT_BELOW_CURRENT,
    TARGET_NOT_ABOVE_CURRENT,
    REVIEW_DATE_NOT_FUTURE,
    OUT_OF_RANGE,
}

/** Accepted ranges for profile and goal input. The energy formula is validated for adults. */
object ProfileRules {
    val HEIGHT_CM = 120.0..230.0
    val WEIGHT_KG = 35.0..300.0
    val AGE_YEARS = 16..90
    val TRAINING_DAYS = 0..7
    val HOME_MINUTES = 5..60

    fun checkHeight(cm: Double?): InputError? = when {
        cm == null -> InputError.REQUIRED
        cm !in HEIGHT_CM -> InputError.HEIGHT_RANGE
        else -> null
    }

    fun checkWeight(kg: Double?): InputError? = when {
        kg == null -> InputError.REQUIRED
        kg !in WEIGHT_KG -> InputError.WEIGHT_RANGE
        else -> null
    }

    fun checkBirthDate(birthDate: LocalDate?, today: LocalDate): InputError? {
        if (birthDate == null) return InputError.REQUIRED
        val age = java.time.Period.between(birthDate, today).years
        return if (age !in AGE_YEARS) InputError.AGE_RANGE else null
    }

    /** Target weight is optional; when given it must be realistic and point in the goal's direction. */
    fun checkTargetWeight(targetKg: Double?, currentKg: Double?, goal: PrimaryGoal): InputError? {
        if (targetKg == null) return null
        if (targetKg !in WEIGHT_KG) return InputError.TARGET_WEIGHT_RANGE
        if (currentKg == null) return null
        return when (goal) {
            PrimaryGoal.FAT_LOSS -> if (targetKg >= currentKg) InputError.TARGET_NOT_BELOW_CURRENT else null
            PrimaryGoal.MUSCLE_GAIN -> if (targetKg <= currentKg) InputError.TARGET_NOT_ABOVE_CURRENT else null
            PrimaryGoal.MAINTAIN -> null
        }
    }

    fun checkReviewDate(reviewDate: LocalDate?, today: LocalDate): InputError? =
        if (reviewDate != null && !reviewDate.isAfter(today)) InputError.REVIEW_DATE_NOT_FUTURE else null
}
