package app.fitos.personal.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitEmojiImage
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
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
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.LifestyleRules
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.EasyStart
import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.ReturnEvent
import app.fitos.core.domain.training.SessionEstimate
import app.fitos.personal.R
import app.fitos.personal.feature.body.WeightEntrySheet
import app.fitos.personal.feature.habits.CheckCircle
import app.fitos.personal.feature.habits.HabitRow
import app.fitos.personal.feature.habits.emojiIcon
import app.fitos.personal.feature.habits.statusLine
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

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
 * Home: today at a glance. Only real logs fill a plate; anything not logged says so and counts as
 * zero, never as done.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    today: LocalDate,
    now: () -> LocalTime,
    onOpenSettings: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenWorkout: (programId: String, dayId: String) -> Unit,
    onOpenTraining: () -> Unit,
    onOpenSession: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    LaunchedEffect(today) { viewModel.setToday(today) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val day by viewModel.day.collectAsStateWithLifecycle()
    var logging by remember { mutableStateOf(false) }
    HomeContent(
        state, day, today, onOpenSettings, onOpenTargets, onOpenWorkout, onOpenTraining, onOpenSession, onLogWeight = { logging = true },
        onToggleHabit = viewModel::toggleHabit,
        onAddWater = viewModel::addWater,
        onChooseReturn = viewModel::chooseReturn,
        onOpenHabits = onOpenHabits,
        onOpenBackup = onOpenBackup,
    )
    if (logging) {
        WeightEntrySheet(
            editing = null,
            lastWeightKg = state.latestWeight?.weightKg,
            today = today,
            now = now(),
            onDismiss = { logging = false },
            onSave = { input ->
                viewModel.addWeight(input)
                logging = false
            },
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    day: HomeDay,
    today: LocalDate,
    onOpenSettings: () -> Unit,
    onOpenTargets: () -> Unit,
    onOpenWorkout: (programId: String, dayId: String) -> Unit,
    onOpenTraining: () -> Unit,
    onOpenSession: () -> Unit,
    onLogWeight: () -> Unit,
    onToggleHabit: (HabitRow) -> Unit,
    onAddWater: (Int) -> Unit,
    onChooseReturn: (ReturnEvent, ReturnChoice) -> Unit,
    onOpenHabits: () -> Unit,
    onOpenBackup: () -> Unit,
) {
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
            TodayLoadCard(trainingDone = state.training?.doneToday == true, eaten = state.eaten, target = state.target, day = day, modifier = Modifier.pullUp(56.dp))
            day.returnEvent?.let { ReturnCard(it, onChooseReturn) }
            state.training?.let { TrainingCard(it, onOpenWorkout, onOpenTraining, onOpenSession) }
            if (day.loaded) HabitsTodayCard(day, onToggleHabit, onAddWater, onOpenHabits)
            state.target?.let { TargetsCard(it, onOpenTargets) }
            WeightCard(state.latestWeight, state.goal, onLogWeight)
            day.backupDueDays?.let { BackupDueCard(it, onOpenBackup) }
            Spacer(Modifier.height(FitSpacing.s16))
        }
    }
}

