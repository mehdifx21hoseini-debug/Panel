package app.fitos.personal.feature.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.MuscleMap
import app.fitos.core.designsystem.component.bestSide
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.training.ExercisePerformance
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Progression
import app.fitos.core.domain.training.SessionExercise
import app.fitos.core.domain.training.SessionFlow
import app.fitos.core.domain.training.SetLog
import app.fitos.core.domain.training.SetType
import app.fitos.core.domain.training.Suggestion
import app.fitos.core.domain.training.SuggestionReason
import app.fitos.core.domain.training.WorkoutSession
import app.fitos.personal.R
import app.fitos.personal.feature.home.easyText
import app.fitos.personal.feature.training.label
import app.fitos.personal.feature.training.muscleNames
import app.fitos.personal.ui.Fmt
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/** "۴۵:۰۷" or "۱:۰۲:۰۵". */
fun clockText(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    return PersianNumbers.digits(text)
}

@Composable
fun daysAgo(day: LocalDate, today: LocalDate): String = when (val d = ChronoUnit.DAYS.between(day, today)) {
    0L -> stringResource(R.string.days_ago_today)
    1L -> stringResource(R.string.days_ago_yesterday)
    else -> stringResource(R.string.days_ago, Fmt.int(d.toInt()))
}

/** The current time, refreshed every second while the screen is shown. */
@Composable
private fun ticking(clock: Clock): Instant {
    val now by produceState(clock.instant()) {
        while (true) {
            delay(1_000)
            value = clock.instant()
        }
    }
    return now
}

