package app.fitos.core.domain.report

import app.fitos.core.domain.body.DailyWeight
import app.fitos.core.domain.body.WeightTrend
import app.fitos.core.domain.nutrition.AdaptiveBasisNote
import app.fitos.core.domain.nutrition.EnergyCalculator
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.TargetAssumptions
import app.fitos.core.domain.nutrition.TargetCalculator
import app.fitos.core.domain.nutrition.TargetOrigin
import app.fitos.core.domain.nutrition.on
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.Sex
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** What the estimate is built on; shown in full with any suggestion. */
data class AdaptiveBasis(
    val from: LocalDate,
    val to: LocalDate,
    /** Days with a complete-looking food log, and the mean energy eaten on them. */
    val foodDays: Int,
    val meanIntakeKcal: Double,
    /** Weigh-in days in the window and the weight change per week (least squares, negative = losing). */
    val weighDays: Int,
    val rateKgPerWeek: Double,
    val trendWeightKg: Double,
    /** Measured maintenance: intake minus the energy of the weight change. */
    val tdeeKcal: Double,
    val bmrKcal: Double,
    /** BMR, or the absolute minimum when that is higher. */
    val floorKcal: Int,
    /** The lowest target allowed: the floor, or 25% under measured maintenance when that is higher. */
    val lowestKcal: Int,
    val currentKcal: Int,
    /** Where the goal and pace would put the target with this maintenance, after the safety limits. */
    val idealKcal: Int,
    val goal: PrimaryGoal,
)

/** Why no adjustment can be judged yet; each says what is missing and how far along it is. */
sealed interface Missing {
    /** Weigh-in days in each of the window's three weeks (oldest first), against [AdaptiveTargets.MIN_WEIGH_DAYS_PER_WEEK]. */
    data class WeighIns(val perWeek: List<Int>) : Missing

    /** Days with a complete-looking food log, against [needed]. */
    data class FoodDays(val logged: Int, val needed: Int) : Missing

    /** The current target is too new to judge: [days] since it started, against [needed]. */
    data class RecentChange(val days: Int, val needed: Int) : Missing

    data object NoTarget : Missing
    data object NoProfile : Missing
}

enum class ChangeReason {
    /** Losing more than 1% of body weight a week: more energy. */
    LOSING_TOO_FAST,
    /** The target sits below BMR or more than 25% under measured maintenance: more energy. */
    TOO_LOW,
    /** Measured maintenance says the goal and pace need less energy than the target. */
    SLOWER_THAN_PACE,
    /** Measured maintenance says the goal and pace allow more energy than the target. */
    FASTER_THAN_PACE,
}

/** The answer of the weekly check. A change is only ever suggested; nothing is applied without the user. */
sealed interface AdaptiveResult {
    data class NotReady(val missing: List<Missing>) : AdaptiveResult

    /** The numbers do not add up (maintenance far below BMR or implausibly high): most likely meals were left out. */
    data class NotPlausible(val basis: AdaptiveBasis) : AdaptiveResult

    /** The target fits the measured maintenance and the goal. */
    data class Keep(val basis: AdaptiveBasis) : AdaptiveResult

    /** Losing too fast while eating well under the target: the target is fine, eating up to it is the advice. */
    data class EatUpToTarget(val basis: AdaptiveBasis) : AdaptiveResult

    data class Change(val basis: AdaptiveBasis, val newKcal: Int, val reason: ChangeReason) : AdaptiveResult {
        val delta: Int get() = newKcal - basis.currentKcal
    }
}

/**
 * The calorie check of the weekly report (docs/formulas.md). It measures maintenance from what was
 * really eaten and how the weight really moved over three weeks, then suggests at most one small
 * step toward the goal's pace. Never automatic, never below BMR, never a big jump.
 */
