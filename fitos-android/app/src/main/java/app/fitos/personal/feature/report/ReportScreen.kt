package app.fitos.personal.feature.report

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.EmojiTile
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.report.AdaptiveResult
import app.fitos.core.domain.report.AdaptiveTargets
import app.fitos.core.domain.report.Advice
import app.fitos.core.domain.report.ChangeReason
import app.fitos.core.domain.report.Missing
import app.fitos.core.domain.report.ProgressMetric
import app.fitos.core.domain.report.WeeklyReport
import app.fitos.core.domain.report.WeeklyReportRules
import app.fitos.core.domain.report.WeeklyReportSource
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.personal.R
import app.fitos.personal.feature.habits.FitSheet
import app.fitos.personal.feature.habits.percentText
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import java.time.Clock
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportUi(
    val week: LocalDate? = null,
    val report: WeeklyReport? = null,
    /** Weeks with any log, newest first. */
    val weeks: List<LocalDate> = emptyList(),
    val latest: LocalDate? = null,
    val today: LocalDate? = null,
    val dismissed: Boolean = false,
    val applied: Boolean = false,
    val target: NutritionTarget? = null,
    val profile: Profile? = null,
)

/** One week's report at a time; built on open, from the data as it is now. */
class ReportViewModel(
    private val source: WeeklyReportSource,
    private val targets: NutritionTargetRepository,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    start: LocalDate?,
) : ViewModel() {
    private val _ui = MutableStateFlow(ReportUi())
    val ui: StateFlow<ReportUi> = _ui.asStateFlow()
    private var loading: Job? = null

    init {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val latest = WeeklyReportRules.latestWeek(today)
            _ui.update { it.copy(weeks = source.weeks(today), latest = latest, today = today) }
            show(start ?: latest)
        }
    }

    fun show(week: LocalDate) {
        loading?.cancel()
        _ui.update { it.copy(week = week) }
        loading = viewModelScope.launch {
            val today = LocalDate.now(clock)
            val report = source.report(week, today)
            val latest = WeeklyReportRules.latestWeek(today)
            if (week == latest) settings.setReportSeenWeek(week)
            _ui.update {
                it.copy(
                    report = report,
                    dismissed = settings.adaptiveDismissedWeek.first() == week,
                    target = targets.effectiveOn(today).first(),
                    profile = profiles.profile.first(),
                )
            }
        }
    }

    fun apply(change: AdaptiveResult.Change) {
        val s = _ui.value
        val target = s.target ?: return
        val profile = s.profile ?: return
        val today = s.today ?: return
        viewModelScope.launch {
            targets.save(AdaptiveTargets.apply(target, change, profile, today))
            _ui.update { it.copy(applied = true) }
            s.week?.let(::show)
        }
    }

    fun dismiss() {
        val week = _ui.value.week ?: return
        viewModelScope.launch {
            settings.setAdaptiveDismissedWeek(week)
            _ui.update { it.copy(dismissed = true) }
        }
    }
}

@Composable
internal fun weekLabel(start: LocalDate): String = stringResource(R.string.report_week, DateFormats.dayMonth(start), DateFormats.dayMonth(start.plusDays(6)))

@Composable
private fun rateText(kgPerWeek: Double): String = when {
    kgPerWeek <= -0.05 -> stringResource(R.string.rate_down, Fmt.kg(-kgPerWeek))
    kgPerWeek >= 0.05 -> stringResource(R.string.rate_up, Fmt.kg(kgPerWeek))
    else -> stringResource(R.string.rate_flat)
}

private fun kcal(value: Double): String = Fmt.int(value.roundToInt())

