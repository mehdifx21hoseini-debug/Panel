package app.fitos.personal.feature.targets

import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.TargetCalculator
import app.fitos.core.domain.nutrition.TargetOrigin
import app.fitos.core.domain.nutrition.TargetRules
import app.fitos.core.domain.profile.InputError
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import kotlin.math.roundToInt

/** Each target field with its accepted range, in the unit the user types (liters, hours). */
enum class TargetField(val range: ClosedFloatingPointRange<Double>) {
    KCAL(TargetRules.KCAL.toDoubleRange()),
    PROTEIN(TargetRules.PROTEIN_G.toDoubleRange()),
    CARBS(TargetRules.CARBS_G.toDoubleRange()),
    FAT(TargetRules.FAT_G.toDoubleRange()),
    FIBER(TargetRules.FIBER_G.toDoubleRange()),
    WATER(TargetRules.WATER_ML.first / 1000.0..TargetRules.WATER_ML.last / 1000.0),
    SLEEP(TargetRules.SLEEP_MINUTES.first / 60.0..TargetRules.SLEEP_MINUTES.last / 60.0),
}

private fun IntRange.toDoubleRange() = first.toDouble()..last.toDouble()

/**
 * Targets as typed. [base] is the calculated suggestion (or the saved target) these fields started
 * from; it supplies the assumptions and decides whether the result counts as edited.
 */
data class TargetsForm(
    val kcal: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val fiber: String,
    val water: String,
    val sleep: String,
    val base: NutritionTarget,
) {
    fun value(field: TargetField): Double? = PersianNumbers.parse(
        when (field) {
            TargetField.KCAL -> kcal
            TargetField.PROTEIN -> protein
            TargetField.CARBS -> carbs
            TargetField.FAT -> fat
            TargetField.FIBER -> fiber
            TargetField.WATER -> water
            TargetField.SLEEP -> sleep
        },
    )

    /** Fibre may be left empty (no target); everything else is required. */
    fun errors(): Map<TargetField, InputError> = TargetField.entries.mapNotNull { field ->
        val v = value(field)
        when {
            v == null && field == TargetField.FIBER -> null
            v == null -> field to InputError.REQUIRED
            v !in field.range -> field to InputError.OUT_OF_RANGE
            else -> null
        }
    }.toMap()

    /** Energy from the typed macros, or null while one of them is missing. */
    val macroKcal: Int?
        get() {
            val p = value(TargetField.PROTEIN) ?: return null
            val c = value(TargetField.CARBS) ?: return null
            val f = value(TargetField.FAT) ?: return null
            return TargetCalculator.kcalFromMacros(p.roundToInt(), c.roundToInt(), f.roundToInt())
        }

    val isBelowBmr: Boolean
        get() = value(TargetField.KCAL)?.let { TargetRules.isBelowBmr(it.roundToInt(), base.assumptions) } ?: false

    val isEdited: Boolean get() = this != from(base)

    /** Call only after [errors] is empty. Unchanged suggestions keep their origin; edits become MANUAL. */
    fun toTarget(effectiveFrom: LocalDate): NutritionTarget = NutritionTarget(
        effectiveFrom = effectiveFrom,
        kcal = value(TargetField.KCAL)!!.roundToInt(),
        proteinG = value(TargetField.PROTEIN)!!.roundToInt(),
        carbsG = value(TargetField.CARBS)!!.roundToInt(),
        fatG = value(TargetField.FAT)!!.roundToInt(),
        fiberG = value(TargetField.FIBER)?.roundToInt(),
        waterMl = (value(TargetField.WATER)!! * 1000).roundToInt(),
        sleepMinutes = (value(TargetField.SLEEP)!! * 60).roundToInt(),
        origin = if (isEdited) TargetOrigin.MANUAL else base.origin,
        assumptions = base.assumptions,
    )

    companion object {
        fun from(target: NutritionTarget) = TargetsForm(
            kcal = Fmt.field(target.kcal),
            protein = Fmt.field(target.proteinG),
            carbs = Fmt.field(target.carbsG),
            fat = Fmt.field(target.fatG),
            fiber = target.fiberG?.let(Fmt::field).orEmpty(),
            water = Fmt.field(target.waterMl / 1000.0, maxFractionDigits = 2),
            sleep = Fmt.field(target.sleepMinutes / 60.0),
            base = target,
        )
    }
}