object AdaptiveTargets {
    const val WINDOW_DAYS = 21
    const val MIN_WEIGH_DAYS_PER_WEEK = 3
    const val MIN_FOOD_SHARE = 0.70
    const val MIN_DAYS_BETWEEN_CHANGES = 14
    const val MAX_STEP_KCAL = 150
    /** Differences smaller than this are noise in the estimate: the target stays. */
    const val DEAD_BAND_KCAL = 100
    const val KCAL_PER_KG = 7700.0
    const val MAX_LOSS_PERCENT_PER_WEEK = 1.0
    const val MAX_DEFICIT_PERCENT = TargetCalculator.MAX_DEFICIT_PERCENT
    /** A logged day under this share of the target is taken as incomplete and left out. */
    const val INCOMPLETE_DAY_SHARE = 0.5
    /** Measured maintenance outside BMR × these bounds is not believable. */
    const val PLAUSIBLE_MIN_FACTOR = 1.0
    const val PLAUSIBLE_MAX_FACTOR = 2.5
    /** Eating this much under the target while losing too fast means the target is not the problem. */
    const val UNDER_TARGET_KCAL = 200
    private const val KCAL_FLOOR_MALE = 1500
    private const val KCAL_FLOOR_FEMALE = 1200

    val foodDaysNeeded: Int get() = ceil(WINDOW_DAYS * MIN_FOOD_SHARE).toInt()

    /**
     * The check for the 21 days ending on [asOf]. [weights] are daily averages, [food] the daily
     * totals, [targets] the target history (newest first). The target compared is the one in force
     * on [today], so a change made after the window also counts as a recent change.
     */
    fun check(
        asOf: LocalDate,
        weights: List<DailyWeight>,
        food: Map<LocalDate, NutrientTotals>,
        targets: List<NutritionTarget>,
        profile: Profile?,
        today: LocalDate = asOf,
    ): AdaptiveResult {
        val from = asOf.minusDays(WINDOW_DAYS - 1L)
        val current = targets.on(maxOf(asOf, today))
        val missing = mutableListOf<Missing>()
        if (profile == null) missing += Missing.NoProfile
        if (current == null) missing += Missing.NoTarget

        val window = weights.filter { it.day in from..asOf }
        val perWeek = (0 until 3).map { w ->
            val start = from.plusDays(7L * w)
            window.count { it.day >= start && it.day <= start.plusDays(6) }
        }
        if (perWeek.any { it < MIN_WEIGH_DAYS_PER_WEEK }) missing += Missing.WeighIns(perWeek)

        val complete = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= asOf }.mapNotNull { day ->
            val totals = food[day]?.takeIf { it.count > 0 } ?: return@mapNotNull null
            val reference = targets.on(day)?.kcal ?: current?.kcal ?: 0
            totals.kcal.takeIf { it >= reference * INCOMPLETE_DAY_SHARE }
        }.toList()
        if (complete.size < foodDaysNeeded) missing += Missing.FoodDays(complete.size, foodDaysNeeded)

        if (current != null) {
            val since = ChronoUnit.DAYS.between(current.effectiveFrom, maxOf(asOf, today)).toInt()
            if (since < MIN_DAYS_BETWEEN_CHANGES) missing += Missing.RecentChange(since, MIN_DAYS_BETWEEN_CHANGES)
        }
        if (missing.isNotEmpty() || profile == null || current == null) return AdaptiveResult.NotReady(missing)

        val rate = WeightTrend.slopePerWeek(window) ?: return AdaptiveResult.NotReady(listOf(Missing.WeighIns(perWeek)))
        val trendWeight = WeightTrend.ema(weights.filter { it.day <= asOf }).last().trendKg
        val intake = complete.average()
        val tdee = intake - rate / 7.0 * KCAL_PER_KG
        val bmr = EnergyCalculator.bmr(profile.sex, trendWeight, profile.heightCm, profile.ageOn(asOf))
        val floor = maxOf(roundUp(bmr), if (profile.sex == Sex.MALE) KCAL_FLOOR_MALE else KCAL_FLOOR_FEMALE)
        val lowest = maxOf(floor.toDouble(), tdee * (1 - MAX_DEFICIT_PERCENT / 100.0))
        val ideal = when (profile.primaryGoal) {
            PrimaryGoal.FAT_LOSS -> {
                val byPace = tdee * (1 - minOf(profile.energyPace.deficitPercent, MAX_DEFICIT_PERCENT) / 100.0)
                val byLossCap = tdee - MAX_LOSS_PERCENT_PER_WEEK / 100.0 * trendWeight * KCAL_PER_KG / 7.0
                maxOf(byPace, byLossCap, lowest)
            }
            PrimaryGoal.MAINTAIN -> tdee
            PrimaryGoal.MUSCLE_GAIN -> tdee * (1 + TargetCalculator.SURPLUS_PERCENT / 100.0)
        }
        val basis = AdaptiveBasis(
            from = from, to = asOf, foodDays = complete.size, meanIntakeKcal = intake, weighDays = window.size,
            rateKgPerWeek = rate, trendWeightKg = trendWeight, tdeeKcal = tdee, bmrKcal = bmr, floorKcal = floor, lowestKcal = roundUp(lowest),
            currentKcal = current.kcal, idealKcal = roundTo(ideal, 50), goal = profile.primaryGoal,
        )
        if (tdee < bmr * PLAUSIBLE_MIN_FACTOR || tdee > bmr * PLAUSIBLE_MAX_FACTOR) return AdaptiveResult.NotPlausible(basis)

