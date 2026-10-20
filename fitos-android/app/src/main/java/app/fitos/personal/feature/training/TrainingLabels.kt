package app.fitos.personal.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.Muscle
import app.fitos.core.domain.training.PlannedExercise
import app.fitos.core.domain.training.VariantKind
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt

val VariantKind.label: Int
    get() = when (this) {
        VariantKind.FULL -> R.string.variant_full
        VariantKind.HOME -> R.string.variant_home
        VariantKind.MINIMAL -> R.string.variant_minimal
    }

val LogType.label: Int
    get() = when (this) {
        LogType.WEIGHT_REPS -> R.string.log_weight_reps
        LogType.REPS -> R.string.log_reps
        LogType.TIME -> R.string.log_time
        LogType.DISTANCE -> R.string.log_distance
    }

fun muscleNames(muscles: Collection<Muscle>): String = muscles.joinToString("، ") { it.nameFa }

/** "۲:۳۰" for 150 s, "۴۵ ثانیه" under a minute. */
@Composable
fun restText(seconds: Int): String =
    if (seconds >= 60) stringResource(R.string.minutes_seconds, PersianNumbers.digits((seconds / 60).toString()), PersianNumbers.digits("%02d".format(seconds % 60)))
    else stringResource(R.string.seconds_value, Fmt.int(seconds))

fun repRange(min: Int?, max: Int?): String = when {
    min == null || max == null -> "—"
    min == max -> Fmt.int(min)
    else -> "${Fmt.int(min)}–${Fmt.int(max)}"
}

/** "۴ ست × ۶–۸ تکرار · استراحت ۲:۳۰ · RIR ۲" */
@Composable
fun plannedSummary(p: PlannedExercise): String {
    val main = when (p.exercise.logType) {
        LogType.WEIGHT_REPS, LogType.REPS -> stringResource(R.string.planned_summary_reps, Fmt.int(p.sets), repRange(p.repMin, p.repMax))
        LogType.TIME -> stringResource(R.string.planned_summary_time, Fmt.int(p.sets), Fmt.int(p.durationSec ?: 0))
        LogType.DISTANCE -> stringResource(R.string.planned_summary_distance, Fmt.int(p.distanceM ?: 0))
    }
    val parts = buildList {
        add(main)
        p.restSec?.let { add(stringResource(R.string.planned_rest, restText(it))) }
        p.targetRir?.let { add("RIR ${Fmt.int(it)}") }
        p.startWeightKg?.let { add(stringResource(R.string.value_kg, Fmt.kg(it))) }
    }
    return parts.joinToString(" · ")
}
