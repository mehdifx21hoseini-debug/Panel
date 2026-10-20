package app.fitos.personal.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitStepper
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.PlannedExercise
import app.fitos.core.domain.training.PlannedTargets
import app.fitos.core.domain.training.ProgressionRule
import app.fitos.core.domain.training.ProgressionType
import app.fitos.core.domain.training.TrainingRules
import app.fitos.core.domain.training.TrainingRules.Problem
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldHint
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.Fmt
import kotlin.math.roundToInt

private val RestOptions = listOf(45, 60, 90, 120, 150, 180)
private val RirOptions = listOf<Int?>(null, 0, 1, 2, 3, 4)
private val DeloadOptions = listOf(5, 10, 15, 20)

/** Edit the targets of one planned exercise; also the place to replace or remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedExerciseSheet(
    planned: PlannedExercise,
    previousGroup: Int?,
    hasPrevious: Boolean,
    onDismiss: () -> Unit,
    onSave: (PlannedTargets) -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    val type = planned.exercise.logType
    var sets by remember { mutableIntStateOf(planned.sets) }
    var repMin by remember { mutableStateOf(planned.repMin?.let(Fmt::field).orEmpty()) }
    var repMax by remember { mutableStateOf(planned.repMax?.let(Fmt::field).orEmpty()) }
    var duration by remember { mutableStateOf(planned.durationSec?.let(Fmt::field).orEmpty()) }
    var distance by remember { mutableStateOf(planned.distanceM?.let(Fmt::field).orEmpty()) }
    var rir by remember { mutableStateOf(planned.targetRir) }
    var rest by remember { mutableIntStateOf(planned.restSec ?: 90) }
    var weight by remember { mutableStateOf(planned.startWeightKg?.let { Fmt.field(it, 2) }.orEmpty()) }
    var superset by remember { mutableStateOf(planned.supersetGroup != null) }
    var progression by remember { mutableStateOf(planned.progression?.type == ProgressionType.DOUBLE_PROGRESSION) }
    var increment by remember { mutableStateOf((planned.progression?.incrementKg ?: planned.exercise.weightIncrementKg ?: 2.5).let { Fmt.field(it, 2) }) }
    var deload by remember { mutableIntStateOf(planned.progression?.deloadPercent ?: 10) }
    var requireGood by remember { mutableStateOf(planned.progression?.requireGoodQuality ?: true) }
    var note by remember { mutableStateOf(planned.note.orEmpty()) }
    var problems by remember { mutableStateOf(emptySet<Problem>()) }

    fun int(text: String) = PersianNumbers.parse(text)?.roundToInt()
    fun targets() = PlannedTargets(
        sets = sets,
        repMin = if (type == LogType.WEIGHT_REPS || type == LogType.REPS) int(repMin) else null,
        repMax = if (type == LogType.WEIGHT_REPS || type == LogType.REPS) int(repMax) else null,
        targetRir = rir,
        durationSec = if (type == LogType.TIME) int(duration) else null,
        distanceM = if (type == LogType.DISTANCE) int(distance) else null,
        startWeightKg = if (type == LogType.WEIGHT_REPS) PersianNumbers.parse(weight) else null,
        restSec = rest,
        supersetGroup = if (superset) planned.supersetGroup ?: previousGroup ?: (planned.position + 1) else null,
        note = note,
        progression = if (type == LogType.WEIGHT_REPS && progression) {
            ProgressionRule(ProgressionType.DOUBLE_PROGRESSION, PersianNumbers.parse(increment), deload, requireGoodQuality = requireGood)
        } else {
            null
        },
    )
    @Composable
    fun err(p: Problem, text: Int) = if (p in problems) stringResource(text) else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FitTheme.colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s16),
        ) {
            Text(planned.exercise.nameFa, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FitStepper(
                label = stringResource(R.string.field_sets),
                valueText = Fmt.int(sets),
                onDecrease = { sets-- },
                onIncrease = { sets++ },
                canDecrease = sets > TrainingRules.SETS.first,
                canIncrease = sets < TrainingRules.SETS.last,
                decreaseLabel = stringResource(R.string.action_decrease),
                increaseLabel = stringResource(R.string.action_increase),
            )
            when (type) {
                LogType.WEIGHT_REPS, LogType.REPS -> Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
                    FitNumberField(repMin, { repMin = it }, stringResource(R.string.field_rep_min), allowDecimal = false, maxIntegerDigits = 3, modifier = Modifier.weight(1f),
                        error = err(Problem.REPS, R.string.error_reps) ?: err(Problem.REP_ORDER, R.string.error_rep_order))
                    FitNumberField(repMax, { repMax = it }, stringResource(R.string.field_rep_max), allowDecimal = false, maxIntegerDigits = 3, modifier = Modifier.weight(1f))
                }
                LogType.TIME -> FitNumberField(duration, { duration = it }, stringResource(R.string.field_duration), suffix = stringResource(R.string.unit_second),
                    allowDecimal = false, error = err(Problem.DURATION, R.string.error_duration))
                LogType.DISTANCE -> FitNumberField(distance, { distance = it }, stringResource(R.string.field_distance), suffix = stringResource(R.string.unit_meter),
                    allowDecimal = false, maxIntegerDigits = 5, error = err(Problem.DISTANCE, R.string.error_distance))
            }
            if (type == LogType.WEIGHT_REPS || type == LogType.REPS) {
                Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                    FieldLabel(stringResource(R.string.field_rir))
                    FitChips(RirOptions, setOf(rir), { rir = it }, label = { it?.let(Fmt::int) ?: stringResource(R.string.rir_none) }, multi = false)
                    FieldHint(stringResource(R.string.field_rir_hint))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                FieldLabel(stringResource(R.string.field_rest))
                FitChips((RestOptions + rest).distinct().sorted(), setOf(rest), { rest = it }, label = { restText(it) }, multi = false)
            }
            if (type == LogType.WEIGHT_REPS) {
                FitNumberField(weight, { weight = it }, stringResource(R.string.field_start_weight) + " (" + stringResource(R.string.optional) + ")",
                    suffix = stringResource(R.string.unit_kg), maxIntegerDigits = 3, maxFractionDigits = 2, error = err(Problem.WEIGHT, R.string.error_planned_weight))
                Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.field_progression), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = progression, onCheckedChange = { progression = it }, colors = SwitchDefaults.colors(checkedTrackColor = FitTheme.colors.primary))
                    }
                    FieldHint(stringResource(R.string.field_progression_hint))
                    if (progression) {
                        FitNumberField(increment, { increment = it }, stringResource(R.string.field_increment), suffix = stringResource(R.string.unit_kg),
                            maxIntegerDigits = 2, maxFractionDigits = 2, error = err(Problem.INCREMENT, R.string.error_increment))
                        FieldLabel(stringResource(R.string.field_deload))
                        FitChips(DeloadOptions, setOf(deload), { deload = it }, label = { Fmt.int(it) + "٪" }, multi = false)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.field_require_quality), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = requireGood, onCheckedChange = { requireGood = it }, colors = SwitchDefaults.colors(checkedTrackColor = FitTheme.colors.primary))
                        }
                    }
                }
            }
            if (hasPrevious) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.field_superset), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = superset, onCheckedChange = { superset = it }, colors = SwitchDefaults.colors(checkedTrackColor = FitTheme.colors.primary))
                }
            }
            FitTextField(note, { note = it.take(200) }, stringResource(R.string.field_note) + " (" + stringResource(R.string.optional) + ")")
            FitPrimaryButton(
                stringResource(R.string.action_save),
                onClick = {
                    val t = targets()
                    problems = TrainingRules.check(t, type)
                    if (problems.isEmpty()) onSave(t)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                FitSecondaryButton(stringResource(R.string.planned_replace), onClick = onReplace, icon = FitIcons.Swap, modifier = Modifier.weight(1f))
                FitSecondaryButton(stringResource(R.string.planned_remove), onClick = onRemove, icon = FitIcons.Trash, modifier = Modifier.weight(1f))
            }
        }
    }
}
