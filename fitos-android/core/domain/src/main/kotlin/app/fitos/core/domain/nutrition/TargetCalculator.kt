package app.fitos.core.domain.nutrition

import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Sex
import java.time.LocalDate
import kotlin.math.roundToInt

/** Mifflin-St Jeor (1990) resting energy, in kcal/day. */
object EnergyCalculator {

    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return base + if (sex == Sex.MALE) 5.0 else -161.0
    }

    fun tdee(bmr: Double, activity: ActivityLevel): Double = bmr * activity.factor
}

data class TargetInput(
    val sex: Sex,
    val ageYears: Int,
    val weightKg: Double,
    val heightCm: Double,
    val activity: ActivityLevel,
    val goal: PrimaryGoal,
    val pace: EnergyPace,
    val targetWeightKg: Double?,
)

/**
 * Starting targets from documented rules (docs/formulas.md). Never applied silently: the user sees
 * the numbers with their assumptions and can change any of them.
 *
 * - Energy: TDEE × (1 − deficit) for fat loss (10/20/25%), TDEE for maintenance, TDEE × 1.10 for gain.
 *   Never below BMR, and never below 1500 (men) / 1200 (women). Rounded to 50 kcal (up, when floored).
 * - Reference weight: the target weight when losing towards it, otherwise the current weight.
 * - Protein 1.8 g/kg reference weight. Fat 0.7 g/kg, but at least 20% of energy. Carbs take the rest.
 * - Fibre 14 g per 1000 kcal. Water 30 ml/kg reference weight (2–4 L, steps of 250 ml). Sleep 7.5 h.
 */
object TargetCalculator {
    const val FORMULA_VERSION = 1
    const val PROTEIN_PER_KG = 1.8
    const val FAT_PER_KG = 0.7
    const val MIN_FAT_SHARE = 0.20
    const val SURPLUS_PERCENT = 10
    const val MAX_DEFICIT_PERCENT = 25
    const val FIBER_PER_1000_KCAL = 14.0
    const val WATER_ML_PER_KG = 30.0
    const val SLEEP_MINUTES = 450
    private const val KCAL_FLOOR_MALE = 1500.0
    private const val KCAL_FLOOR_FEMALE = 1200.0

    fun suggest(input: TargetInput, effectiveFrom: LocalDate): NutritionTarget {
        val bmr = EnergyCalculator.bmr(input.sex, input.weightKg, input.heightCm, input.ageYears)
        val tdee = EnergyCalculator.tdee(bmr, input.activity)
        val adjustment = when (input.goal) {
            PrimaryGoal.FAT_LOSS -> -input.pace.deficitPercent.coerceAtMost(MAX_DEFICIT_PERCENT)
            PrimaryGoal.MAINTAIN -> 0
            PrimaryGoal.MUSCLE_GAIN -> SURPLUS_PERCENT
        }
        val adjusted = tdee * (1 + adjustment / 100.0)
        val floor = maxOf(bmr, if (input.sex == Sex.MALE) KCAL_FLOOR_MALE else KCAL_FLOOR_FEMALE)
        val raisedToFloor = adjusted < floor
        // Rounding must never take a floored value back under the floor, so it rounds up there.
        val kcal = if (raisedToFloor) ceilTo(floor, 50) else roundTo(adjusted, 50)

        val reference = input.targetWeightKg
            ?.takeIf { input.goal == PrimaryGoal.FAT_LOSS && it < input.weightKg }
            ?: input.weightKg
        val protein = roundTo(PROTEIN_PER_KG * reference, 5)
        val fatByWeight = FAT_PER_KG * reference
        val fatMinimum = MIN_FAT_SHARE * kcal / 9.0
        val fatRaised = fatByWeight < fatMinimum
        val fat = roundTo(maxOf(fatByWeight, fatMinimum), 5)
        val carbs = roundTo(((kcal - protein * 4 - fat * 9) / 4.0).coerceAtLeast(0.0), 5)
        val fiber = (FIBER_PER_1000_KCAL * kcal / 1000.0).roundToInt()
        val water = roundTo((WATER_ML_PER_KG * reference).coerceIn(2000.0, 4000.0), 250)

        return NutritionTarget(
            effectiveFrom = effectiveFrom,
            kcal = kcal,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber,
            waterMl = water,
            sleepMinutes = SLEEP_MINUTES,
            origin = TargetOrigin.CALCULATED,
            assumptions = TargetAssumptions(
                formulaVersion = FORMULA_VERSION,
                sex = input.sex,
                ageYears = input.ageYears,
                weightKg = input.weightKg,
                heightCm = input.heightCm,
                activityLevel = input.activity,
                bmr = bmr,
                tdee = tdee,
                goal = input.goal,
                adjustmentPercent = adjustment,
                referenceWeightKg = reference,
                proteinPerKg = PROTEIN_PER_KG,
                fatPerKg = FAT_PER_KG,
                raisedToFloor = raisedToFloor,
                fatRaisedToMinimum = fatRaised,
            ),
        )
    }

    /** Energy implied by the macros: 4 kcal/g protein and carbs, 9 kcal/g fat. */
    fun kcalFromMacros(proteinG: Int, carbsG: Int, fatG: Int): Int = proteinG * 4 + carbsG * 4 + fatG * 9

    private fun roundTo(value: Double, step: Int): Int = ((value / step).roundToInt() * step)

    private fun ceilTo(value: Double, step: Int): Int = kotlin.math.ceil(value / step).toInt() * step
}

/** Ranges for manually edited targets. Values outside are rejected; [isBelowBmr] only warns. */
object TargetRules {
    val KCAL = 1000..6000
    val PROTEIN_G = 30..400
    val CARBS_G = 0..900
    val FAT_G = 20..300
    val FIBER_G = 0..100
    val WATER_ML = 500..8000
    val SLEEP_MINUTES = 240..720

    fun isBelowBmr(kcal: Int, assumptions: TargetAssumptions?): Boolean =
        assumptions != null && kcal < assumptions.bmr
}