@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf<AdaptiveResult.Change?>(null) }
    val colors = FitTheme.colors
    EditScaffold(title = stringResource(R.string.report_title), onBack = onBack) {
        val week = ui.week ?: return@EditScaffold
        val today = ui.today ?: return@EditScaffold
        val oldest = ui.weeks.lastOrNull()
        val current = JalaliCalendar.startOfWeek(today)
        WeekHeader(
            week,
            onOlder = { viewModel.show(week.minusWeeks(1)) }.takeIf { oldest != null && week > oldest },
            onNewer = { viewModel.show(week.plusWeeks(1)) }.takeIf { week < current },
            onPick = { picking = true },
        )
        val report = ui.report?.takeIf { it.weekStart == week } ?: return@EditScaffold
        Text(
            stringResource(if (report.inProgress) R.string.report_in_progress else R.string.report_complete),
            style = MaterialTheme.typography.bodySmall,
            color = colors.subtle,
        )
        if (!report.hasData) {
            FitNote(stringResource(R.string.report_empty))
            return@EditScaffold
        }
        if (!report.inProgress) {
            HighlightCard(report)
            AdviceCard(report, ui, onApply = { applying = it }, onDismiss = viewModel::dismiss)
        }
        WeightCard(report)
        TrainingCard(report)
        FoodCard(report)
        HabitsSleepCard(report)
        report.adaptive?.let { CheckCard(it) }
        Text(stringResource(R.string.report_live_note), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        Text(stringResource(R.string.report_reminder_note), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
    if (picking) {
        WeekPicker(ui.weeks, ui.week, ui.today?.let(JalaliCalendar::startOfWeek), onPick = { viewModel.show(it); picking = false }, onDismiss = { picking = false })
    }
    applying?.let { change ->
        ApplySheet(change, ui, onConfirm = { viewModel.apply(change); applying = null }, onDismiss = { applying = null })
    }
}

@Composable
private fun WeekHeader(week: LocalDate, onOlder: (() -> Unit)?, onNewer: (() -> Unit)?, onPick: () -> Unit) {
    val colors = FitTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onOlder?.invoke() }, enabled = onOlder != null) {
            Icon(FitIcons.ChevronLeft, contentDescription = stringResource(R.string.report_older))
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = stringResource(R.string.report_pick_week), role = Role.Button, onClick = onPick)
                .padding(vertical = FitSpacing.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(weekLabel(week), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_pick_week), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Icon(FitIcons.ChevronDown, contentDescription = null, tint = colors.primary)
            }
        }
        IconButton(onClick = { onNewer?.invoke() }, enabled = onNewer != null) {
            Icon(FitIcons.ChevronRight, contentDescription = stringResource(R.string.report_newer))
        }
    }
}

