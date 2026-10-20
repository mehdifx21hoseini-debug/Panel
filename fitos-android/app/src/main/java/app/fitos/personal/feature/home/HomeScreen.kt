package app.fitos.personal.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.personal.ui.Fmt
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitEmojiImage
import app.fitos.core.designsystem.component.HeroBand
import app.fitos.core.designsystem.component.Plate
import app.fitos.core.designsystem.component.PlateMeter
import app.fitos.core.designsystem.component.WeekDay
import app.fitos.core.designsystem.component.WeekStrip
import app.fitos.core.designsystem.component.pullUp
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.CategoryColor
import app.fitos.core.designsystem.theme.FitColors
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.personal.R
import java.time.LocalDate

/** The six daily modules, in plate order from the collar outwards. */
private enum class Module(@param:StringRes val label: Int, val emoji: FitEmoji, val color: (FitColors) -> CategoryColor) {
    Training(R.string.module_training, FitEmoji.LIFTING, { it.training }),
    Calories(R.string.module_calories, FitEmoji.FIRE, { it.calories }),
    Protein(R.string.module_protein, FitEmoji.POULTRY, { it.protein }),
    Water(R.string.module_water, FitEmoji.DROPLET, { it.water }),
    Sleep(R.string.module_sleep, FitEmoji.SLEEPING, { it.sleep }),
    Habits(R.string.module_habits, FitEmoji.CHECK, { it.habits }),
}

/**
 * Home, Stage 0: real date and week, and an empty plate meter. Logging arrives in later stages;
 * until then every module honestly reads "not logged" and the meter stays at zero.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, today: LocalDate, onOpenSettings: () -> Unit, onOpenTargets: () -> Unit) {
    LaunchedEffect(today) { viewModel.setToday(today) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, today, onOpenSettings, onOpenTargets)
}

@Composable
private fun HomeContent(state: HomeState, today: LocalDate, onOpenSettings: () -> Unit, onOpenTargets: () -> Unit) {
    val colors = FitTheme.colors
    val week = remember(today) { weekOf(today) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HeroBand {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.name?.let { stringResource(R.string.greeting_named, it) } ?: stringResource(R.string.home_greeting),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        FitEmojiImage(FitEmoji.WAVE, Modifier.padding(start = FitSpacing.s8).size(28.dp))
                    }
                    Text(DateFormats.longDate(today), style = MaterialTheme.typography.bodyMedium, color = colors.onHeroBandVariant)
                }
                IconButton(onClick = onOpenSettings) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(colors.onHeroBand.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(FitIcons.Settings, contentDescription = stringResource(R.string.home_settings), tint = colors.onHeroBand)
                    }
                }
            }
            WeekStrip(week)
        }

        Column(
            Modifier.padding(horizontal = FitSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.gapSection),
        ) {
            TodayLoadCard(Modifier.pullUp(56.dp))
            state.target?.let { TargetsCard(it, onOpenTargets) }
            WeightCard(state.latestWeight, state.goal)
            FitCard {
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
                    EmojiTile(FitEmoji.ROCKET, tint = colors.primaryContainer)
                    Text(stringResource(R.string.home_start_title), style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.home_start_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(FitSpacing.s16))
        }
    }
}

@Composable
private fun TodayLoadCard(modifier: Modifier = Modifier) {
    val colors = FitTheme.colors
    val percent = PersianNumbers.digits("0") + "٪"
    FitCard(modifier, hero = true) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_today_load), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_today_load_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
            Text(percent, style = MaterialTheme.typography.displayMedium, color = colors.primary)
        }
        PlateMeter(
            plates = Module.entries.map { Plate(fraction = 0f, color = it.color(colors).fill) },
            description = stringResource(R.string.home_meter_description, percent),
        )
        Module.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.gapRelated)) {
                row.forEach { module ->
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EmojiTile(module.emoji, size = 32.dp, tint = module.color(colors).container)
                        Column {
                            Text(stringResource(module.label), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                            Text(stringResource(R.string.home_not_logged), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                        }
                    }
                }
            }
        }
    }
}

/** Today's targets at a glance; numbers come from the dated target that applies today. */
@Composable
private fun TargetsCard(target: NutritionTarget, onEdit: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_targets_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            FitTextButton(stringResource(R.string.home_targets_edit), onClick = onEdit)
        }
        val items = listOf(
            Triple(Module.Calories, Fmt.int(target.kcal), stringResource(R.string.unit_kcal)),
            Triple(Module.Protein, Fmt.int(target.proteinG), stringResource(R.string.unit_g)),
            Triple(Module.Water, Fmt.liters(target.waterMl), stringResource(R.string.unit_liter)),
            Triple(Module.Sleep, Fmt.hours(target.sleepMinutes), stringResource(R.string.unit_hour)),
        )
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                row.forEach { (module, value, unit) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(module.color(colors).container)
                            .padding(FitSpacing.s12)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(FitSpacing.s4),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8), verticalAlignment = Alignment.CenterVertically) {
                            FitEmojiImage(module.emoji, Modifier.size(24.dp))
                            Text(stringResource(module.label), style = MaterialTheme.typography.bodySmall, color = module.color(colors).onContainer)
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                            Text(value, style = numeralStyle(22), color = module.color(colors).onContainer, maxLines = 1)
                            Text(unit, style = MaterialTheme.typography.bodySmall, color = module.color(colors).onContainer, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Latest weigh-in and the active goal; never invents progress that was not logged. */
@Composable
private fun WeightCard(latest: WeightEntry?, goal: WeightGoal?) {
    val colors = FitTheme.colors
    FitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.SCALE, tint = colors.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_weight_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                latest?.let {
                    Text(stringResource(R.string.home_weight_last, DateFormats.dayMonth(it.day)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
            }
            latest?.let {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                    Text(Fmt.kg(it.weightKg), style = numeralStyle(28), color = colors.onSurface)
                    Text(stringResource(R.string.unit_kg), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
            }
        }
        if (goal == null) {
            Text(stringResource(R.string.home_goal_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            HorizontalDivider(color = colors.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_goal_line, Fmt.kg(goal.targetWeightKg)), style = MaterialTheme.typography.bodyLarge)
                    goal.reviewDate?.let {
                        Text(stringResource(R.string.home_goal_review, DateFormats.longDate(it)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                    }
                }
                latest?.let {
                    val remaining = it.weightKg - goal.targetWeightKg
                    val start = goal.startWeightKg
                    val losing = start == null || start >= goal.targetWeightKg
                    val reached = if (losing) remaining <= 0 else remaining >= 0
                    Text(
                        if (reached) stringResource(R.string.home_goal_reached) else stringResource(R.string.home_goal_remaining, Fmt.kg(kotlin.math.abs(remaining))),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

private fun weekOf(today: LocalDate): List<WeekDay> {
    val start = JalaliCalendar.startOfWeek(today)
    return (0L until 7L).map { offset ->
        val date = start.plusDays(offset)
        WeekDay(
            initial = JalaliCalendar.weekdayInitial(date.dayOfWeek),
            number = DateFormats.dayNumber(date),
            description = DateFormats.longDate(date),
            isToday = date == today,
            isFuture = date.isAfter(today),
            isDone = false,
        )
    }
}
