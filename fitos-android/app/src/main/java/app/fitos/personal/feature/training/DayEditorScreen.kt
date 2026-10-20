package app.fitos.personal.feature.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.BodySide
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitSegmented
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.component.MuscleMap
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.PlannedExercise
import app.fitos.core.domain.training.PlannedTargets
import app.fitos.core.domain.training.PlannedVolume
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.ReturnPlan
import app.fitos.core.domain.training.ReturnPlanRepository
import app.fitos.core.domain.training.SessionEstimate
import app.fitos.core.domain.training.TrainingRules
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.WorkoutSession
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import java.time.Clock
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DayEditorViewModel(
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    private val returnPlan: ReturnPlanRepository,
    private val clock: Clock,
    private val programId: String,
    val dayId: String,
) : ViewModel() {
    val open: StateFlow<WorkoutSession?> = workouts.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Starts this day in the selected version (lighter during an easy start after a break), or opens the session already running. */
    fun start(onReady: () -> Unit) = viewModelScope.launch {
        if (workouts.active.first() == null) {
            val easy = ReturnPlan.next(returnPlan.latest.first(), clock.instant(), workouts.history.first().map { it.startedAt })
            val day = programs.program(programId).first()?.days?.firstOrNull { it.id == dayId }
            workouts.start(programId, dayId, kind.value, lighter = ReturnPlan.lightens(easy, kind.value, day?.hasMinimal == true))
        }
        onReady()
    }

    val program: StateFlow<Program?> = programs.program(programId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _kind = MutableStateFlow(VariantKind.FULL)
    val kind: StateFlow<VariantKind> = _kind.asStateFlow()

    fun select(kind: VariantKind) {
        _kind.value = kind
    }

    fun updateDay(name: String, rest: Boolean, weekday: DayOfWeek?, note: String?) = viewModelScope.launch {
        programs.updateDay(dayId, name.take(TrainingRules.NAME_MAX), rest, weekday, note)
    }
    fun update(id: String, targets: PlannedTargets) = viewModelScope.launch { programs.updatePlanned(id, targets) }
    fun move(id: String, by: Int) = viewModelScope.launch { programs.movePlanned(id, by) }
    fun remove(id: String) = viewModelScope.launch { programs.removePlanned(id) }
    fun copyFromFull() = viewModelScope.launch { programs.copyVariant(dayId, VariantKind.FULL, kind.value) }
}

@Composable
fun DayEditorScreen(
    viewModel: DayEditorViewModel,
    onBack: () -> Unit,
    onAddExercise: (VariantKind) -> Unit,
    onReplace: (plannedId: String) -> Unit,
    onOpenSession: () -> Unit,
) {
    val program by viewModel.program.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var editingDay by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlannedExercise?>(null) }
    val day = program?.days?.firstOrNull { it.id == viewModel.dayId }

    EditScaffold(
        title = day?.let { if (it.isRest) stringResource(R.string.training_rest) else it.name } ?: "",
        onBack = onBack,
        bottomBar = if (day != null && !day.isRest) {
            {
                val count = day.variant(kind)?.exercises?.size ?: 0
                FitPrimaryButton(
                    stringResource(R.string.day_add_exercise),
                    onClick = { onAddExercise(kind) },
                    enabled = count < TrainingRules.MAX_EXERCISES,
                    icon = FitIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            null
        },
    ) {
        if (day == null) return@EditScaffold
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.day_settings), style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOfNotNull(
                            day.preferredWeekday?.let(JalaliCalendar::weekdayName) ?: stringResource(R.string.day_weekday_none),
                            if (day.isRest) stringResource(R.string.training_rest) else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                    )
                }
                IconButton(onClick = { editingDay = true }) { Icon(FitIcons.Edit, contentDescription = stringResource(R.string.day_settings)) }
            }
        }
        if (day.isRest) {
            FitNote(stringResource(R.string.day_rest_body))
            return@EditScaffold
        }

        FitSegmented(VariantKind.entries, kind, viewModel::select, label = { stringResource(it.label) })
        val exercises = day.variant(kind)?.exercises.orEmpty()
        val (primary, secondary) = PlannedVolume.muscles(exercises)
        if (exercises.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                MuscleMap(BodySide.FRONT, primary, secondary, width = 40.dp)
                MuscleMap(BodySide.BACK, primary, secondary, width = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(muscleNames(primary), style = MaterialTheme.typography.bodyMedium, color = colors.training.ink)
                    Text(
                        stringResource(R.string.training_exercises_minutes, Fmt.int(exercises.size), Fmt.int(SessionEstimate.minutes(exercises))),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                    )
                }
            }
        } else {
            FitNote(stringResource(R.string.day_variant_empty))
            if (kind != VariantKind.FULL && day.full.isNotEmpty()) {
                FitSecondaryButton(stringResource(R.string.day_copy_from_full), onClick = viewModel::copyFromFull, icon = FitIcons.Copy, modifier = Modifier.fillMaxWidth())
            }
        }

        if (exercises.isNotEmpty()) {
            if (open != null) {
                FitSecondaryButton(stringResource(R.string.training_resume), onClick = onOpenSession, icon = FitIcons.Play, modifier = Modifier.fillMaxWidth())
            } else {
                FitSecondaryButton(stringResource(R.string.day_start, stringResource(kind.label)), onClick = { viewModel.start(onOpenSession) }, icon = FitIcons.Play, modifier = Modifier.fillMaxWidth())
            }
        }
        exercises.forEachIndexed { index, p ->
            PlannedRow(
                p,
                superset = p.supersetGroup != null,
                canUp = index > 0,
                canDown = index < exercises.lastIndex,
                onClick = { editing = p },
                onUp = { viewModel.move(p.id, -1) },
                onDown = { viewModel.move(p.id, 1) },
            )
        }
    }

    if (editingDay && day != null) {
        DayDialog(
            name = day.name,
            rest = day.isRest,
            weekday = day.preferredWeekday,
            note = day.note,
            onDismiss = { editingDay = false },
            onSave = { name, rest, weekday, note ->
                viewModel.updateDay(name, rest, weekday, note)
                editingDay = false
            },
        )
    }
    editing?.let { p ->
        val list = day?.variant(kind)?.exercises.orEmpty()
        val previous = list.getOrNull(list.indexOfFirst { it.id == p.id } - 1)
        PlannedExerciseSheet(
            planned = p,
            previousGroup = previous?.let { it.supersetGroup ?: (it.position + 1) },
            hasPrevious = previous != null,
            onDismiss = { editing = null },
            onSave = { targets ->
                viewModel.update(p.id, targets)
                // Joining a superset also gives the previous exercise the same group number.
                if (targets.supersetGroup != null && previous != null && previous.supersetGroup == null) {
                    viewModel.update(previous.id, app.fitos.core.domain.training.PlannedTargets(
                        previous.sets, previous.repMin, previous.repMax, previous.targetRir, previous.durationSec, previous.distanceM,
                        previous.startWeightKg, previous.restSec, targets.supersetGroup, previous.note, previous.progression,
                    ))
                }
                editing = null
            },
            onReplace = {
                editing = null
                onReplace(p.id)
            },
            onRemove = {
                viewModel.remove(p.id)
                editing = null
            },
        )
    }
}