@Composable
private fun WeekPicker(weeks: List<LocalDate>, selected: LocalDate?, current: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = FitTheme.colors
    FitSheet(stringResource(R.string.report_weeks_title), onDismiss) {
        weeks.forEach { w ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (w == selected) colors.primaryContainer else colors.surfaceContainer)
                    .clickable(role = Role.Button) { onPick(w) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = FitSpacing.s16, vertical = FitSpacing.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(weekLabel(w), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (w == current) Text(stringResource(R.string.report_week_current), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Line(text: String, strong: Boolean = false) {
    Text(
        text,
        style = if (strong) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
        color = if (strong) FitTheme.colors.onSurface else FitTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun HighlightCard(report: WeeklyReport) {
    val colors = FitTheme.colors
    FitCard(hero = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12), verticalAlignment = Alignment.CenterVertically) {
            EmojiTile(if (report.highlight != null) FitEmoji.TROPHY else FitEmoji.BARCHART, tint = colors.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.report_highlight_title), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                Text(
                    report.highlight?.let { highlightText(it) } ?: stringResource(R.string.report_highlight_none),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}

@Composable
private fun AdviceCard(report: WeeklyReport, ui: ReportUi, onApply: (AdaptiveResult.Change) -> Unit, onDismiss: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        SectionTitle(stringResource(R.string.report_advice_title))
        if (ui.applied) FitNote(stringResource(R.string.advice_applied))
        if (report.advice.isEmpty()) Line(stringResource(R.string.report_advice_none))
        report.advice.forEach { advice ->
            val (emoji, title, body) = adviceText(advice)
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                EmojiTile(emoji, size = 36.dp, tint = colors.surfaceContainer)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    val change = (advice as? Advice.Calories)?.result as? AdaptiveResult.Change
                    if (change != null) {
                        if (ui.dismissed) {
                            Text(stringResource(R.string.advice_dismissed), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                        } else {
                            FitPrimaryButton(stringResource(R.string.advice_apply), onClick = { onApply(change) }, modifier = Modifier.fillMaxWidth())
                            FitTextButton(stringResource(R.string.advice_not_now), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun adviceText(advice: Advice): Triple<FitEmoji, String, String> = when (advice) {
    is Advice.LogFood -> Triple(FitEmoji.PLATE, stringResource(R.string.advice_log_food), stringResource(R.string.advice_log_food_body, Fmt.int(advice.loggedDays), Fmt.int(advice.days)))
    is Advice.WeighMore -> Triple(FitEmoji.SCALE, stringResource(R.string.advice_weigh), stringResource(R.string.advice_weigh_body, Fmt.int(advice.weighDays)))
    is Advice.Training -> Triple(FitEmoji.HOUSE, stringResource(R.string.advice_training), stringResource(R.string.advice_training_body, Fmt.int(advice.done), Fmt.int(advice.planned)))
    is Advice.Protein -> Triple(FitEmoji.POULTRY, stringResource(R.string.advice_protein), stringResource(R.string.advice_protein_body, kcal(advice.meanG), kcal(advice.targetG)))
    is Advice.Sleep -> Triple(
        FitEmoji.SLEEPING, stringResource(R.string.advice_sleep),
        stringResource(R.string.advice_sleep_body, Fmt.hours(advice.meanMinutes.roundToInt()), Fmt.hours(advice.targetMinutes)),
    )
    is Advice.Habit -> Triple(FitEmoji.CHECK, stringResource(R.string.advice_habit, advice.name), stringResource(R.string.advice_habit_body, percentText(advice.score)))
    is Advice.CaloriesOver -> Triple(FitEmoji.FIRE, stringResource(R.string.advice_kcal_over), stringResource(R.string.advice_kcal_over_body, kcal(advice.meanKcal), kcal(advice.targetKcal)))
    is Advice.Calories -> when (val r = advice.result) {
        is AdaptiveResult.Change -> Triple(
            FitEmoji.BULLSEYE,
            stringResource(R.string.advice_kcal_change, Fmt.int(r.newKcal), Fmt.int(r.basis.currentKcal)),
            when (r.reason) {
                ChangeReason.LOSING_TOO_FAST -> stringResource(R.string.advice_kcal_too_fast, rateText(r.basis.rateKgPerWeek))
                ChangeReason.TOO_LOW -> stringResource(R.string.advice_kcal_too_low, kcal(r.basis.tdeeKcal))
                ChangeReason.FASTER_THAN_PACE -> stringResource(R.string.advice_kcal_up, kcal(r.basis.tdeeKcal))
                ChangeReason.SLOWER_THAN_PACE -> stringResource(R.string.advice_kcal_down, kcal(r.basis.tdeeKcal))
            },
        )
        is AdaptiveResult.EatUpToTarget -> Triple(
            FitEmoji.BULLSEYE, stringResource(R.string.advice_eat_up),
            stringResource(R.string.advice_eat_up_body, kcal(r.basis.meanIntakeKcal), Fmt.int(r.basis.currentKcal)),
        )
        else -> Triple(FitEmoji.BULLSEYE, "", "")
    }
}

@Composable
private fun WeightCard(report: WeeklyReport) {
    FitCard {
        SectionTitle(stringResource(R.string.report_weight_title))
        val w = report.weight
        if (w.days == 0) {
            Line(stringResource(R.string.report_weight_none))
        } else {
            Line(stringResource(R.string.report_weight_avg, Fmt.kg(w.averageKg!!), Fmt.int(w.days)), strong = true)
            val change = w.change
            Line(
                when {
                    change == null -> stringResource(R.string.report_weight_no_change, Fmt.int(w.previousDays), Fmt.int(w.days))
                    change <= -0.05 -> stringResource(R.string.report_weight_down, Fmt.kg(-change))
                    change >= 0.05 -> stringResource(R.string.report_weight_up, Fmt.kg(change))
                    else -> stringResource(R.string.report_weight_same)
                },
            )
        }
        report.waist?.let { waist ->
            val latest = waist.latest
            val before = waist.before
            if (latest == null) {
                before?.let { Line(stringResource(R.string.report_waist_none, waist.siteName, Fmt.kg(it.second), DateFormats.dayMonth(it.first))) }
            } else {
                Line(stringResource(R.string.report_waist, waist.siteName, Fmt.kg(latest.second), DateFormats.dayMonth(latest.first)), strong = true)
                val c = waist.change
                Line(
                    when {
                        c == null || before == null -> stringResource(R.string.report_waist_first)
                        c <= -0.05 -> stringResource(R.string.report_waist_down, Fmt.kg(-c), DateFormats.dayMonth(before.first))
                        c >= 0.05 -> stringResource(R.string.report_waist_up, Fmt.kg(c), DateFormats.dayMonth(before.first))
                        else -> stringResource(R.string.report_waist_same, DateFormats.dayMonth(before.first))
                    },
                )
            }
        }
    }
}

@Composable
private fun TrainingCard(report: WeeklyReport) {
    val t = report.training
    FitCard {
        SectionTitle(stringResource(R.string.report_training_title))
        if (t.sessions == 0) {
            Line(stringResource(R.string.report_training_none))
            t.planned?.let { Line(stringResource(R.string.report_sessions_planned, Fmt.int(0), Fmt.int(it))) }
            return@FitCard
        }
        Line(
            t.planned?.let { stringResource(R.string.report_sessions_planned, Fmt.int(t.sessions), Fmt.int(it)) }
                ?: stringResource(R.string.report_sessions, Fmt.int(t.sessions)),
            strong = true,
        )
        Line(stringResource(R.string.report_training_volume, Fmt.int(t.minutes), Fmt.int(t.tonnageKg.roundToInt())))
        if (t.shortVersions > 0) Line(stringResource(R.string.report_short_versions, Fmt.int(t.shortVersions)))
        if (t.compared.isEmpty()) {
            if (t.exercises.isNotEmpty()) Line(stringResource(R.string.report_progress_first))
        } else {
            Line(stringResource(R.string.report_progress, Fmt.int(t.improved.size), Fmt.int(t.compared.size)), strong = true)
            t.improved.take(5).forEach { e ->
                val before = e.before!!
                Row {
                    Text(e.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        when (e.metric) {
                            ProgressMetric.E1RM, ProgressMetric.WEIGHT -> stringResource(R.string.report_progress_kg, Fmt.kg(before), Fmt.kg(e.best))
                            ProgressMetric.REPS -> stringResource(R.string.report_progress_reps, Fmt.int(before.roundToInt()), Fmt.int(e.best.roundToInt()))
                            ProgressMetric.SECONDS -> stringResource(R.string.report_progress_seconds, Fmt.int(before.roundToInt()), Fmt.int(e.best.roundToInt()))
                            ProgressMetric.METERS -> stringResource(R.string.report_progress_meters, Fmt.int(before.roundToInt()), Fmt.int(e.best.roundToInt()))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitTheme.colors.primary,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(stringResource(R.string.report_progress_note), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
        }
        val volume = t.volume
        if (volume.isNotEmpty()) {
            Line(stringResource(R.string.report_volume_title), strong = true)
            volume.forEach { (muscle, done, planned) ->
                Row {
                    Text(muscle.nameFa, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        planned?.let { stringResource(R.string.report_volume_row, Fmt.kg(done), Fmt.kg(it)) } ?: stringResource(R.string.report_volume_done, Fmt.kg(done)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (planned != null && done < planned) FitTheme.colors.onSurfaceVariant else FitTheme.colors.primary,
                    )
                }
            }
            Text(stringResource(R.string.report_volume_note), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
        }
    }
}

@Composable
private fun FoodCard(report: WeeklyReport) {
    val n = report.nutrition
    FitCard {
        SectionTitle(stringResource(R.string.report_food_title))
        if (n.loggedDays == 0) {
            Line(stringResource(R.string.report_food_none))
            return@FitCard
        }
        Line(stringResource(R.string.report_food_days, Fmt.int(n.loggedDays), Fmt.int(n.days)), strong = true)
        Line(
            n.meanKcalTarget?.let { stringResource(R.string.report_food_kcal, kcal(n.meanKcal!!), kcal(it)) }
                ?: stringResource(R.string.report_food_kcal_only, kcal(n.meanKcal!!)),
        )
        Line(
            n.meanProteinTargetG?.let { stringResource(R.string.report_food_protein, kcal(n.meanProteinG!!), kcal(it)) }
                ?: stringResource(R.string.report_food_protein_only, kcal(n.meanProteinG!!)),
        )
        if (n.meanKcalTarget != null) Line(stringResource(R.string.report_food_days_met, Fmt.int(n.daysInBand), Fmt.int(n.daysProteinMet)))
        Text(stringResource(R.string.report_food_note), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
    }
}

@Composable
private fun HabitsSleepCard(report: WeeklyReport) {
    val h = report.habits
    val s = report.sleep
    FitCard {
        SectionTitle(stringResource(R.string.report_habits_title))
        if (h.score == null) {
            Line(stringResource(R.string.report_habits_none))
        } else {
            Line(stringResource(R.string.report_habits, percentText(h.score!!), Fmt.int(h.habits)), strong = true)
            h.weakest?.takeIf { h.habits > 1 }?.let { Line(stringResource(R.string.report_habits_weakest, it.name, percentText(it.score))) }
        }
        if (s.nights == 0 || s.meanMinutes == null) {
            Line(stringResource(R.string.report_sleep_none))
        } else {
            val mean = Fmt.hours(s.meanMinutes!!.roundToInt())
            Line(
                s.targetMinutes?.let { stringResource(R.string.report_sleep_target, mean, Fmt.int(s.nights), Fmt.hours(it)) }
                    ?: stringResource(R.string.report_sleep, mean, Fmt.int(s.nights)),
                strong = true,
            )
        }
    }
}

@Composable
private fun CheckCard(result: AdaptiveResult) {
    val colors = FitTheme.colors
    FitCard {
        SectionTitle(stringResource(R.string.check_title))
        when (result) {
            is AdaptiveResult.NotReady -> {
                Line(stringResource(R.string.check_not_ready), strong = true)
                result.missing.forEach { m ->
                    Line(
                        "• " + when (m) {
                            is Missing.WeighIns -> stringResource(R.string.check_need_weigh, m.perWeek.joinToString("، ") { Fmt.int(it) })
                            is Missing.FoodDays -> stringResource(R.string.check_need_food, Fmt.int(m.logged), Fmt.int(m.needed))
                            is Missing.RecentChange -> stringResource(R.string.check_need_time, Fmt.int(m.days))
                            Missing.NoTarget -> stringResource(R.string.check_need_target)
                            Missing.NoProfile -> stringResource(R.string.check_need_profile)
                        },
                    )
                }
            }
            is AdaptiveResult.NotPlausible -> Line(stringResource(R.string.check_implausible, kcal(result.basis.tdeeKcal), kcal(result.basis.bmrKcal)), strong = true)
            is AdaptiveResult.Keep -> Line(stringResource(R.string.check_keep), strong = true)
            is AdaptiveResult.EatUpToTarget -> Line(stringResource(R.string.check_eat_up), strong = true)
            is AdaptiveResult.Change -> Line(stringResource(R.string.check_change, Fmt.int(result.newKcal), Fmt.int(result.basis.currentKcal)), strong = true)
        }
        val basis = when (result) {
            is AdaptiveResult.NotPlausible -> result.basis
            is AdaptiveResult.Keep -> result.basis
            is AdaptiveResult.EatUpToTarget -> result.basis
            is AdaptiveResult.Change -> result.basis
            is AdaptiveResult.NotReady -> null
        }
        basis?.let { b ->
            Line(stringResource(R.string.check_window, DateFormats.dayMonth(b.from), DateFormats.dayMonth(b.to)))
            Line(stringResource(R.string.check_intake, Fmt.int(b.foodDays), kcal(b.meanIntakeKcal)))
            Line(stringResource(R.string.check_rate, Fmt.int(b.weighDays), rateText(b.rateKgPerWeek)))
            Line(stringResource(R.string.check_tdee, kcal(b.tdeeKcal)))
            Line(stringResource(R.string.check_floor, kcal(b.bmrKcal), Fmt.int(b.lowestKcal)))
            Line(stringResource(R.string.check_targets, Fmt.int(b.currentKcal), Fmt.int(b.idealKcal)))
        }
        Text(stringResource(R.string.check_note), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
}

@Composable
private fun ApplySheet(change: AdaptiveResult.Change, ui: ReportUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val target = ui.target ?: return
    val profile = ui.profile ?: return
    val today = ui.today ?: return
    val next = remember(change, target) { AdaptiveTargets.apply(target, change, profile, today) }
    FitSheet(stringResource(R.string.apply_title), onDismiss) {
        Line(stringResource(R.string.apply_kcal, Fmt.int(target.kcal), Fmt.int(next.kcal)), strong = true)
        if (abs(next.carbsG - target.carbsG) > 0) Line(stringResource(R.string.apply_carbs, Fmt.int(target.carbsG), Fmt.int(next.carbsG)))
        Line(stringResource(R.string.apply_same))
        Text(stringResource(R.string.apply_from), style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
        FitPrimaryButton(stringResource(R.string.apply_confirm), onClick = onConfirm, modifier = Modifier.fillMaxWidth())
        FitSecondaryButton(stringResource(R.string.apply_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
