package app.fitos.personal.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.Plate
import app.fitos.core.designsystem.component.PlateMeter
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.habits.LifestyleRules
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.today.DayItem
import app.fitos.core.domain.today.DayScore
import app.fitos.core.domain.today.DayScoreRules
import app.fitos.core.domain.today.ItemScore
import app.fitos.core.domain.today.NextAction
import app.fitos.core.domain.today.NotCounted
import app.fitos.core.domain.training.VariantKind
import app.fitos.personal.R
import app.fitos.personal.feature.habits.FitSheet
import app.fitos.personal.feature.nutrition.label
import app.fitos.personal.feature.report.highlightText
import app.fitos.personal.feature.report.weekLabel
import app.fitos.personal.ui.Fmt
import kotlin.math.roundToInt

internal val DayItem.module: Module
    get() = when (this) {
        DayItem.TRAINING -> Module.Training
        DayItem.CALORIES -> Module.Calories
        DayItem.PROTEIN -> Module.Protein
        DayItem.WATER -> Module.Water
        DayItem.SLEEP -> Module.Sleep
        DayItem.HABITS -> Module.Habits
    }

private fun percentText(fraction: Double): String = PersianNumbers.digits((fraction * 100).roundToInt().toString()) + "٪"

/** The short status under each tile: what was logged, "not logged", or why it does not count. */
@Composable
private fun shortStatus(s: ItemScore): String = when {
    s.notCounted == NotCounted.NO_PROGRAM -> stringResource(R.string.today_tile_no_program)
    s.notCounted == NotCounted.REST_DAY -> stringResource(R.string.today_tile_rest)
    s.notCounted == NotCounted.NO_HABITS -> stringResource(R.string.today_tile_no_habits)
    s.notCounted == NotCounted.NO_TARGET && s.value == null -> stringResource(R.string.today_tile_no_target)
    !s.logged -> stringResource(R.string.home_not_logged)
    else -> when (s.item) {
        DayItem.TRAINING -> stringResource(R.string.home_done)
        DayItem.CALORIES -> Fmt.int(s.value!!.roundToInt())
        DayItem.PROTEIN -> stringResource(R.string.grams_value, Fmt.int(s.value!!.roundToInt()))
        DayItem.WATER -> stringResource(R.string.amount_liters, Fmt.liters(s.value!!.toInt()))
        DayItem.SLEEP -> stringResource(R.string.home_sleep_status, Fmt.hours(s.value!!.toInt()))
        DayItem.HABITS -> stringResource(R.string.home_habits_status, Fmt.int(s.done), Fmt.int(s.total))
    }
}

/**
 * Today's completion: one plate per item that counts, filled by its share; tiles say what was
 * logged. Tapping opens how the number was worked out.
 */
