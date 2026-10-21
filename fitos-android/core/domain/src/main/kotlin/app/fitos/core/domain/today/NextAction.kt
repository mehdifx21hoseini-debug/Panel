package app.fitos.core.domain.today

import app.fitos.core.domain.habits.Habit
import java.time.LocalTime

/** The one thing Home suggests doing next. */
sealed interface NextAction {
    data object ResumeSession : NextAction
    data object LogWeight : NextAction
    data object LogSleep : NextAction
    data object StartWorkout : NextAction
    /** Nothing eaten is logged yet, or protein is well behind by the afternoon. */
    data class LogFood(val proteinBehind: Boolean) : NextAction
    /** Water is behind where it would be if spread over the waking day; [ml] to catch up. */
    data class DrinkWater(val ml: Int) : NextAction
    data class DoHabit(val habit: Habit) : NextAction
    data object AllDone : NextAction
}

/** What the suggestion is chosen from. */
data class NextFacts(
    val time: LocalTime,
    val sessionOpen: Boolean,
    /** A weight was logged before or a goal is set, so a daily weigh-in is part of the routine. */
    val tracksWeight: Boolean,
    val weighedToday: Boolean,
    val sleepLogged: Boolean,
    val trainingPlanned: Boolean,
    val trainedToday: Boolean,
    val foodEntries: Int,
    val proteinG: Double?,
    val proteinTargetG: Int?,
    val waterMl: Int,
    val waterTargetMl: Int?,
    /** Today's own yes/no habits still open. */
    val openHabits: List<Habit>,
)

/**
 * In order: an open session; the morning weigh-in (before noon); last night's sleep (before 2 pm);
 * today's planned workout (6 am to 9:30 pm); food when nothing is logged by 9 am or protein is
 * under half the target by 3 pm; water more than half a litre behind an even spread from 7 am to
 * 9 pm; an open habit; otherwise all done.
 */
object NextActionRules {
    val WEIGH_BY: LocalTime = LocalTime.of(12, 0)
    val SLEEP_BY: LocalTime = LocalTime.of(14, 0)
    val TRAIN_FROM: LocalTime = LocalTime.of(6, 0)
    val TRAIN_UNTIL: LocalTime = LocalTime.of(21, 30)
    val FOOD_FROM: LocalTime = LocalTime.of(9, 0)
    val PROTEIN_CHECK: LocalTime = LocalTime.of(15, 0)
    val WATER_FROM: LocalTime = LocalTime.of(7, 0)
    val WATER_UNTIL: LocalTime = LocalTime.of(21, 0)
    const val WATER_SLACK_ML = 500

    /** How much water would have been drunk by [time] if the target were spread evenly. */
    fun waterExpected(time: LocalTime, target: Int): Int {
        val from = WATER_FROM.toSecondOfDay().toDouble()
        val span = WATER_UNTIL.toSecondOfDay() - from
        val share = ((time.toSecondOfDay() - from) / span).coerceIn(0.0, 1.0)
        return (target * share).toInt()
    }

    fun pick(f: NextFacts): NextAction {
        val t = f.time
        return when {
            f.sessionOpen -> NextAction.ResumeSession
            f.tracksWeight && !f.weighedToday && t < WEIGH_BY -> NextAction.LogWeight
            !f.sleepLogged && t < SLEEP_BY -> NextAction.LogSleep
            f.trainingPlanned && !f.trainedToday && t >= TRAIN_FROM && t <= TRAIN_UNTIL -> NextAction.StartWorkout
            f.foodEntries == 0 && t >= FOOD_FROM -> NextAction.LogFood(proteinBehind = false)
            t >= PROTEIN_CHECK && f.proteinTargetG != null && (f.proteinG ?: 0.0) < f.proteinTargetG * 0.5 -> NextAction.LogFood(proteinBehind = true)
            f.waterTargetMl != null && waterExpected(t, f.waterTargetMl) - f.waterMl > WATER_SLACK_ML ->
                NextAction.DrinkWater(waterExpected(t, f.waterTargetMl) - f.waterMl)
            f.openHabits.isNotEmpty() -> NextAction.DoHabit(f.openHabits.first())
            else -> NextAction.AllDone
        }
    }
}
