package app.fitos.personal.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.EmptyState
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.CategoryColor
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.habits.ActivityEntry
import app.fitos.core.domain.habits.Adherence
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.LifestyleRules
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.SleepEntry
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.habits.TrendPoint
import app.fitos.core.domain.habits.WaterEntry
import app.fitos.core.domain.time.DateFormats
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel,
    today: LocalDate,
    zone: ZoneId,
    onOpenHabit: (String) -> Unit,
    onNewHabit: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenBody: () -> Unit,
) {
    LaunchedEffect(today) { viewModel.setToday(today) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val undo = stringResource(R.string.action_undo)
    var answering by remember { mutableStateOf<HabitRow?>(null) }
    var waterOther by remember { mutableStateOf(false) }
    var sleepSheet by remember { mutableStateOf(false) }
    var activitySheet by remember { mutableStateOf<ActivityEntry?>(null) }
    var newActivity by remember { mutableStateOf(false) }

    fun undoable(message: String, restore: () -> Unit) {
        scope.launch {
            if (snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) restore()
        }
    }

    fun log(system: SystemHabit) = when (system) {
        SystemHabit.WATER -> viewModel.addWater(LifestyleRules.GLASS_ML)
        SystemHabit.SLEEP -> sleepSheet = true
        SystemHabit.ACTIVITY, SystemHabit.HOME_ACTIVITY -> newActivity = true
        SystemHabit.WEIGH_IN -> onOpenBody()
        SystemHabit.TRAINING -> onOpenTraining()
        SystemHabit.NUTRITION -> onOpenNutrition()
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_title), style = MaterialTheme.typography.titleLarge) },
                actions = { IconButton(onClick = onOpenReminders) { Icon(FitIcons.Bell, contentDescription = stringResource(R.string.reminders_title)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onSurface, actionIconContentColor = colors.onSurface),
            )
        },
    ) { padding ->
        if (!ui.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = FitSpacing.gutter, end = FitSpacing.gutter, bottom = FitSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            item { DayBar(ui.day, ui.today, viewModel::show) }
            if (ui.active.isEmpty()) {
                item {
                    FitCard {
                        EmptyState(FitEmoji.SPARKLES, stringResource(R.string.habits_empty_title), stringResource(R.string.habits_empty_body))
                        Suggestions(ui.suggestions) { s, name -> viewModel.addSuggestion(s, name) }
                        FitSecondaryButton(stringResource(R.string.habits_new), onClick = onNewHabit, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                item { AdherenceCard(ui.overall, ui.trend) }
                item {
                    FitCard(contentPadding = PaddingValues(start = FitSpacing.s16, end = FitSpacing.s8, top = FitSpacing.s16, bottom = FitSpacing.s8)) {
                        Text(stringResource(R.string.habits_list_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        if (ui.rows.isEmpty()) {
                            Text(stringResource(R.string.habits_none_due), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                        ui.rows.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(color = colors.outlineVariant)
                            HabitItem(row, onToggle = { viewModel.toggle(row) }, onAnswer = { answering = row }, onLog = ::log)
                        }
                    }
                }
            }
            item {
                WaterCard(
                    ui.water, ui.waterMl, ui.waterTargetMl, zone,
                    onAdd = viewModel::addWater,
                    onOther = { waterOther = true },
                    onDelete = { entry ->
                        viewModel.deleteWater(entry)
                        undoable(context.getString(R.string.water_deleted, Fmt.int(entry.amountMl))) { viewModel.restoreWater(entry) }
                    },
                )
            }
            item { SleepCard(ui.day, ui.sleep, ui.sleepTargetMinutes, onLog = { sleepSheet = true }) }
            item { ActivityCard(ui.activities, onAdd = { newActivity = true }, onOpen = { activitySheet = it }) }
            if (ui.active.isNotEmpty()) {
                if (ui.suggestions.isNotEmpty()) item { FitCard { Suggestions(ui.suggestions) { s, name -> viewModel.addSuggestion(s, name) } } }
                item { FitPrimaryButton(stringResource(R.string.habits_new), onClick = onNewHabit, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth()) }
            }
            if (ui.archived.isNotEmpty()) {
                item {
                    FitCard {
                        Text(stringResource(R.string.habits_archived), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        ui.archived.forEach { h ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenHabit(h.id) }.heightIn(min = FitSpacing.target),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
                            ) {
                                EmojiTile(h.emojiIcon, size = 32.dp)
                                Text(h.name, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Icon(FitIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    answering?.let { row ->
        HabitDaySheet(
            row.habit, ui.day, row.result,
            onDismiss = { answering = null },
            onAnswer = { status, value -> viewModel.answer(row.habit, ui.day, status, value); answering = null },
            onClear = { viewModel.clear(row.habit, ui.day); answering = null },
            onDetails = { answering = null; onOpenHabit(row.habit.id) },
        )
    }
    if (waterOther) {
        WaterAmountSheet(onDismiss = { waterOther = false }, onAdd = { viewModel.addWater(it); waterOther = false })
    }
    if (sleepSheet) {
        val existing = ui.sleep
        SleepSheet(
            ui.day, existing,
            onDismiss = { sleepSheet = false },
            onSave = { bed, wake, quality, note -> viewModel.saveSleep(bed, wake, quality, note); sleepSheet = false },
            onDelete = existing?.let {
                {
                    sleepSheet = false
                    viewModel.deleteSleep(it)
                    undoable(context.getString(R.string.sleep_deleted)) { viewModel.restoreSleep(it) }
                }
            },
        )
    }
    if (newActivity || activitySheet != null) {
        val existing = activitySheet
        ActivitySheet(
            existing,
            onDismiss = { newActivity = false; activitySheet = null },
            onSave = { input -> viewModel.saveActivity(existing?.id, input); newActivity = false; activitySheet = null },
            onDelete = existing?.let {
                {
                    activitySheet = null
                    viewModel.deleteActivity(it)
                    undoable(context.getString(R.string.activity_deleted)) { viewModel.restoreActivity(it) }
                }
            },
        )
    }
}

@Composable
internal fun DayBar(day: LocalDate, today: LocalDate, onShow: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShow(day.minusDays(1)) }) { Icon(FitIcons.ChevronLeft, contentDescription = stringResource(R.string.day_previous)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (day) {
                    today -> stringResource(R.string.habits_today) + " · " + DateFormats.longDate(day)
                    today.minusDays(1) -> stringResource(R.string.habits_yesterday) + " · " + DateFormats.longDate(day)
                    else -> DateFormats.longDate(day)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (day != today) FitTextButton(stringResource(R.string.days_ago_today), onClick = { onShow(today) })
        }
        IconButton(onClick = { onShow(day.plusDays(1)) }, enabled = day < today) { Icon(FitIcons.ChevronRight, contentDescription = stringResource(R.string.day_next)) }
    }
}

/** Four-week average and eight weekly bars; never a streak that a missed day would wipe out. */
@Composable
private fun AdherenceCard(overall: Adherence, trend: List<TrendPoint>) {
    val colors = FitTheme.colors
    FitCard(hero = true) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.habits_adherence_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            overall.score?.let { Text(percentText(it), style = numeralStyle(32), color = colors.habits.ink) }
        }
        if (overall.score == null) {
            Text(stringResource(R.string.habits_adherence_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        TrendBars(trend, colors.habits)
        Text(stringResource(R.string.habits_adherence_note), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
}

/** Eight weekly bars in reading order (oldest at the right in RTL); the running week is lighter. */
@Composable
internal fun TrendBars(trend: List<TrendPoint>, color: CategoryColor) {
    val colors = FitTheme.colors
    val noData = stringResource(R.string.habits_trend_no_data)
    val description = stringResource(R.string.habits_trend_description, trend.joinToString("، ") { p -> p.score?.let { percentText(it) } ?: noData })
    Row(
        Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
        verticalAlignment = Alignment.Bottom,
    ) {
        trend.forEach { p ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                val f = (p.score ?: 0.0).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(if (p.score == null) 0.04f else maxOf(f, 0.04f))
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (p.score == null) colors.surfaceContainerHigh else if (p.inProgress) color.container else color.fill),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
        trend.forEach { p ->
            Text(
                DateFormats.dayNumber(p.weekStart),
                style = MaterialTheme.typography.labelSmall,
                color = colors.subtle,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HabitItem(row: HabitRow, onToggle: () -> Unit, onAnswer: () -> Unit, onLog: (SystemHabit) -> Unit) {
    val colors = FitTheme.colors
    val habit = row.habit
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAnswer).heightIn(min = 56.dp).padding(vertical = FitSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
    ) {
        EmojiTile(habit.emojiIcon, size = 40.dp, tint = colors.habits.container)
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleSmall)
            Text(
                statusLine(habit, row.result, row.week),
                style = MaterialTheme.typography.bodySmall,
                color = if (row.result.outcome == Outcome.DONE) colors.habits.ink else colors.subtle,
            )
            row.adherence.score?.let {
                Text(stringResource(R.string.habit_adherence_short, percentText(it)), style = MaterialTheme.typography.labelSmall, color = colors.subtle)
            }
        }
        val system = habit.system
        when {
            system == null && habit.kind == HabitKind.BOOLEAN -> CheckCircle(row.result.outcome == Outcome.DONE, habit.name, onToggle)
            system == SystemHabit.WATER -> FitTextButton(stringResource(R.string.water_glass), onClick = { onLog(system) })
            system != null -> FitTextButton(stringResource(R.string.habit_action_log), onClick = { onLog(system) })
            else -> FitTextButton(stringResource(R.string.habit_action_log), onClick = onAnswer)
        }
    }
}

/** One tap marks done; the circle says which way it will go. */
@Composable
internal fun CheckCircle(done: Boolean, name: String, onToggle: () -> Unit) {
    val colors = FitTheme.colors
    val description = if (done) stringResource(R.string.habit_unmark, name) else stringResource(R.string.habit_mark_done, name)
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (done) colors.habits.fill else Color.Transparent)
                .border(2.dp, if (done) colors.habits.fill else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(FitIcons.Check, contentDescription = null, tint = colors.habits.onFill, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<SystemHabit>, onAdd: (SystemHabit, String) -> Unit) {
    if (suggestions.isEmpty()) return
    val context = LocalContext.current
    Text(stringResource(R.string.habits_suggestions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    FitChips(suggestions, emptySet(), { s -> onAdd(s, context.getString(s.label)) }, label = { "+ " + stringResource(it.label) })
}

@Composable
private fun ProgressLine(fraction: Double, color: Color) {
    val colors = FitTheme.colors
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
private fun WaterCard(entries: List<WaterEntry>, totalMl: Int?, targetMl: Int?, zone: ZoneId, onAdd: (Int) -> Unit, onOther: () -> Unit, onDelete: (WaterEntry) -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.DROPLET, tint = colors.water.container)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.water_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(
                    when {
                        totalMl == null -> stringResource(R.string.water_none)
                        targetMl != null -> stringResource(R.string.water_progress, Fmt.liters(totalMl), Fmt.liters(targetMl))
                        else -> stringResource(R.string.water_total, Fmt.liters(totalMl))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.subtle,
                )
            }
        }
        if (targetMl != null) ProgressLine((totalMl ?: 0) / targetMl.toDouble(), colors.water.fill)
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            listOf(LifestyleRules.GLASS_ML, 500).forEach { ml ->
                val description = stringResource(R.string.water_add_description, Fmt.int(ml))
                FitSecondaryButton(
                    Fmt.int(ml),
                    onClick = { onAdd(ml) },
                    icon = FitIcons.Plus,
                    modifier = Modifier.weight(1f).semantics { contentDescription = description },
                )
            }
            FitSecondaryButton(stringResource(R.string.water_add_other_short), onClick = onOther, modifier = Modifier.weight(1f))
        }
        entries.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.water_entry, timeText(e.loggedAt.atZone(zone).toLocalTime()), Fmt.int(e.amountMl)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onDelete(e) }) {
                    Icon(FitIcons.Trash, contentDescription = stringResource(R.string.water_delete, Fmt.int(e.amountMl)), tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SleepCard(day: LocalDate, sleep: SleepEntry?, targetMinutes: Int?, onLog: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.SLEEPING, tint = colors.sleep.container)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sleep_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.sleep_night_of, DateFormats.dayMonth(day)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
            if (sleep != null) Text(durationText(sleep.durationMinutes), style = MaterialTheme.typography.titleSmall, color = colors.sleep.ink)
        }
        if (sleep == null) {
            Text(stringResource(R.string.sleep_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        } else {
            val parts = listOfNotNull(
                if (sleep.bedtime != null && sleep.wake != null) stringResource(R.string.sleep_range, timeText(sleep.bedtime!!.toLocalTime()), timeText(sleep.wake!!.toLocalTime())) else null,
                sleep.quality?.let { stringResource(R.string.sleep_quality_line, Fmt.int(it)) },
                targetMinutes?.let { stringResource(R.string.sleep_target, durationText(it)) },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (targetMinutes != null) ProgressLine(sleep.durationMinutes / targetMinutes.toDouble(), colors.sleep.fill)
        }
        FitSecondaryButton(stringResource(if (sleep == null) R.string.sleep_log else R.string.sleep_edit), onClick = onLog, icon = if (sleep == null) FitIcons.Plus else FitIcons.Edit, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ActivityCard(entries: List<ActivityEntry>, onAdd: () -> Unit, onOpen: (ActivityEntry) -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(FitEmoji.WALKING, tint = colors.training.container)
            Text(stringResource(R.string.activity_card_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        }
        if (entries.isEmpty()) Text(stringResource(R.string.activity_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        entries.forEach { e ->
            val line = listOfNotNull(
                stringResource(e.kind.label),
                e.durationMinutes?.let { durationText(it) },
                e.steps?.let { stringResource(R.string.amount_steps, Fmt.int(it)) },
            ).joinToString(" · ")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(e) }.heightIn(min = FitSpacing.target),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(FitIcons.Edit, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        FitSecondaryButton(stringResource(R.string.activity_log), onClick = onAdd, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
    }
}
