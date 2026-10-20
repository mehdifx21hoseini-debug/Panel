package app.fitos.personal.feature.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.EmptyState
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitEmoji
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.time.DateFormats
import app.fitos.core.domain.training.PersonalRecord
import app.fitos.core.domain.training.Progression
import app.fitos.core.domain.training.RecordType
import app.fitos.core.domain.training.SessionExercise
import app.fitos.core.domain.training.SessionReport
import app.fitos.core.domain.training.SessionSummary
import app.fitos.core.domain.training.SetLog
import app.fitos.core.domain.training.SetType
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.personal.R
import app.fitos.personal.feature.training.label
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---- history list ----

class SessionHistoryViewModel(workouts: WorkoutRepository) : ViewModel() {
    val sessions: StateFlow<List<SessionSummary>?> = workouts.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun SessionHistoryScreen(viewModel: SessionHistoryViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    EditScaffold(title = stringResource(R.string.training_history), onBack = onBack) {
        val list = sessions ?: return@EditScaffold
        if (list.isEmpty()) {
            FitCard { EmptyState(FitEmoji.LIFTING, stringResource(R.string.training_history), stringResource(R.string.history_empty)) }
            return@EditScaffold
        }
        Text(stringResource(R.string.training_sessions_done, Fmt.int(list.size)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        list.forEach { s ->
            FitCard {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(s.id) }.heightIn(min = FitSpacing.target),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FitSpacing.s2)) {
                        Text(DateFormats.longDate(s.day), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                        Text(
                            listOfNotNull(s.dayName ?: stringResource(R.string.session_title), s.variantKind?.let { stringResource(it.label) }).joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.history_item, Fmt.int(s.activeMinutes), Fmt.int(s.workingSets), Fmt.int(s.tonnageKg.roundToInt())),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Icon(FitIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- one session: the end-of-session summary and the history detail ----

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(private val workouts: WorkoutRepository, val sessionId: String) : ViewModel() {
    /** Rebuilt whenever a set is fixed, so totals and records stay true. */
    val report: StateFlow<SessionReport?> = workouts.session(sessionId)
        .mapLatest { if (it == null) null else workouts.report(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateSet(id: String, input: app.fitos.core.domain.training.SetInput) = viewModelScope.launch { workouts.updateSet(id, input) }
    fun deleteSet(id: String) = viewModelScope.launch { workouts.deleteSet(id) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        workouts.delete(sessionId)
        onDone()
    }
}

@Composable
fun SessionDetailScreen(viewModel: SessionDetailViewModel, justFinished: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var editing by remember { mutableStateOf<Pair<SessionExercise, SetLog>?>(null) }
    var deleting by remember { mutableStateOf(false) }
    EditScaffold(
        title = stringResource(R.string.summary_title),
        onBack = onBack,
        bottomBar = if (justFinished) {
            { FitPrimaryButton(stringResource(R.string.summary_done), onClick = onDone, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth()) }
        } else {
            null
        },
    ) {
        val r = report ?: return@EditScaffold
        val s = r.session
        val minutes = ((s.activeMillis(s.endedAt ?: s.startedAt) + 30_000) / 60_000).toInt()
        FitCard(hero = true) {
            Text(DateFormats.longDate(s.day), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            Text(
                listOfNotNull(s.dayName ?: stringResource(R.string.session_title), s.variantKind?.let { stringResource(it.label) }).joinToString(" · "),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                Stat(Fmt.int(minutes), stringResource(R.string.summary_duration), Modifier.weight(1f))
                Stat(Fmt.int(s.workingSets), stringResource(R.string.summary_sets), Modifier.weight(1f))
                Stat(PersianNumbers.format(r.tonnageKg.roundToInt()), stringResource(R.string.summary_tonnage), Modifier.weight(1f))
            }
            r.previous?.let { prev ->
                if (prev.tonnageKg > 0) {
                    val change = ((r.tonnageKg - prev.tonnageKg) / prev.tonnageKg * 100).roundToInt()
                    val sign = if (change > 0) "+" else ""
                    Text(
                        stringResource(R.string.summary_vs_previous, DateFormats.dayMonth(prev.day), sign + PersianNumbers.format(change.toDouble(), maxFractionDigits = 0) + "٪"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            s.perceivedEffort?.let { Text(stringResource(R.string.summary_effort, Fmt.int(it)), style = MaterialTheme.typography.bodySmall, color = colors.subtle) }
            s.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        SectionTitle(stringResource(R.string.summary_records))
        val names = s.exercises.associate { it.exercise.id to it.exercise.nameFa }
        if (r.records.isEmpty()) {
            FitNote(stringResource(R.string.summary_no_records))
        } else {
            FitCard {
                r.records.forEach { (exerciseId, records) ->
                    Text(names[exerciseId].orEmpty(), style = MaterialTheme.typography.titleSmall)
                    records.sortedBy { it.type.ordinal }.forEach { rec ->
                        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8), verticalAlignment = Alignment.CenterVertically) {
                            Icon(FitIcons.Star, contentDescription = null, tint = colors.primary)
                            Text(recordText(rec), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (r.firstTime.isNotEmpty()) {
            Text(
                stringResource(R.string.summary_first_time, r.firstTime.mapNotNull { names[it] }.joinToString("، ")),
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtle,
            )
        }

        SectionTitle(stringResource(R.string.summary_exercises))
        Text(stringResource(R.string.session_edit_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
        s.exercises.filter { it.sets.isNotEmpty() }.forEach { e ->
            FitCard {
                Text(e.exercise.nameFa, style = MaterialTheme.typography.titleSmall)
                e.sets.forEachIndexed { i, set ->
                    if (i > 0) HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = e to set }.heightIn(min = 44.dp).padding(vertical = FitSpacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12),
                    ) {
                        Text(
                            if (set.type == SetType.WARMUP) stringResource(R.string.set_warmup_label) else Fmt.int(e.working.indexOf(set) + 1),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.subtle,
                        )
                        Text(setText(e.exercise.logType, set), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(FitIcons.Edit, contentDescription = null, tint = colors.subtle)
                    }
                }
            }
        }
        if (!justFinished) {
            FitTextButton(stringResource(R.string.session_delete), onClick = { deleting = true })
        }
    }

    editing?.let { (e, set) ->
        SetEditSheet(
            set, e.sets.indexOf(set) + 1, e.exercise.logType, Progression.step(e.exercise, e.progression),
            onDismiss = { editing = null },
            onSave = { viewModel.updateSet(set.id, it); editing = null },
            onDelete = { viewModel.deleteSet(set.id); editing = null },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.session_delete_title)) },
            text = { Text(stringResource(R.string.session_delete_body)) },
            confirmButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { deleting = false; viewModel.delete(onBack) }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { deleting = false }) },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(value, style = numeralStyle(26), color = FitTheme.colors.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
    }
}

@Composable
fun recordText(r: PersonalRecord): String = when (r.type) {
    RecordType.E1RM -> stringResource(R.string.record_e1rm, Fmt.kg(r.value))
    RecordType.MAX_WEIGHT_FOR_REPS -> stringResource(R.string.record_weight_reps, Fmt.int(r.reps ?: 0), Fmt.kg(r.value))
    RecordType.MAX_REPS_BODYWEIGHT -> stringResource(R.string.record_max_reps, Fmt.int(r.value.roundToInt()))
    RecordType.SESSION_TONNAGE -> stringResource(R.string.record_tonnage, PersianNumbers.format(r.value.roundToInt()))
}