/**
 * The focused workout screen: one exercise at a time, values filled in from the plan, the
 * suggestion and last time, one tap to save a set, then the rest timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    clock: Clock,
    onClose: () -> Unit,
    onFinished: (String) -> Unit,
    onReplace: (sessionExerciseId: String) -> Unit,
    onAdd: (sessionId: String) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val session = ui.session
    // Set when this screen itself finishes or discards, so the session vanishing does not close twice.
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(ui.loaded, session == null) { if (ui.loaded && session == null && !leaving) onClose() }
    if (session == null) return
    val now = ticking(clock)
    val colors = FitTheme.colors
    var finishing by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<SessionExercise, SetLog>?>(null) }
    val item = ui.current
    val allDone = SessionFlow.firstOpen(session) == null && session.exercises.isNotEmpty()

    val loaded = item != null && ui.last.containsKey(item.id)
    val form = remember(item?.id, item?.exercise?.id, item?.sets?.size, loaded) {
        SetForm(item?.let { SessionViewModel.prefill(it, ui.suggestions[it.id], ui.last[it.id]) } ?: app.fitos.core.domain.training.SetInput())
    }
    var details by rememberSaveable(item?.id) { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            listOfNotNull(session.dayName ?: stringResource(R.string.session_title), session.variantKind?.let { stringResource(it.label) }).joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                        Text(clockText(session.activeMillis(now) / 1000), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(FitIcons.Back, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    if (session.isPaused) {
                        IconButton(onClick = { viewModel.resume() }) { Icon(FitIcons.Play, contentDescription = stringResource(R.string.session_resume)) }
                    } else {
                        IconButton(onClick = { viewModel.pause() }) { Icon(FitIcons.Pause, contentDescription = stringResource(R.string.session_pause)) }
                    }
                    FitTextButton(stringResource(R.string.session_finish), onClick = { finishing = true })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background, titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface, actionIconContentColor = colors.onSurface,
                ),
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(colors.background).navigationBarsPadding().padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s12),
                verticalArrangement = Arrangement.spacedBy(FitSpacing.s8),
            ) {
                if (item != null) {
                    val input = form.toInput(item.exercise.logType)
                    val label = when {
                        form.warmup -> R.string.session_log_warmup
                        item.working.size >= item.targets.sets -> R.string.session_extra_set
                        else -> R.string.session_log_set
                    }
                    val log = { input?.let { viewModel.logSet(item, it) }; Unit }
                    if (allDone) {
                        FitPrimaryButton(stringResource(R.string.finish_title), onClick = { finishing = true }, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
                        FitSecondaryButton(stringResource(label), onClick = log, enabled = input != null, modifier = Modifier.fillMaxWidth())
                    } else {
                        FitPrimaryButton(stringResource(label), onClick = log, enabled = input != null, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            if (session.isPaused) {
                FitNote(stringResource(R.string.session_paused), warning = true)
                FitSecondaryButton(stringResource(R.string.session_resume), onClick = { viewModel.resume() }, icon = FitIcons.Play, modifier = Modifier.fillMaxWidth())
            }
            ui.easy?.let { FitNote(easyText(it)) }
            val restEnd = ui.restEndsAt
            if (restEnd != null && restEnd.isAfter(now) && !session.isPaused) {
                RestCard(Duration.between(now, restEnd).seconds + 1, item?.exercise?.nameFa.orEmpty(), viewModel::adjustRest, viewModel::skipRest)
            }
            if (allDone) FitNote(stringResource(R.string.session_all_done))
            if (item == null) {
                FitSecondaryButton(stringResource(R.string.session_add_exercise), onClick = { onAdd(session.id) }, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
                return@Column
            }
            ExerciseHeader(session, ui.selected, item)
            if (loaded) SuggestionCard(item, ui.suggestions[item.id], ui.last[item.id], session.day) { form.weight = Fmt.field(it, 2) }

            // The set being entered comes first; the table of today's sets follows.
            FitCard {
                val number = item.working.size + 1
                Text(
                    if (number <= item.targets.sets) stringResource(R.string.session_set_of, Fmt.int(number), Fmt.int(item.targets.sets)) else stringResource(R.string.session_extra_set),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                SetMainFields(form, item.exercise.logType, Progression.step(item.exercise, item.progression))
                FitTextButton(stringResource(if (details) R.string.session_details_hide else R.string.session_details), onClick = { details = !details })
                if (details) SetDetailFields(form, item.exercise.logType)
            }
            TodaySets(item, ui.last[item.id]) { set -> editing = item to set }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.select(ui.selected - 1) }, enabled = ui.selected > 0) {
                    Icon(FitIcons.ChevronLeft, contentDescription = stringResource(R.string.session_previous))
                }
                FitTextButton(stringResource(R.string.session_all_exercises), onClick = { overview = true }, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.select(ui.selected + 1) }, enabled = ui.selected < session.exercises.lastIndex) {
                    Icon(FitIcons.ChevronRight, contentDescription = stringResource(R.string.session_next))
                }
            }
            NotificationPermissionNote()
            Spacer(Modifier.size(FitSpacing.s8))
        }
    }

    if (overview) {
        OverviewSheet(
            session, ui.selected,
            onDismiss = { overview = false },
            onSelect = { viewModel.select(it); overview = false },
            onSkip = { e, skip -> viewModel.setSkipped(e, skip) },
            onReplace = { overview = false; onReplace(it.id) },
            onAdd = { overview = false; onAdd(session.id) },
            onDiscard = { overview = false; discarding = true },
        )
    }
    editing?.let { (e, set) ->
        SetEditSheet(
            set, e.sets.indexOf(set) + 1, e.exercise.logType, Progression.step(e.exercise, e.progression),
            onDismiss = { editing = null },
            onSave = { viewModel.updateSet(set.id, it); editing = null },
            onDelete = { viewModel.deleteSet(set.id); editing = null },
        )
    }
    if (finishing) {
        FinishDialog(
            session, now,
            onDismiss = { finishing = false },
            onFinish = { effort, note ->
                finishing = false
                leaving = true
                viewModel.finish(effort, note, onFinished)
            },
            onDiscard = { finishing = false; discarding = true },
        )
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body, Fmt.int(session.exercises.sumOf { it.sets.size }))) },
            confirmButton = {
                FitTextButton(stringResource(R.string.discard), onClick = {
                    discarding = false
                    leaving = true
                    viewModel.discard(onClose)
                })
            },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { discarding = false }) },
        )
    }
}

@Composable
private fun NotificationPermissionNote() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AndroidRestAlarm.canNotify(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) return
    FitNote(stringResource(R.string.notify_permission_hint))
    FitTextButton(stringResource(R.string.notify_permission_allow), onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) })
}

@Composable
private fun RestCard(secondsLeft: Long, next: String, onAdjust: (Long) -> Unit, onSkip: () -> Unit) {
    val colors = FitTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.primaryContainer).padding(FitSpacing.s16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s4),
    ) {
        Text(stringResource(R.string.rest_title), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
        Text(
            clockText(secondsLeft),
            style = numeralStyle(48),
            color = colors.onPrimaryContainer,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (next.isNotEmpty()) Text(stringResource(R.string.rest_next, next), style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAdjust(-15) }) { Icon(FitIcons.Minus, contentDescription = stringResource(R.string.rest_less), tint = colors.onPrimaryContainer) }
            FitTextButton(stringResource(R.string.rest_skip), onClick = onSkip)
            IconButton(onClick = { onAdjust(15) }) { Icon(FitIcons.Plus, contentDescription = stringResource(R.string.rest_more), tint = colors.onPrimaryContainer) }
        }
    }
}

@Composable
private fun ExerciseHeader(session: WorkoutSession, index: Int, item: SessionExercise) {
    val colors = FitTheme.colors
    var guide by rememberSaveable(item.exercise.id) { mutableStateOf(false) }
    val e = item.exercise
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                Text(
                    stringResource(R.string.session_exercise_of, Fmt.int(index + 1), Fmt.int(session.exercises.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.subtle,
                )
                Text(e.nameFa, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(muscleNames(e.primary), style = MaterialTheme.typography.bodyMedium, color = colors.training.ink)
                val partners = SessionFlow.supersetAround(session, index).filter { it != index }.map { session.exercises[it].exercise.nameFa }
                if (partners.isNotEmpty()) {
                    Text(stringResource(R.string.session_superset, partners.joinToString("، ")), style = MaterialTheme.typography.labelMedium, color = colors.training.ink)
                }
            }
            MuscleMap(bestSide(e.primary, e.secondary), e.primary, e.secondary, width = 44.dp)
        }
        item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        e.instructions?.let { text ->
            FitTextButton(stringResource(R.string.session_guide), onClick = { guide = !guide })
            if (guide) Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuggestionCard(item: SessionExercise, s: Suggestion?, last: ExercisePerformance?, today: LocalDate, onUseWeight: (Double) -> Unit) {
    val colors = FitTheme.colors
    val type = item.exercise.logType
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(FitSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(FitSpacing.s4),
    ) {
        if (s != null && (type == LogType.WEIGHT_REPS || type == LogType.REPS)) {
            val value = when {
                s.weightKg != null && s.reps != null -> "${Fmt.kg(s.weightKg!!)} × ${Fmt.int(s.reps!!)}"
                s.reps != null -> stringResource(R.string.reps_value, Fmt.int(s.reps!!))
                else -> null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.suggest_title), style = MaterialTheme.typography.labelLarge, color = colors.subtle, modifier = Modifier.weight(1f))
                value?.let { Text(it, style = numeralStyle(20), color = colors.onSurface) }
            }
            Text(reasonText(s, type), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            val previous = s.previousWeightKg
            if (previous != null && s.weightKg != null && s.weightKg != previous && item.working.isEmpty()) {
                FitTextButton(stringResource(R.string.suggest_reject, Fmt.kg(previous)), onClick = { onUseWeight(previous) })
            }
        }
        if (last != null) {
            Text(
                stringResource(R.string.session_last_time, daysAgo(last.day, today)) + ": " + last.working.map { setText(type, it) }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtle,
            )
        } else {
            Text(stringResource(R.string.session_first_time), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        }
    }
}

@Composable
private fun reasonText(s: Suggestion, type: LogType): String {
    val reps = s.reps?.let(Fmt::int).orEmpty()
    return when (s.reason) {
        SuggestionReason.NO_HISTORY -> stringResource(if (s.weightKg != null) R.string.reason_no_history else R.string.reason_no_history_empty)
        SuggestionReason.TOP_OF_RANGE -> stringResource(if (type == LogType.WEIGHT_REPS) R.string.reason_top else R.string.reason_reps_top)
        SuggestionReason.IN_RANGE -> stringResource(if (type == LogType.WEIGHT_REPS) R.string.reason_in_range else R.string.reason_reps_range, reps)
        SuggestionReason.BELOW_RANGE -> stringResource(R.string.reason_below)
        SuggestionReason.QUALITY -> stringResource(R.string.reason_quality)
        SuggestionReason.RIR -> stringResource(R.string.reason_rir)
        SuggestionReason.PAIN -> stringResource(R.string.reason_pain)
        SuggestionReason.BREAK -> stringResource(R.string.reason_break, Fmt.int((s.daysSince ?: 0).toInt()))
        SuggestionReason.NO_RULE -> stringResource(R.string.reason_no_rule)
        SuggestionReason.EASY_RETURN -> stringResource(R.string.reason_easy_return)
    }
}

/** Planned sets with last time's numbers beside them; saved sets can be tapped to fix. */
@Composable
private fun TodaySets(item: SessionExercise, last: ExercisePerformance?, onEdit: (SetLog) -> Unit) {
    val colors = FitTheme.colors
    val type = item.exercise.logType
    val lastSets = last?.working.orEmpty()
    val rows = maxOf(item.targets.sets, item.working.size)
    FitCard {
        Text(stringResource(R.string.session_today_sets), style = MaterialTheme.typography.titleSmall)
        item.sets.filter { it.type == SetType.WARMUP }.forEach { set ->
            SetRow(stringResource(R.string.set_warmup_label), setText(type, set), null, done = true, onClick = { onEdit(set) })
        }
        (0 until rows).forEach { i ->
            if (i > 0 || item.sets.any { it.type == SetType.WARMUP }) HorizontalDivider(color = colors.outlineVariant)
            val set = item.working.getOrNull(i)
            SetRow(
                label = Fmt.int(i + 1),
                value = set?.let { setText(type, it) + flags(it) } ?: "—",
                previous = lastSets.getOrNull(i)?.let { setText(type, it) },
                done = set != null,
                onClick = set?.let { { onEdit(it) } },
            )
        }
        if (item.skipped) Text(stringResource(R.string.session_skipped), style = MaterialTheme.typography.labelMedium, color = colors.subtle)
    }
}

