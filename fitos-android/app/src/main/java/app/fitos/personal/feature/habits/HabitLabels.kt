package app.fitos.personal.feature.habits

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.habits.ActivityKind
import app.fitos.core.domain.habits.ActivityUnit
import app.fitos.core.domain.habits.DayResult
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitInput
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.habits.WeekResult
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt
import java.time.DayOfWeek
import kotlin.math.roundToInt

val SystemHabit.label: Int
    @StringRes get() = when (this) {
        SystemHabit.WATER -> R.string.system_water
        SystemHabit.SLEEP -> R.string.system_sleep
        SystemHabit.TRAINING -> R.string.system_training
        SystemHabit.NUTRITION -> R.string.system_nutrition
        SystemHabit.WEIGH_IN -> R.string.system_weigh_in
        SystemHabit.ACTIVITY -> R.string.system_activity
        SystemHabit.HOME_ACTIVITY -> R.string.system_home_activity
    }

val SystemHabit.about: Int
    @StringRes get() = when (this) {
        SystemHabit.WATER -> R.string.system_water_about
        SystemHabit.SLEEP -> R.string.system_sleep_about
        SystemHabit.TRAINING -> R.string.system_training_about
        SystemHabit.NUTRITION -> R.string.system_nutrition_about
        SystemHabit.WEIGH_IN -> R.string.system_weigh_in_about
        SystemHabit.ACTIVITY -> R.string.system_activity_about
        SystemHabit.HOME_ACTIVITY -> R.string.system_home_activity_about
    }

val SystemHabit.emoji: FitEmoji
    get() = when (this) {
        SystemHabit.WATER -> FitEmoji.DROPLET
        SystemHabit.SLEEP -> FitEmoji.SLEEPING
        SystemHabit.TRAINING -> FitEmoji.LIFTING
        SystemHabit.NUTRITION -> FitEmoji.SALAD
        SystemHabit.WEIGH_IN -> FitEmoji.SCALE
        SystemHabit.ACTIVITY -> FitEmoji.WALKING
        SystemHabit.HOME_ACTIVITY -> FitEmoji.HOUSE
    }

val ActivityKind.label: Int
    @StringRes get() = when (this) {
        ActivityKind.WALK -> R.string.activity_kind_walk
        ActivityKind.STEPS -> R.string.activity_kind_steps
        ActivityKind.HOME_WORKOUT -> R.string.activity_kind_home
        ActivityKind.OTHER -> R.string.activity_kind_other
    }

/** Emoji a custom habit can wear. */
val HABIT_EMOJI = listOf(
    FitEmoji.SPARKLES, FitEmoji.LOTUS, FitEmoji.BULB, FitEmoji.SUN, FitEmoji.MOON, FitEmoji.BULLSEYE, FitEmoji.CHECK,
    FitEmoji.APPLE, FitEmoji.CUPSTRAW, FitEmoji.SHOE, FitEmoji.BICEPS, FitEmoji.STOPWATCH, FitEmoji.TROPHY, FitEmoji.ROCKET,
)

val Habit.emojiIcon: FitEmoji
    get() = emoji?.let { key -> FitEmoji.entries.firstOrNull { it.name == key } } ?: system?.emoji ?: FitEmoji.SPARKLES

/** A ready-made habit read from the app's own logs, as added with one tap. */
fun systemInput(system: SystemHabit, name: String, trainingDaysPerWeek: Int?): HabitInput = HabitInput(
    name = name,
    emoji = null,
    kind = system.kind,
    system = system,
    frequency = system.frequency,
    target = when (system) {
        SystemHabit.TRAINING -> (trainingDaysPerWeek ?: 3).coerceIn(HabitRules.WEEKLY_TIMES).toDouble()
        SystemHabit.HOME_ACTIVITY -> HabitRules.DEFAULT_HOME_PER_WEEK.toDouble()
        SystemHabit.ACTIVITY -> HabitRules.DEFAULT_ACTIVITY_MINUTES.toDouble()
        else -> null
    },
    unit = if (system == SystemHabit.ACTIVITY) ActivityUnit.MINUTES else null,
    weekdays = emptySet(),
)

