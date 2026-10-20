package app.fitos.personal.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.SetInput
import app.fitos.core.domain.training.SetLog
import app.fitos.core.domain.training.SetQuality
import app.fitos.core.domain.training.SetType
import app.fitos.core.domain.training.TrainingRules
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.Fmt
import kotlin.math.roundToInt

private val RirChoices = listOf(0, 1, 2, 3, 4, 5)

/** Editable values of one set. Numbers are kept as typed text and read when saving. */
@Stable
class SetForm(start: SetInput) {
    var weight by mutableStateOf(start.weightKg?.let { Fmt.field(it, 2) }.orEmpty())
    var reps by mutableStateOf(start.reps?.let(Fmt::field).orEmpty())
    var seconds by mutableStateOf(start.durationSec?.let(Fmt::field).orEmpty())
    var meters by mutableStateOf(start.distanceM?.let { Fmt.field(it, 0) }.orEmpty())
    var rir by mutableStateOf(start.rir)
    var quality by mutableStateOf(start.quality)
    var pain by mutableStateOf(start.pain)
    var warmup by mutableStateOf(start.type == SetType.WARMUP)

    /** The set, or null while a required value is missing or out of range. */
    fun toInput(logType: LogType): SetInput? {
        val w = PersianNumbers.parse(weight)
        val r = PersianNumbers.parse(reps)?.roundToInt()
        val type = if (warmup) SetType.WARMUP else SetType.WORKING
        return when (logType) {
            LogType.WEIGHT_REPS -> {
                if (w == null || w !in TrainingRules.WEIGHT_KG || r == null || r !in TrainingRules.REPS) return null
                SetInput(type, w, r, rir = rir, quality = quality, pain = pain)
            }
            LogType.REPS -> {
                if (r == null || r !in TrainingRules.REPS || (w != null && w !in TrainingRules.WEIGHT_KG)) return null
                SetInput(type, w?.takeIf { it > 0 }, r, rir = rir, quality = quality, pain = pain)
            }
            LogType.TIME -> {
                val s = PersianNumbers.parse(seconds)?.roundToInt()
                if (s == null || s !in 1..36_000) return null
                SetInput(type, durationSec = s, quality = quality, pain = pain)
            }
            LogType.DISTANCE -> {
                val m = PersianNumbers.parse(meters)
                if (m == null || m <= 0 || m > 100_000) return null
                SetInput(type, distanceM = m, quality = quality, pain = pain)
            }
        }
    }
}

/** The main numbers with − / + buttons; the step is the exercise's weight step. */
@Composable
fun SetMainFields(form: SetForm, logType: LogType, weightStep: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
        when (logType) {
            LogType.WEIGHT_REPS, LogType.REPS -> {
                StepperField(
                    form.weight, { form.weight = it },
                    label = stringResource(if (logType == LogType.REPS) R.string.field_set_added_weight else R.string.field_set_weight),
                    suffix = stringResource(R.string.unit_kg), step = weightStep, decimals = 2, max = TrainingRules.WEIGHT_KG.endInclusive,
                )
                StepperField(form.reps, { form.reps = it }, label = stringResource(R.string.field_set_reps), step = 1.0, decimals = 0, max = 100.0)
            }
            LogType.TIME -> StepperField(form.seconds, { form.seconds = it }, label = stringResource(R.string.field_set_seconds), step = 5.0, decimals = 0, max = 36_000.0)
            LogType.DISTANCE -> StepperField(form.meters, { form.meters = it }, label = stringResource(R.string.field_set_meters), step = 100.0, decimals = 0, max = 100_000.0)
        }
    }
}

/** RIR, quality, pain and warm-up: optional, folded away during a session. */
@Composable
fun SetDetailFields(form: SetForm, logType: LogType) {
    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
        if (logType == LogType.WEIGHT_REPS || logType == LogType.REPS) {
            FieldLabel(stringResource(R.string.field_rir))
            FitChips(RirChoices, setOfNotNull(form.rir), { form.rir = if (form.rir == it) null else it }, label = { Fmt.int(it) }, multi = false)
        }
        FieldLabel(stringResource(R.string.field_quality))
        FitChips(SetQuality.entries, setOfNotNull(form.quality), { form.quality = if (form.quality == it) null else it }, label = { stringResource(it.label) }, multi = false)
        SwitchRow(stringResource(R.string.field_pain), form.pain) { form.pain = it }
        SwitchRow(stringResource(R.string.field_warmup), form.warmup) { form.warmup = it }
    }
}