@Composable
private fun TodayLoadCard(trainingDone: Boolean, eaten: NutrientTotals?, target: NutritionTarget?, day: HomeDay, modifier: Modifier = Modifier) {
    val colors = FitTheme.colors
    // Only real logs fill a plate: a finished session today; calories, protein, water and last
    // night's sleep against today's targets; and the share of today's habits done (each capped at
    // a full plate). The full day score, with tolerances, is stage 7.
    fun share(value: Number?, of: Number?): Float =
        if (value != null && of != null && of.toDouble() > 0) (value.toDouble() / of.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    fun fraction(module: Module): Float = when (module) {
        Module.Training -> if (trainingDone) 1f else 0f
        Module.Calories -> share(eaten?.kcal, target?.kcal)
        Module.Protein -> share(eaten?.proteinG, target?.proteinG)
        Module.Water -> share(day.waterMl, target?.waterMl)
        Module.Sleep -> share(day.sleepMinutes, target?.sleepMinutes)
        Module.Habits -> share(day.habitsDone, day.habitsCounted.size)
    }
    val percent = PersianNumbers.digits((Module.entries.sumOf { fraction(it).toDouble() } * 100 / Module.entries.size).toInt().toString()) + "٪"
    FitCard(modifier, hero = true) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_today_load), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_today_load_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
            Text(percent, style = MaterialTheme.typography.displayMedium, color = colors.primary)
        }
        PlateMeter(
            plates = Module.entries.map { Plate(fraction = fraction(it), color = it.color(colors).fill) },
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
                            val status = when {
                                module == Module.Training && trainingDone -> stringResource(R.string.home_done)
                                // The plate shows the share of the target; the tile says how much.
                                module == Module.Calories && eaten != null -> Fmt.int(eaten.kcal.roundToInt())
                                module == Module.Protein && eaten != null -> stringResource(R.string.grams_value, Fmt.int(eaten.proteinG.roundToInt()))
                                module == Module.Water && day.waterMl != null -> stringResource(R.string.amount_liters, Fmt.liters(day.waterMl))
                                module == Module.Sleep && day.sleepMinutes != null -> stringResource(R.string.home_sleep_status, Fmt.hours(day.sleepMinutes))
                                module == Module.Habits && day.habitsCounted.isNotEmpty() ->
                                    stringResource(R.string.home_habits_status, Fmt.int(day.habitsDone), Fmt.int(day.habitsCounted.size))
                                else -> stringResource(R.string.home_not_logged)
                            }
                            Text(status, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** The next session of the active program, or a pointer to the Training tab when there is none. */
@Composable
private fun TrainingCard(training: HomeTraining, onOpenWorkout: (String, String) -> Unit, onOpenTraining: () -> Unit, onOpenSession: () -> Unit) {
    val colors = FitTheme.colors
    val next = training.next
    val programId = training.programId
    val open = training.open
    FitCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                    when {
                        open != null -> onOpenSession()
                        programId != null && next != null -> onOpenWorkout(programId, next.id)
                        else -> onOpenTraining()
                    }
                }
                .heightIn(min = FitSpacing.target),
            horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiTile(FitEmoji.LIFTING, tint = colors.training.container)
            Column(Modifier.weight(1f)) {
                if (open != null) {
                    Text(
                        stringResource(R.string.home_session_open, open.dayName ?: stringResource(R.string.session_title)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.home_session_continue), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                } else if (next != null) {
                    if (training.doneToday) Text(stringResource(R.string.home_trained_today), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    training.easy?.let { easy ->
                        Text(easyText(easy), style = MaterialTheme.typography.labelLarge, color = colors.training.ink)
                    }
                    Text(stringResource(R.string.home_next_workout, next.name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.training_exercises_minutes, Fmt.int(next.full.size), Fmt.int(SessionEstimate.minutes(next.full))),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                    )
                } else {
                    Text(stringResource(R.string.home_training_none_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.home_training_none_body), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
            }
            Icon(FitIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
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
private fun WeightCard(latest: WeightEntry?, goal: WeightGoal?, onLog: () -> Unit) {
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
        FitSecondaryButton(stringResource(R.string.home_log_weight), onClick = onLog, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
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

@Composable
internal fun easyText(easy: EasyStart): String = stringResource(
    if (easy.choice == ReturnChoice.MINIMAL) R.string.easy_banner_minimal else R.string.easy_banner_lighter,
    Fmt.int(easy.number),
    Fmt.int(easy.total),
)

/** Asked once per break, calmly: carry on, two lighter sessions, or the minimal version. */
@Composable
private fun ReturnCard(event: ReturnEvent, onChoose: (ReturnEvent, ReturnChoice) -> Unit) {
    val colors = FitTheme.colors
    FitCard(hero = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.LOTUS, tint = colors.training.container)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_return_title, Fmt.int(event.gapDays)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.home_return_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        ReturnOption(R.string.home_return_lighter, R.string.home_return_lighter_body) { onChoose(event, ReturnChoice.LIGHTER) }
        ReturnOption(R.string.home_return_minimal, R.string.home_return_minimal_body) { onChoose(event, ReturnChoice.MINIMAL) }
        ReturnOption(R.string.home_return_continue, null) { onChoose(event, ReturnChoice.CONTINUE) }
        FitTextButton(stringResource(R.string.home_return_later), onClick = { onChoose(event, ReturnChoice.DISMISSED) })
    }
}

@Composable
private fun ReturnOption(@StringRes title: Int, @StringRes body: Int?, onClick: () -> Unit) {
    val colors = FitTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).clickable(onClick = onClick)
            .heightIn(min = FitSpacing.target).padding(horizontal = FitSpacing.s16, vertical = FitSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            body?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
        Icon(FitIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** Today's habits with one-tap checks, and a glass of water. */
@Composable
private fun HabitsTodayCard(day: HomeDay, onToggle: (HabitRow) -> Unit, onAddWater: (Int) -> Unit, onOpenAll: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_habits_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            FitTextButton(stringResource(R.string.home_habits_all), onClick = onOpenAll)
        }
        if (day.habits.isEmpty()) {
            Text(stringResource(R.string.home_habits_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        day.habits.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp)) {
                EmojiTile(row.habit.emojiIcon, size = 36.dp, tint = colors.habits.container)
                Column(Modifier.weight(1f)) {
                    Text(row.habit.name, style = MaterialTheme.typography.bodyLarge)
                    Text(statusLine(row.habit, row.result, row.week), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
                if (row.habit.system == null && row.habit.kind == HabitKind.BOOLEAN) {
                    CheckCircle(row.result.outcome == Outcome.DONE, row.habit.name) { onToggle(row) }
                }
            }
        }
        val description = stringResource(R.string.water_add_description, Fmt.int(LifestyleRules.GLASS_ML))
        FitSecondaryButton(
            stringResource(R.string.water_glass_home),
            onClick = { onAddWater(LifestyleRules.GLASS_ML) },
            icon = FitIcons.Droplet,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        )
    }
}

/** A plain nudge when the last backup is a week old and no automatic backup covers it. */
@Composable
private fun BackupDueCard(days: Int, onOpen: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.FLOPPY, tint = colors.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_backup_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    if (days < 0) stringResource(R.string.home_backup_never) else stringResource(R.string.home_backup_body, Fmt.int(days)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        FitSecondaryButton(stringResource(R.string.home_backup_now), onClick = onOpen, icon = FitIcons.Download, modifier = Modifier.fillMaxWidth())
    }
}
