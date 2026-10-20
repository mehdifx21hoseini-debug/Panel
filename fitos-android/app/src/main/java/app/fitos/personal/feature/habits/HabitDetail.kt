package app.fitos.personal.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.habits.Adherence
import app.fitos.core.domain.habits.DayFactsSource
import app.fitos.core.domain.habits.DayResult
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitBoard
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitRules
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.Outcome
import app.fitos.core.domain.habits.TrendPoint
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HabitDetailUi(
    val loaded: Boolean = false,
    val habit: Habit? = null,
    val results: Map<LocalDate, DayResult> = emptyMap(),
    val adherence: Adherence = Adherence(null, 0, 0),
    val trend: List<TrendPoint> = emptyList(),
)

class HabitDetailViewModel(
    private val habits: HabitRepository,
    facts: DayFactsSource,
    private val habitId: String,
    private val today: LocalDate,
) : ViewModel() {
    private val from = JalaliCalendar.startOfWeek(today).minusWeeks(7)

    val ui: StateFlow<HabitDetailUi> = combine(habits.habit(habitId), habits.logs(from, today), facts.facts(from, today)) { h, logs, f ->
        if (h == null) return@combine HabitDetailUi(loaded = true)
        val results = HabitBoard.results(h, logs, f, from, today)
        HabitDetailUi(
            loaded = true,
            habit = h,
            results = results,
            adherence = HabitRules.adherence(h, today.minusDays(27), today, today, results),
            trend = HabitRules.trend(h, 8, today, results),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUi())

    fun answer(day: LocalDate, status: HabitStatus, value: Double?) = viewModelScope.launch {
        val h = ui.value.habit ?: return@launch
        val s = if (value != null && h.kind != HabitKind.BOOLEAN && status != HabitStatus.SKIPPED) {
            if (h.target == null || value >= h.target!!) HabitStatus.DONE else HabitStatus.NOT_DONE
        } else {
            status
        }
        habits.setLog(habitId, day, s, value)
    }

    fun clear(day: LocalDate) = viewModelScope.launch { habits.clearLog(habitId, day) }
}

@Composable
fun HabitDetailScreen(viewModel: HabitDetailViewModel, today: LocalDate, onBack: () -> Unit, onEdit: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var answering by remember { mutableStateOf<LocalDate?>(null) }
    val habit = ui.habit
    if (!ui.loaded) return
    if (habit == null) {
        // Deleted from the editor: nothing left to show.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    EditScaffold(title = habit.name, onBack = onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(habit.emojiIcon, tint = colors.habits.container)
            Text(
                habit.system?.let { stringResource(it.about) } ?: describe(habit),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        FitCard(hero = true) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.habits_adherence_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                ui.adherence.score?.let { Text(percentText(it), style = numeralStyle(32), color = colors.habits.ink) }
            }
            Text(
                when {
                    ui.adherence.score == null -> stringResource(R.string.habits_adherence_none)
                    habit.frequency == HabitFrequency.WEEKLY -> stringResource(R.string.habit_detail_counted_weeks, Fmt.int(ui.adherence.counted), Fmt.int(ui.adherence.full))
                    else -> stringResource(R.string.habit_detail_counted_days, Fmt.int(ui.adherence.counted), Fmt.int(ui.adherence.full))
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtle,
            )
            Text(stringResource(R.string.habit_detail_trend), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            TrendBars(ui.trend, colors.habits)
        }
        FitCard {
            Text(stringResource(R.string.habit_detail_calendar), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.habit_detail_calendar_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            Calendar(habit, ui.results, today) { answering = it }
            Legend()
        }
        FitSecondaryButton(stringResource(R.string.habit_edit), onClick = onEdit, icon = FitIcons.Edit, modifier = Modifier.fillMaxWidth())
    }
    answering?.let { day ->
        HabitDaySheet(
            habit, day, ui.results[day] ?: HabitRules.day(habit, null, null),
            onDismiss = { answering = null },
            onAnswer = { status, value -> viewModel.answer(day, status, value); answering = null },
            onClear = { viewModel.clear(day); answering = null },
            onDetails = null,
        )
    }
}

/** "روزانه · هدف ۱۰ صفحه" for an own habit. */
@Composable
private fun describe(habit: Habit): String {
    val context = LocalContext.current
    val parts = mutableListOf<String>()
    if (habit.frequency == HabitFrequency.WEEKLY) {
        parts += stringResource(R.string.habit_weekly) + ": " + Fmt.int((habit.target ?: 1.0).toInt())
    } else {
        parts += weekdaysText(habit.weekdays)
        habit.target?.let { parts += stringResource(R.string.habit_target) + " " + amountText(context, habit, it) }
    }
    return parts.joinToString(" · ")
}

/** Five weeks, Saturday first, each day coloured by how it went. */
@Composable
private fun Calendar(habit: Habit, results: Map<LocalDate, DayResult>, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val colors = FitTheme.colors
    val start = JalaliCalendar.startOfWeek(today).minusWeeks(4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        JalaliCalendar.WEEK_ORDER.forEach { d ->
            Text(JalaliCalendar.weekdayInitial(d), style = MaterialTheme.typography.labelMedium, color = colors.subtle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    (0L until 5L).forEach { w ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
            (0L until 7L).forEach { i ->
                val day = start.plusWeeks(w).plusDays(i)
                val due = day <= today && HabitRules.isDue(habit.copy(archived = false), day)
                val r = results[day]
                val outcome = r?.outcome
                val (bg, border, fg) = when {
                    !due -> Triple(Color.Transparent, Color.Transparent, colors.outlineVariant)
                    outcome == Outcome.DONE -> Triple(colors.habits.fill, colors.habits.fill, colors.habits.onFill)
                    outcome == Outcome.PARTIAL -> Triple(colors.habits.container, colors.habits.container, colors.habits.onContainer)
                    outcome == Outcome.SKIPPED -> Triple(colors.surfaceContainerHigh, colors.surfaceContainerHigh, colors.onSurfaceVariant)
                    else -> Triple(Color.Transparent, colors.outline, colors.onSurface)
                }
                val state = when {
                    !due -> stringResource(R.string.habit_not_due)
                    outcome == Outcome.DONE -> stringResource(R.string.habit_status_done)
                    outcome == Outcome.PARTIAL -> stringResource(R.string.habit_legend_partial)
                    outcome == Outcome.SKIPPED -> stringResource(R.string.habit_legend_skipped)
                    outcome == Outcome.NOT_DONE -> stringResource(R.string.habit_status_not_done)
                    else -> stringResource(R.string.habit_status_not_logged)
                }
                val description = stringResource(R.string.habit_day_cell, DateFormats.longDate(day), state)
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(bg).border(1.5.dp, border, CircleShape)
                        .then(if (due) Modifier.clickable { onDay(day) } else Modifier)
                        .clearAndSetSemantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(DateFormats.dayNumber(day), style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    val colors = FitTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            colors.habits.fill to R.string.habit_legend_done,
            colors.habits.container to R.string.habit_legend_partial,
            colors.surfaceContainerHigh to R.string.habit_legend_skipped,
        ).forEach { (c, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = colors.subtle)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).border(1.5.dp, colors.outline, RoundedCornerShape(3.dp)))
            Text(stringResource(R.string.habit_legend_missed), style = MaterialTheme.typography.labelSmall, color = colors.subtle)
        }
    }
}
