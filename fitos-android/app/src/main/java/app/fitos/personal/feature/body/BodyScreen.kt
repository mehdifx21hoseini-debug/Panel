package app.fitos.personal.feature.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.BodyFigure
import app.fitos.core.designsystem.component.BodyShape
import app.fitos.core.designsystem.component.ChartPoint
import app.fitos.core.designsystem.component.EmptyState
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitSegmented
import app.fitos.core.designsystem.component.TrendChart
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.body.GoalProgress
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** "+۰٫۳" / "−۰٫۳": signed so a change reads at a glance. */
fun signed(value: Double): String = (if (value > 0.05) "+" else "") + Fmt.kg(value)

fun timeOf(entry: WeightEntry): String =
    PersianNumbers.digits(entry.measuredAt.atZone(entry.zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) })

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(
    viewModel: BodyViewModel,
    onLogMeasurements: () -> Unit,
    onOpenSite: (String) -> Unit,
    onNewGoal: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheetFor by remember { mutableStateOf<WeightEntry?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    val deletedText = stringResource(R.string.weight_deleted)
    val undoText = stringResource(R.string.action_undo)

    fun openSheet(entry: WeightEntry?) {
        sheetFor = entry
        sheetOpen = true
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.body_title), style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onSurface),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val today = viewModel.today()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = FitSpacing.gutter, end = FitSpacing.gutter, bottom = FitSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.gapSection),
        ) {
            if (state.entries.isEmpty()) {
                item {
                    FitCard {
                        EmptyState(FitEmoji.SCALE, stringResource(R.string.body_empty_title), stringResource(R.string.body_empty_body)) {
                            FitPrimaryButton(stringResource(R.string.body_log_weight), onClick = { openSheet(null) }, icon = FitIcons.Plus)
                        }
                    }
                }
            } else {
                item { SummaryCard(state, onLog = { openSheet(null) }) }
                item { ChartCard(state, today, onRange = viewModel::setRange) }
            }
            item { GoalCard(state, today, onNewGoal = onNewGoal, onExtend = viewModel::extendGoal) }
            if (state.weekly.size >= 2) item { WeeklyCard(state) }
            item { MeasurementsCard(state, onLog = onLogMeasurements, onOpenSite = onOpenSite) }
            if (state.entries.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = FitSpacing.s8).semantics { heading() },
                    )
                }
                items(state.entries.asReversed(), key = { it.id }) { entry -> HistoryRow(entry, onClick = { openSheet(entry) }) }
            }
        }
    }

    if (sheetOpen) {
        val editing = sheetFor
        WeightEntrySheet(
            editing = editing,
            lastWeightKg = state.entries.lastOrNull()?.weightKg,
            today = viewModel.today(),
            now = viewModel.now(),
            onDismiss = { sheetOpen = false },
            onSave = { input ->
                viewModel.saveWeight(editing, input)
                sheetOpen = false
            },
            onDelete = editing?.let { entry ->
                {
                    sheetOpen = false
                    viewModel.deleteWeight(entry.id)
                    scope.launch {
                        val result = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restoreWeight(entry.id)
                    }
                }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: BodyState, onLog: () -> Unit) {
    val colors = FitTheme.colors
    val latest = state.entries.last()
    val trend = state.trend.lastOrNull()?.trendKg
    FitCard(hero = true) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
                Text(stringResource(R.string.body_latest), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                Text("${DateFormats.longDate(latest.day)} · ${timeOf(latest)}", style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                Text(Fmt.kg(latest.weightKg), style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
                Text(stringResource(R.string.unit_kg), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
        }
        trend?.let { Text(stringResource(R.string.body_trend, Fmt.kg(it)), style = MaterialTheme.typography.bodyMedium, color = colors.primary) }
        HorizontalDivider(color = colors.outlineVariant)
        Text(stringResource(R.string.body_rate_label), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        val rate = state.rate
        if (rate == null) {
            Text(stringResource(R.string.body_rate_unknown), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        } else {
            Text(stringResource(R.string.body_rate, signed(rate)), style = numeralStyle(20), color = colors.onSurface)
        }
        FitPrimaryButton(stringResource(R.string.body_log_weight), onClick = onLog, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChartCard(state: BodyState, today: LocalDate, onRange: (ChartRange) -> Unit) {
    val colors = FitTheme.colors
    val from = state.range.days?.let { today.minusDays(it - 1) }
    val inRange = state.trend.filter { from == null || !it.day.isBefore(from) }
    FitCard {
        Text(stringResource(R.string.chart_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FitSegmented(ChartRange.entries, state.range, onRange, label = { stringResource(it.label) })
        if (inRange.size < 3) {
            FitNote(stringResource(R.string.chart_not_enough, Fmt.int(inRange.size)))
        } else {
            val tooltipFormat = stringResource(R.string.chart_tooltip) // template, filled per point
            TrendChart(
                points = inRange.map { ChartPoint(it.day.toEpochDay(), it.actualKg, it.trendKg) },
                description = stringResource(
                    R.string.chart_description,
                    DateFormats.dayMonth(inRange.first().day),
                    DateFormats.dayMonth(inRange.last().day),
                    Fmt.kg(inRange.last().trendKg),
                ),
                valueLabel = { Fmt.kg(it) },
                dateLabel = { DateFormats.dayMonth(LocalDate.ofEpochDay(it)) },
                tooltip = { p -> tooltipFormat.format(DateFormats.dayMonth(LocalDate.ofEpochDay(p.x)), Fmt.kg(p.value)) },
                goal = state.goal?.targetWeightKg,
                goalLabel = state.goal?.let { stringResource(R.string.chart_goal, Fmt.kg(it.targetWeightKg)) },
            )
            Text(stringResource(R.string.body_trend_note), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        }
    }
}

@Composable
private fun GoalCard(state: BodyState, today: LocalDate, onNewGoal: () -> Unit, onExtend: () -> Unit) {
    val colors = FitTheme.colors
    val goal = state.goal
    FitCard {
        Text(stringResource(R.string.goal_card_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (goal == null) {
            Text(stringResource(R.string.goal_card_none), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            return@FitCard
        }
        val current = state.trend.lastOrNull()?.trendKg ?: state.entries.lastOrNull()?.weightKg
        val start = goal.startWeightKg
        val losing = start == null || start > goal.targetWeightKg
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            JourneyStep(R.string.goal_start, start?.let(Fmt::kg), if (losing) BodyShape.START else null, colors.surfaceContainerHighest)
            JourneyStep(R.string.goal_now, current?.let(Fmt::kg), if (losing) BodyShape.NOW else null, colors.primary)
            JourneyStep(R.string.goal_target, Fmt.kg(goal.targetWeightKg), if (losing) BodyShape.GOAL else null, colors.outline, outlined = true)
        }
        val fraction = if (current != null) GoalProgress.fraction(start, goal.targetWeightKg, current) else null
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction.toFloat() },
                color = colors.primary,
                trackColor = colors.track,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.goal_progress, Fmt.int((fraction * 100).roundToInt())), style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        goal.reviewDate?.let {
            Text(stringResource(R.string.home_goal_review, DateFormats.longDate(it)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        }
        if (GoalProgress.isReviewDue(goal.reviewDate, today)) {
            FitNote(stringResource(R.string.goal_review_due))
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                FitPrimaryButton(stringResource(R.string.goal_new), onClick = onNewGoal, modifier = Modifier.weight(1f))
                FitSecondaryButton(stringResource(R.string.goal_extend), onClick = onExtend, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun JourneyStep(label: Int, value: String?, shape: BodyShape?, color: androidx.compose.ui.graphics.Color, outlined: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        if (shape != null) BodyFigure(shape, color, outlined = outlined)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = FitTheme.colors.onSurfaceVariant)
        Text(value ?: "—", style = numeralStyle(16), color = FitTheme.colors.onSurface)
    }
}

@Composable
private fun WeeklyCard(state: BodyState) {
    val colors = FitTheme.colors
    val weeks = state.weekly.takeLast(5)
    FitCard {
        Text(stringResource(R.string.weekly_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        weeks.indices.reversed().take(4).forEach { i ->
            val week = weeks[i]
            val change = if (i > 0) week.averageKg - weeks[i - 1].averageKg else null
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.weekly_row, DateFormats.dayMonth(week.weekStart)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.weekly_days, Fmt.int(week.days)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
                change?.let { Text(signed(it), style = numeralStyle(14), color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = FitSpacing.s12)) }
                Text(Fmt.kg(week.averageKg), style = numeralStyle(18), color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun MeasurementsCard(state: BodyState, onLog: () -> Unit, onOpenSite: (String) -> Unit) {
    val colors = FitTheme.colors
    val bySite = state.measurements.groupBy { it.siteId }
    val visible = state.sites.filter { !it.isHidden }
    val withData = visible.filter { bySite[it.id].orEmpty().isNotEmpty() }
    FitCard {
        Text(stringResource(R.string.measurements_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (withData.isEmpty()) {
            Text(stringResource(R.string.measurements_empty), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        withData.forEach { site ->
            val values = bySite.getValue(site.id)
            val latest = values.last()
            val first = values.first()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenSite(site.id) }
                    .heightIn(min = FitSpacing.target)
                    .padding(vertical = FitSpacing.s4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(site.name, style = MaterialTheme.typography.bodyLarge)
                    if (values.size >= 2) {
                        Text(
                            stringResource(R.string.measurement_change, signed(latest.valueCm - first.valueCm), DateFormats.dayMonth(first.day)),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.subtle,
                        )
                    }
                }
                Text(stringResource(R.string.value_cm, Fmt.kg(latest.valueCm)), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                Icon(FitIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = FitSpacing.s8))
            }
        }
        val empty = visible.size - withData.size
        if (withData.isNotEmpty() && empty > 0) {
            Text(stringResource(R.string.measurement_others, Fmt.int(empty)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        }
        FitSecondaryButton(stringResource(R.string.measurements_log), onClick = onLog, icon = FitIcons.Ruler, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HistoryRow(entry: WeightEntry, onClick: () -> Unit) {
    val colors = FitTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = FitSpacing.s4, vertical = FitSpacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
            Text(
                "${JalaliCalendar.weekdayName(entry.day.dayOfWeek)} ${DateFormats.dayMonth(entry.day)} · ${timeOf(entry)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val extra = listOfNotNull(entry.condition?.let { stringResource(it.label) }, entry.note).joinToString(" · ")
            if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.bodySmall, color = colors.subtle, maxLines = 2)
        }
        Text(Fmt.kg(entry.weightKg), style = numeralStyle(18), color = colors.onSurface)
    }
}
