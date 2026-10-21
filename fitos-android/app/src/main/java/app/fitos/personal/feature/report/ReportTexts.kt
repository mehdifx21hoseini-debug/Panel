package app.fitos.personal.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fitos.core.domain.report.Highlight
import app.fitos.core.domain.report.ProgressMetric
import app.fitos.personal.R
import app.fitos.personal.feature.habits.percentText
import app.fitos.personal.ui.Fmt
import kotlin.math.roundToInt

/** The week's highlight in one sentence, with its numbers. */
@Composable
fun highlightText(h: Highlight): String = when (h) {
    is Highlight.Strength -> {
        val e = h.exercise
        val before = e.before ?: 0.0
        when (e.metric) {
            ProgressMetric.E1RM -> stringResource(R.string.highlight_e1rm, e.name, Fmt.kg(before), Fmt.kg(e.best), Fmt.kg(e.bestWeightKg ?: 0.0), Fmt.int(e.bestReps ?: 0))
            ProgressMetric.WEIGHT -> stringResource(R.string.highlight_weight, e.name, Fmt.kg(before), Fmt.kg(e.best))
            ProgressMetric.REPS -> stringResource(R.string.highlight_reps, e.name, Fmt.int(before.roundToInt()), Fmt.int(e.best.roundToInt()))
            ProgressMetric.SECONDS -> stringResource(R.string.highlight_seconds, e.name, Fmt.int(before.roundToInt()), Fmt.int(e.best.roundToInt()))
            ProgressMetric.METERS -> stringResource(R.string.highlight_meters, e.name, Fmt.int(before.roundToInt()), Fmt.int(e.best.roundToInt()))
        }
    }
    is Highlight.WeightDown -> stringResource(R.string.highlight_weight_down, Fmt.kg(h.kg))
    is Highlight.WeightUp -> stringResource(R.string.highlight_weight_up, Fmt.kg(h.kg))
    is Highlight.WaistDown -> stringResource(R.string.highlight_waist_down, h.siteName, Fmt.kg(h.cm))
    is Highlight.AllSessions -> stringResource(R.string.highlight_sessions, Fmt.int(h.done), Fmt.int(h.planned))
    is Highlight.Habits -> stringResource(R.string.highlight_habits, percentText(h.score))
    is Highlight.Protein -> stringResource(R.string.highlight_protein, Fmt.int(h.days))
    is Highlight.FoodEveryDay -> stringResource(R.string.highlight_food, Fmt.int(h.days))
}