@Composable
internal fun TodayCard(score: DayScore?, onDetails: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FitTheme.colors
    val percent = PersianNumbers.digits((score?.percent ?: 0).toString()) + "٪"
    val details = stringResource(R.string.today_details_label)
    FitCard(modifier.clip(RoundedCornerShape(32.dp)).clickable(onClickLabel = details, role = Role.Button, onClick = onDetails), hero = true) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.today_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
            Text(percent, style = MaterialTheme.typography.displayMedium, color = colors.primary)
        }
        val items = score?.items.orEmpty()
        PlateMeter(
            plates = items.filter { it.counts }.map { Plate(fraction = it.score!!.toFloat(), color = it.item.module.color(colors).fill) },
            description = stringResource(R.string.home_meter_description, percent),
        )
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.gapRelated)) {
                row.forEach { s ->
                    val module = s.item.module
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        EmojiTile(module.emoji, size = 32.dp, tint = if (s.counts) module.color(colors).container else colors.surfaceContainer)
                        Column {
                            Text(stringResource(module.label), style = MaterialTheme.typography.bodySmall, color = colors.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                shortStatus(s), style = MaterialTheme.typography.labelLarge, color = if (s.counts) colors.onSurface else colors.subtle,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Every item with its share, what was logged against what target, and the rule. */
@Composable
internal fun DayScoreSheet(score: DayScore, onDismiss: () -> Unit) {
    val colors = FitTheme.colors
    FitSheet(stringResource(R.string.today_details_title), onDismiss) {
        score.items.forEach { s ->
            val module = s.item.module
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(FitSpacing.s12).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiTile(module.emoji, size = 36.dp, tint = if (s.counts) module.color(colors).container else colors.surface)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(module.label), style = MaterialTheme.typography.titleSmall)
                    Text(detailLine(s), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Text(
                    s.score?.let(::percentText) ?: stringResource(R.string.today_not_counted),
                    style = if (s.counts) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                    color = if (s.counts) colors.primary else colors.subtle,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(stringResource(R.string.today_details_rule), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
}

@Composable
private fun detailLine(s: ItemScore): String = when {
    s.notCounted == NotCounted.NO_PROGRAM -> stringResource(R.string.today_no_program)
    s.notCounted == NotCounted.REST_DAY -> stringResource(R.string.today_rest_day)
    s.notCounted == NotCounted.NO_TARGET -> stringResource(R.string.today_no_target)
    s.notCounted == NotCounted.NO_HABITS -> stringResource(R.string.today_no_habits)
    s.item == DayItem.TRAINING -> when {
        !s.logged -> stringResource(R.string.today_planned_not_done)
        s.variant == VariantKind.HOME -> stringResource(R.string.today_done_home)
        s.variant == VariantKind.MINIMAL -> stringResource(R.string.today_done_minimal)
        else -> stringResource(R.string.today_done_full)
    }
    s.item == DayItem.HABITS -> stringResource(R.string.today_habits_line, Fmt.int(s.done), Fmt.int(s.total))
    else -> {
        val v = s.value ?: 0.0
        val t = s.target ?: 0.0
        when (s.item) {
            DayItem.CALORIES -> {
                val line = stringResource(
                    R.string.today_kcal_line, Fmt.int(v.roundToInt()), Fmt.int(t.roundToInt()),
                    Fmt.int((t * (1 - DayScoreRules.KCAL_BAND)).roundToInt()), Fmt.int((t * (1 + DayScoreRules.KCAL_BAND)).roundToInt()),
                )
                if (v > t * (1 + DayScoreRules.KCAL_BAND)) line + "، " + stringResource(R.string.today_kcal_over) else line
            }
            DayItem.PROTEIN -> stringResource(R.string.today_protein_line, Fmt.int(v.roundToInt()), Fmt.int(t.roundToInt()))
            DayItem.WATER -> stringResource(R.string.today_water_line, Fmt.liters(v.toInt()), Fmt.liters(t.toInt()))
            DayItem.SLEEP -> stringResource(R.string.today_sleep_line, Fmt.hours(v.toInt()), Fmt.hours(t.toInt()))
            else -> ""
        }
    }
}

/** What Home suggests doing next, with the button that does it. */
@Composable
internal fun NextActionCard(
    next: NextAction,
    nextDayName: String?,
    meal: Meal,
    proteinG: Double?,
    proteinTargetG: Int?,
    onResume: () -> Unit,
    onLogWeight: () -> Unit,
    onLogSleep: () -> Unit,
    onStartWorkout: () -> Unit,
    onLogFood: () -> Unit,
    onAddWater: (Int) -> Unit,
    onHabitDone: () -> Unit,
) {
    val colors = FitTheme.colors
    val mealName = stringResource(meal.label)
    data class Shown(val emoji: FitEmoji, val title: String, val body: String, val action: String?, val icon: ImageVector, val onClick: () -> Unit)
    val shown = when (next) {
        NextAction.ResumeSession -> Shown(FitEmoji.LIFTING, stringResource(R.string.next_resume), stringResource(R.string.next_resume_body), stringResource(R.string.next_resume_action), FitIcons.Play, onResume)
        NextAction.LogWeight -> Shown(FitEmoji.SCALE, stringResource(R.string.next_weight), stringResource(R.string.next_weight_body), stringResource(R.string.home_log_weight), FitIcons.Scale, onLogWeight)
        NextAction.LogSleep -> Shown(FitEmoji.SLEEPING, stringResource(R.string.next_sleep), stringResource(R.string.next_sleep_body), stringResource(R.string.next_sleep_action), FitIcons.Moon, onLogSleep)
        NextAction.StartWorkout -> Shown(FitEmoji.LIFTING, stringResource(R.string.next_workout, nextDayName.orEmpty()), stringResource(R.string.next_workout_body), stringResource(R.string.next_workout_action), FitIcons.Dumbbell, onStartWorkout)
        is NextAction.LogFood -> if (next.proteinBehind) {
            Shown(
                FitEmoji.POULTRY, stringResource(R.string.next_food_protein, Fmt.int((proteinG ?: 0.0).roundToInt()), Fmt.int(proteinTargetG ?: 0)),
                stringResource(R.string.next_food_protein_body), stringResource(R.string.next_food_action_any), FitIcons.Utensils, onLogFood,
            )
        } else {
            Shown(FitEmoji.PLATE, stringResource(R.string.next_food), stringResource(R.string.next_food_body, mealName), stringResource(R.string.next_food_action, mealName), FitIcons.Utensils, onLogFood)
        }
        is NextAction.DrinkWater -> Shown(
            FitEmoji.DROPLET, stringResource(R.string.next_water, Fmt.int((next.ml / 50) * 50)), stringResource(R.string.next_water_body),
            stringResource(R.string.water_glass_home), FitIcons.Droplet,
        ) { onAddWater(LifestyleRules.GLASS_ML) }
        is NextAction.DoHabit -> Shown(FitEmoji.CHECK, stringResource(R.string.next_habit, next.habit.name), stringResource(R.string.next_habit_body), stringResource(R.string.next_habit_action), FitIcons.Check, onHabitDone)
        NextAction.AllDone -> Shown(FitEmoji.SPARKLES, stringResource(R.string.next_done), stringResource(R.string.next_done_body), null, FitIcons.Check) {}
    }
    FitCard {
        Text(stringResource(R.string.next_title), style = MaterialTheme.typography.labelLarge, color = colors.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(shown.emoji, tint = colors.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(shown.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(shown.body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        shown.action?.let { FitPrimaryButton(it, onClick = shown.onClick, icon = shown.icon, modifier = Modifier.fillMaxWidth()) }
    }
}

/** One-tap logs: a glass of water, weight, food, habits and the workout. */
@Composable
internal fun QuickLogCard(onWater: () -> Unit, onWeight: () -> Unit, onFood: () -> Unit, onHabits: () -> Unit, onWorkout: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Text(stringResource(R.string.quick_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val waterDescription = stringResource(R.string.quick_water_description, Fmt.int(LifestyleRules.GLASS_ML))
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            QuickButton(stringResource(R.string.quick_water), FitIcons.Droplet, colors.water.container, colors.water.onContainer, onWater, Modifier.weight(1f), waterDescription)
            QuickButton(stringResource(R.string.quick_weight), FitIcons.Scale, colors.primaryContainer, colors.onPrimaryContainer, onWeight, Modifier.weight(1f))
            QuickButton(stringResource(R.string.quick_food), FitIcons.Utensils, colors.calories.container, colors.calories.onContainer, onFood, Modifier.weight(1f))
            QuickButton(stringResource(R.string.quick_habit), FitIcons.Check, colors.habits.container, colors.habits.onContainer, onHabits, Modifier.weight(1f))
            QuickButton(stringResource(R.string.quick_workout), FitIcons.Dumbbell, colors.training.container, colors.training.onContainer, onWorkout, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickButton(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    ink: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = FitSpacing.s8)
            .semantics(mergeDescendants = true) { description?.let { contentDescription = it } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s4, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = ink)
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
    }
}

/** Last week's report, until it is opened. */
@Composable
internal fun ReportReadyCard(report: HomeReport, onOpen: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.BARCHART, tint = colors.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.report_ready_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.report_ready_body, weekLabel(report.weekStart)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        report.highlight?.let { Text(highlightText(it), style = MaterialTheme.typography.bodyMedium) }
        if (report.calorieAdvice) Text(stringResource(R.string.report_has_suggestion), style = MaterialTheme.typography.bodySmall, color = colors.primary)
        FitPrimaryButton(stringResource(R.string.report_open), onClick = onOpen, icon = FitIcons.Chart, modifier = Modifier.fillMaxWidth())
    }
}