/** An amount in the habit's own unit: litres of water, hours of sleep, minutes, steps or "۴ صفحه". */
fun amountText(context: Context, habit: Habit, value: Double): String = when {
    habit.system == SystemHabit.WATER -> context.getString(R.string.amount_liters, Fmt.liters(value.roundToInt()))
    habit.system == SystemHabit.ACTIVITY && habit.unit == ActivityUnit.STEPS -> context.getString(R.string.amount_steps, Fmt.int(value.roundToInt()))
    habit.kind == HabitKind.DURATION || habit.system == SystemHabit.ACTIVITY -> durationText(context, value.roundToInt())
    else -> listOfNotNull(PersianNumbers.format(value, maxFractionDigits = 1), habit.unit).joinToString(" ")
}

/**
 * "۴ از ۱۰ صفحه", "۰٫۷۵ از ۲٫۵ لیتر", "۷٫۳ از ۷٫۵ ساعت", "۱۵ از ۲۰ دقیقه": the unit once, so a
 * status line stays on one line.
 */
fun amountPair(context: Context, habit: Habit, value: Double, target: Double): String {
    fun n(v: Double, digits: Int = 1) = PersianNumbers.format(v, maxFractionDigits = digits)
    val (v, t, unit) = when {
        habit.system == SystemHabit.WATER -> Triple(Fmt.liters(value.roundToInt()), Fmt.liters(target.roundToInt()), context.getString(R.string.unit_liter))
        habit.system == SystemHabit.ACTIVITY && habit.unit == ActivityUnit.STEPS -> Triple(Fmt.int(value.roundToInt()), Fmt.int(target.roundToInt()), context.getString(R.string.unit_steps_word))
        habit.kind == HabitKind.DURATION || habit.system == SystemHabit.ACTIVITY ->
            if (target >= 60) Triple(n(value / 60), n(target / 60), context.getString(R.string.unit_hour))
            else Triple(Fmt.int(value.roundToInt()), Fmt.int(target.roundToInt()), context.getString(R.string.unit_minutes_word))
        else -> Triple(n(value), n(target), habit.unit)
    }
    return context.getString(R.string.habit_status_amount, v, t).let { if (unit.isNullOrBlank()) it else "$it $unit" }
}

fun durationText(context: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> context.getString(R.string.amount_minutes, Fmt.int(m))
        m == 0 -> context.getString(R.string.amount_hours, Fmt.int(h))
        else -> context.getString(R.string.amount_hours_minutes, Fmt.int(h), Fmt.int(m))
    }
}

@Composable
fun durationText(minutes: Int): String = durationText(LocalContext.current, minutes)

/** "۸۵٪". */
fun percentText(score: Double): String = PersianNumbers.digits((score * 100).roundToInt().toString()) + "٪"

/** One line under a habit: how the day stands, in words. */
@Composable
fun statusLine(habit: Habit, result: DayResult, week: WeekResult?): String {
    val context = LocalContext.current
    val core = when {
        result.outcome == Outcome.SKIPPED -> stringResource(R.string.habit_status_skipped)
        week != null && habit.frequency == HabitFrequency.WEEKLY -> stringResource(R.string.habit_status_week, Fmt.int(week.done), Fmt.int(week.needed))
        result.value != null && result.target != null && habit.kind != HabitKind.BOOLEAN ->
            amountPair(context, habit, result.value!!, result.target!!)
        result.outcome == Outcome.DONE -> stringResource(R.string.habit_status_done)
        result.outcome == Outcome.NOT_DONE -> stringResource(R.string.habit_status_not_done)
        else -> stringResource(R.string.habit_status_not_logged)
    }
    return if (result.auto && result.outcome != Outcome.SKIPPED) stringResource(R.string.habit_status_auto, core) else core
}

/** "هر روز" or "شنبه، سه‌شنبه". */
@Composable
fun weekdaysText(days: Set<DayOfWeek>): String =
    if (days.isEmpty()) stringResource(R.string.reminder_every_day) else JalaliCalendar.WEEK_ORDER.filter { it in days }.joinToString("، ") { JalaliCalendar.weekdayName(it) }