@Composable
private fun flags(set: SetLog): String {
    val parts = listOfNotNull(
        set.rir?.let { "RIR ${Fmt.int(it)}" },
        set.quality?.let { stringResource(it.label) },
        if (set.pain) stringResource(R.string.set_pain_label) else null,
    )
    return if (parts.isEmpty()) "" else "  (" + parts.joinToString("، ") + ")"
}

@Composable
private fun SetRow(label: String, value: String, previous: String?, done: Boolean, onClick: (() -> Unit)?) {
    val colors = FitTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 44.dp).padding(vertical = FitSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.subtle, modifier = Modifier.width(56.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (done) colors.onSurface else colors.subtle, modifier = Modifier.weight(1f))
        previous?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.subtle) }
        if (done) Icon(FitIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewSheet(
    session: WorkoutSession,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onSkip: (SessionExercise, Boolean) -> Unit,
    onReplace: (SessionExercise) -> Unit,
    onAdd: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = FitTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s8),
        ) {
            Text(stringResource(R.string.session_all_exercises), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            session.exercises.forEachIndexed { i, e ->
                val status = when {
                    e.skipped -> stringResource(R.string.session_skipped)
                    else -> "${Fmt.int(e.working.size)} / ${Fmt.int(e.targets.sets)}"
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (i == selected) colors.primaryContainer else colors.surface)
                        .clickable { onSelect(i) }.heightIn(min = 52.dp).padding(horizontal = FitSpacing.s8)
                        .semantics(mergeDescendants = true) { contentDescription = "${e.exercise.nameFa}، $status" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
                ) {
                    Icon(if (e.isDone) FitIcons.Check else FitIcons.Dumbbell, contentDescription = null, tint = if (e.isDone) colors.primary else colors.subtle, modifier = Modifier.size(20.dp))
                    Text(e.exercise.nameFa, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
                Row(Modifier.padding(start = 28.dp), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                    if (e.sets.isEmpty()) FitTextButton(stringResource(R.string.session_replace), onClick = { onReplace(e) })
                    if (!e.isDone || e.skipped) {
                        FitTextButton(stringResource(if (e.skipped) R.string.session_unskip else R.string.session_skip), onClick = { onSkip(e, !e.skipped) })
                    }
                }
            }
            FitSecondaryButton(stringResource(R.string.session_add_exercise), onClick = onAdd, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
            FitTextButton(stringResource(R.string.discard), onClick = onDiscard)
        }
    }
}

@Composable
private fun FinishDialog(session: WorkoutSession, now: Instant, onDismiss: () -> Unit, onFinish: (Int?, String?) -> Unit, onDiscard: () -> Unit) {
    var effort by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val empty = session.exercises.none { it.sets.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.finish_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                if (empty) {
                    Text(stringResource(R.string.finish_empty))
                } else {
                    Text(stringResource(R.string.finish_body, Fmt.int(session.workingSets), Fmt.int((session.activeMillis(now) / 60_000).toInt())))
                    Text(stringResource(R.string.finish_effort), style = MaterialTheme.typography.bodyMedium)
                    FitChips((1..10).toList(), setOfNotNull(effort), { effort = if (effort == it) null else it }, label = { Fmt.int(it) }, multi = false)
                    FitTextField(note, { note = it.take(300) }, stringResource(R.string.field_note) + " (" + stringResource(R.string.optional) + ")")
                }
            }
        },
        confirmButton = {
            if (empty) FitTextButton(stringResource(R.string.discard), onClick = onDiscard)
            else FitTextButton(stringResource(R.string.finish_save), onClick = { onFinish(effort, note) })
        },
        dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