val SetQuality.label: Int
    get() = when (this) {
        SetQuality.GOOD -> R.string.quality_good
        SetQuality.OK -> R.string.quality_ok
        SetQuality.POOR -> R.string.quality_poor
    }

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = FitTheme.colors.primary))
    }
}

/** − field + : the field accepts typing in any digits; the buttons move by [step]. */
@Composable
fun StepperField(value: String, onValue: (String) -> Unit, label: String, step: Double, decimals: Int, max: Double, suffix: String? = null) {
    val colors = FitTheme.colors
    val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface)
    fun move(by: Double) {
        val next = ((PersianNumbers.parse(value) ?: 0.0) + by).coerceIn(0.0, max)
        // Snap to the step so 61 + 2.5 does not drift into odd numbers.
        val snapped = (next / step).roundToInt() * step
        onValue(if (decimals == 0) Fmt.field(snapped.roundToInt()) else Fmt.field(snapped, decimals))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
        FilledTonalIconButton(onClick = { move(-step) }, colors = buttonColors, modifier = Modifier.size(52.dp)) {
            Icon(FitIcons.Minus, contentDescription = stringResource(R.string.action_decrease) + " " + label)
        }
        FitNumberField(
            value, onValue, label, modifier = Modifier.weight(1f), suffix = suffix,
            allowDecimal = decimals > 0, maxIntegerDigits = if (max >= 10_000) 5 else 3, maxFractionDigits = decimals.coerceAtLeast(1),
        )
        FilledTonalIconButton(onClick = { move(step) }, colors = buttonColors, modifier = Modifier.size(52.dp)) {
            Icon(FitIcons.Plus, contentDescription = stringResource(R.string.action_increase) + " " + label)
        }
    }
}

/** "۶۰ × ۱۲", "۱۲ تکرار", "+۵ × ۸", "۴۵ ثانیه", "۲۰۰۰ متر". */
@Composable
fun setText(logType: LogType, weightKg: Double?, reps: Int?, durationSec: Int?, distanceM: Double?): String = when (logType) {
    LogType.WEIGHT_REPS -> "${Fmt.kg(weightKg ?: 0.0)} × ${Fmt.int(reps ?: 0)}"
    LogType.REPS -> if (weightKg != null && weightKg > 0) "+${Fmt.kg(weightKg)} × ${Fmt.int(reps ?: 0)}" else stringResource(R.string.reps_value, Fmt.int(reps ?: 0))
    LogType.TIME -> stringResource(R.string.seconds_value, Fmt.int(durationSec ?: 0))
    LogType.DISTANCE -> stringResource(R.string.meters_value, Fmt.int((distanceM ?: 0.0).roundToInt()))
}

@Composable
fun setText(logType: LogType, set: SetLog): String = setText(logType, set.weightKg, set.reps, set.durationSec, set.distanceM)

/** Fix or delete a set that is already saved (in the session or later from history). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetEditSheet(set: SetLog, number: Int, logType: LogType, weightStep: Double, onDismiss: () -> Unit, onSave: (SetInput) -> Unit, onDelete: () -> Unit) {
    val form = remember(set.id) { SetForm(set.input()) }
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FitTheme.colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s16),
        ) {
            Text(stringResource(R.string.set_edit_title, Fmt.int(number)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            SetMainFields(form, logType, weightStep)
            SetDetailFields(form, logType)
            val input = form.toInput(logType)
            FitPrimaryButton(stringResource(R.string.action_save), onClick = { input?.let(onSave) }, enabled = input != null, modifier = Modifier.fillMaxWidth())
            FitSecondaryButton(stringResource(R.string.set_delete), onClick = { confirmDelete = true }, icon = FitIcons.Trash, modifier = Modifier.fillMaxWidth())
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.set_delete_title)) },
            text = { Text(stringResource(R.string.set_delete_body, Fmt.int(number), setText(logType, set))) },
            confirmButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { confirmDelete = false; onDelete() }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { confirmDelete = false }) },
        )
    }
}