@Composable
private fun PlannedRow(p: PlannedExercise, superset: Boolean, canUp: Boolean, canDown: Boolean, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    val colors = FitTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A coloured edge marks exercises done back to back as a superset.
        Box(Modifier.width(4.dp).heightIn(min = 64.dp).fillMaxHeight().background(if (superset) colors.training.fill else colors.surface))
        Column(
            Modifier.weight(1f).clickable(onClick = onClick).padding(horizontal = FitSpacing.s12, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s2),
        ) {
            Text(p.exercise.nameFa, style = MaterialTheme.typography.titleSmall)
            Text(plannedSummary(p), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (superset) Text(stringResource(R.string.planned_superset), style = MaterialTheme.typography.labelMedium, color = colors.training.ink)
        }
        IconButton(onClick = onUp, enabled = canUp) { Icon(FitIcons.ChevronUp, contentDescription = stringResource(R.string.move_up)) }
        IconButton(onClick = onDown, enabled = canDown) { Icon(FitIcons.ChevronDown, contentDescription = stringResource(R.string.move_down)) }
    }
}

@Composable
private fun DayDialog(name: String, rest: Boolean, weekday: DayOfWeek?, note: String?, onDismiss: () -> Unit, onSave: (String, Boolean, DayOfWeek?, String?) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var r by remember { mutableStateOf(rest) }
    var w by remember { mutableStateOf(weekday) }
    var t by remember { mutableStateOf(note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.day_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                FitTextField(value = n, onValueChange = { n = it.take(TrainingRules.NAME_MAX) }, label = stringResource(R.string.day_name))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.day_is_rest), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = r, onCheckedChange = { r = it }, colors = SwitchDefaults.colors(checkedTrackColor = FitTheme.colors.primary))
                }
                FieldLabel(stringResource(R.string.day_weekday))
                FitChips(
                    options = JalaliCalendar.WEEK_ORDER,
                    selected = setOfNotNull(w),
                    onToggle = { w = if (w == it) null else it },
                    label = { JalaliCalendar.weekdayName(it) },
                    multi = false,
                )
                FitTextField(value = t, onValueChange = { t = it.take(200) }, label = stringResource(R.string.field_note) + " (" + stringResource(R.string.optional) + ")")
            }
        },
        confirmButton = { FitTextButton(stringResource(R.string.action_save), onClick = { onSave(n.ifBlank { name }, r, w, t) }) },
        dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}