        val cur = current.kcal
        val losingTooFast = rate < -MAX_LOSS_PERCENT_PER_WEEK / 100.0 * trendWeight
        if (losingTooFast) {
            return if (intake < cur - UNDER_TARGET_KCAL) {
                AdaptiveResult.EatUpToTarget(basis)
            } else {
                AdaptiveResult.Change(basis, cur + MAX_STEP_KCAL, ChangeReason.LOSING_TOO_FAST)
            }
        }
        if (cur < lowest) {
            val up = minOf(MAX_STEP_KCAL, roundUp(lowest - cur))
            return AdaptiveResult.Change(basis, cur + up, ChangeReason.TOO_LOW)
        }
        val gap = ideal - cur
        if (abs(gap) < DEAD_BAND_KCAL) return AdaptiveResult.Keep(basis)
        val step = (roundTo(abs(gap), 50)).coerceAtMost(MAX_STEP_KCAL)
        val next = if (gap > 0) cur + step else maxOf(cur - step, roundUp(lowest)).coerceAtMost(cur)
        if (next == cur) return AdaptiveResult.Keep(basis)
        return AdaptiveResult.Change(basis, next, if (gap > 0) ChangeReason.FASTER_THAN_PACE else ChangeReason.SLOWER_THAN_PACE)
    }

    /**
     * The target that accepting [change] creates from [effectiveFrom]: the new energy, protein and
     * fat kept, carbohydrate taking the difference. The reasons travel with it.
     */
    fun apply(current: NutritionTarget, change: AdaptiveResult.Change, profile: Profile, effectiveFrom: LocalDate): NutritionTarget {
        val b = change.basis
        val carbs = (current.carbsG + roundTo(change.delta / 4.0, 5)).coerceAtLeast(0)
        val previous = current.assumptions
        return current.copy(
            effectiveFrom = effectiveFrom,
            kcal = change.newKcal,
            carbsG = carbs,
            fiberG = current.fiberG?.let { (TargetCalculator.FIBER_PER_1000_KCAL * change.newKcal / 1000.0).roundToInt() },
            origin = TargetOrigin.ADAPTIVE,
            assumptions = TargetAssumptions(
                formulaVersion = TargetCalculator.FORMULA_VERSION,
                sex = profile.sex,
                ageYears = profile.ageOn(effectiveFrom),
                weightKg = b.trendWeightKg,
                heightCm = profile.heightCm,
                activityLevel = profile.activityLevel,
                bmr = b.bmrKcal,
                tdee = b.tdeeKcal,
                goal = profile.primaryGoal,
                adjustmentPercent = ((change.newKcal / b.tdeeKcal - 1) * 100).roundToInt(),
                referenceWeightKg = previous?.referenceWeightKg ?: b.trendWeightKg,
                proteinPerKg = previous?.proteinPerKg ?: TargetCalculator.PROTEIN_PER_KG,
                fatPerKg = previous?.fatPerKg ?: TargetCalculator.FAT_PER_KG,
                raisedToFloor = change.newKcal <= b.floorKcal,
                fatRaisedToMinimum = previous?.fatRaisedToMinimum ?: false,
                adaptive = AdaptiveBasisNote(
                    days = (ChronoUnit.DAYS.between(b.from, b.to) + 1).toInt(),
                    meanIntakeKcal = b.meanIntakeKcal,
                    rateKgPerWeek = b.rateKgPerWeek,
                    previousKcal = b.currentKcal,
                ),
            ),
        )
    }

    private fun roundTo(value: Double, step: Int): Int = (value / step).roundToInt() * step

    private fun roundUp(value: Double): Int = (ceil(value / 50) * 50).toInt()
}
