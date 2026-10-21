package app.fitos.core.domain.today

import app.fitos.core.domain.habits.DayResult
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.training.VariantKind

/** The parts of a day that can count toward its completion. */
enum class DayItem { TRAINING, CALORIES, PROTEIN, WATER, SLEEP, HABITS }

/** Why an item does not count today. */
enum class NotCounted { NO_PROGRAM, REST_DAY, NO_TARGET, NO_HABITS }

/**
 * One item's share of the day. [score] is 0..1 when the item counts today; [logged] false means
 * nothing was logged (it then counts as zero and says so). [notCounted] explains a missing score.
 */
data class ItemScore(
    val item: DayItem,
    val score: Double?,
    val logged: Boolean,
    val value: Double? = null,
    val target: Double? = null,
    val notCounted: NotCounted? = null,
    /** Training: the version of today's session (a short or minimal one still counts as done). */
    val variant: VariantKind? = null,
    /** Habits: how many of today's habits are done, out of how many count. */
    val done: Int = 0,
    val total: Int = 0,
) {
    val counts: Boolean get() = score != null
}

data class DayScore(val items: List<ItemScore>) {
    val counted: List<ItemScore> get() = items.filter { it.counts }
    /** The average of the items that count today, 0..100; 0 when nothing counts. */
    val percent: Int get() = counted.takeIf { it.isNotEmpty() }?.let { list -> (list.sumOf { it.score!! } / list.size * 100).toInt() } ?: 0
}

/** What the day's score is worked out from: today's logs and targets. */
data class DayScoreInput(
    /** An active training program exists. */
    val hasProgram: Boolean,
    val trainingPlanned: Boolean,
    /** Today's finished session, if any; any version counts. */
    val trainedVariant: VariantKind?,
    val trained: Boolean,
    val kcal: Double?,
    val kcalTarget: Int?,
    val proteinG: Double?,
    val proteinTargetG: Int?,
    val waterMl: Int?,
    val waterTargetMl: Int?,
    /** Last night's sleep. */
    val sleepMinutes: Int?,
    val sleepTargetMinutes: Int?,
    /** Today's habits with their results. */
    val habits: List<Pair<Habit, DayResult>>,
)

/**
 * The day's completion: the average of the items that apply today.
 * - Training: only on a training day (or when a session was done anyway); any version is done.
 * - Calories: inside ±10% of the target = full; below, in proportion to the band's floor;
 *   above, falling to zero at 50% over the target.
 * - Protein, water, sleep: the share of the target, at most full.
 * - Habits: the average of today's own daily habits (skipped ones aside). System habits that
 *   repeat the items above (water, sleep, training, nutrition) and weekly habits are left out.
 * Anything not logged counts as zero and is marked as not logged.
 */
object DayScoreRules {
    const val KCAL_BAND = 0.10
    const val KCAL_ZERO_OVER = 0.50
    private val REPEATED = setOf(SystemHabit.WATER, SystemHabit.SLEEP, SystemHabit.TRAINING, SystemHabit.NUTRITION)

    fun calories(eaten: Double, target: Int): Double {
        val low = target * (1 - KCAL_BAND)
        val high = target * (1 + KCAL_BAND)
        val zero = target * (1 + KCAL_ZERO_OVER)
        return when {
            eaten < low -> (eaten / low).coerceIn(0.0, 1.0)
            eaten <= high -> 1.0
            else -> ((zero - eaten) / (zero - high)).coerceIn(0.0, 1.0)
        }
    }

    private fun share(value: Double?, target: Int?, item: DayItem): ItemScore = when {
        target == null || target <= 0 -> ItemScore(item, null, value != null, value, null, NotCounted.NO_TARGET)
        value == null || value <= 0.0 -> ItemScore(item, 0.0, false, null, target.toDouble())
        else -> ItemScore(item, (value / target).coerceIn(0.0, 1.0), true, value, target.toDouble())
    }

    fun score(input: DayScoreInput): DayScore {
        val training = when {
            input.trained -> ItemScore(DayItem.TRAINING, 1.0, true, variant = input.trainedVariant ?: VariantKind.FULL)
            input.trainingPlanned -> ItemScore(DayItem.TRAINING, 0.0, false)
            !input.hasProgram -> ItemScore(DayItem.TRAINING, null, false, notCounted = NotCounted.NO_PROGRAM)
            else -> ItemScore(DayItem.TRAINING, null, false, notCounted = NotCounted.REST_DAY)
        }
        val calories = when {
            input.kcalTarget == null || input.kcalTarget <= 0 -> ItemScore(DayItem.CALORIES, null, input.kcal != null, input.kcal, null, NotCounted.NO_TARGET)
            input.kcal == null || input.kcal <= 0.0 -> ItemScore(DayItem.CALORIES, 0.0, false, null, input.kcalTarget.toDouble())
            else -> ItemScore(DayItem.CALORIES, calories(input.kcal, input.kcalTarget), true, input.kcal, input.kcalTarget.toDouble())
        }
        val own = input.habits.filter { (h, r) -> h.frequency == HabitFrequency.DAILY && h.system !in REPEATED && r.outcome != Outcome.SKIPPED }
        val habits = if (own.isEmpty()) {
            ItemScore(DayItem.HABITS, null, false, notCounted = NotCounted.NO_HABITS)
        } else {
            val scores = own.map { (_, r) -> r.score ?: 0.0 }
            ItemScore(
                DayItem.HABITS, scores.average(), own.any { (_, r) -> r.outcome != Outcome.NOT_LOGGED },
                done = own.count { (_, r) -> r.outcome == Outcome.DONE }, total = own.size,
            )
        }
        return DayScore(
            listOf(
                training,
                calories,
                share(input.proteinG, input.proteinTargetG, DayItem.PROTEIN),
                share(input.waterMl?.toDouble(), input.waterTargetMl, DayItem.WATER),
                share(input.sleepMinutes?.toDouble(), input.sleepTargetMinutes, DayItem.SLEEP),
                habits,
            ),
        )
    }
}
