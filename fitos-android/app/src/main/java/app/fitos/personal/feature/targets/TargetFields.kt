package app.fitos.personal.feature.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.nutrition.TargetAssumptions
import app.fitos.core.domain.profile.InputError
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.personal.R
import app.fitos.personal.feature.profile.label
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.message
import kotlin.math.roundToInt

/** Editable daily targets, with the macro-energy check and the below-BMR warning. */
@Composable
fun TargetFields(form: TargetsForm, errors: Map<TargetField, InputError>, onChange: (TargetsForm.() -> TargetsForm) -> Unit) {
    @Composable
    fun field(f: TargetField, value: String, label: Int, unit: Int, modifier: Modifier, decimals: Int = 0, ime: ImeAction = ImeAction.Next) {
        FitNumberField(
            value = value,
            onValueChange = { v ->
                onChange {
                    when (f) {
                        TargetField.KCAL -> copy(kcal = v)
                        TargetField.PROTEIN -> copy(protein = v)
                        TargetField.CARBS -> copy(carbs = v)
                        TargetField.FAT -> copy(fat = v)
                        TargetField.FIBER -> copy(fiber = v)
                        TargetField.WATER -> copy(water = v)
                        TargetField.SLEEP -> copy(sleep = v)
                    }
                }
            },
            label = stringResource(label),
            suffix = stringResource(unit),
            allowDecimal = decimals > 0,
            maxFractionDigits = decimals,
            error = errors[f]?.message(Fmt.field(f.range.start, 2) to Fmt.field(f.range.endInclusive, 2)),
            imeAction = ime,
            modifier = modifier,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
        field(TargetField.KCAL, form.kcal, R.string.field_kcal, R.string.unit_kcal, Modifier)
        if (form.isBelowBmr) {
            FitNote(
                stringResource(R.string.targets_below_bmr, Fmt.int(form.base.assumptions!!.bmr.roundToInt())),
                warning = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
            field(TargetField.PROTEIN, form.protein, R.string.field_protein, R.string.unit_g, Modifier.weight(1f))
            field(TargetField.FAT, form.fat, R.string.field_fat, R.string.unit_g, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
            field(TargetField.CARBS, form.carbs, R.string.field_carbs, R.string.unit_g, Modifier.weight(1f))
            field(TargetField.FIBER, form.fiber, R.string.field_fiber, R.string.unit_g, Modifier.weight(1f))
        }
        form.macroKcal?.let {
            Text(
                stringResource(R.string.targets_macro_sum, Fmt.int(it)),
                style = MaterialTheme.typography.bodySmall,
                color = FitTheme.colors.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
            field(TargetField.WATER, form.water, R.string.field_water, R.string.unit_liter, Modifier.weight(1f), decimals = 2)
            field(TargetField.SLEEP, form.sleep, R.string.field_sleep, R.string.unit_hour, Modifier.weight(1f), decimals = 1, ime = ImeAction.Done)
        }
    }
}

/** Plain-language explanation of how a calculated target was derived. */
@Composable
fun AssumptionsCard(a: TargetAssumptions) {
    val colors = FitTheme.colors
    FitCard {
        Text(stringResource(R.string.assumptions_title), style = MaterialTheme.typography.titleSmall)
        val lines = buildList {
            add(
                stringResource(
                    R.string.assumption_bmr,
                    Fmt.int(a.bmr.roundToInt()), Fmt.int(a.ageYears), Fmt.kg(a.weightKg), Fmt.kg(a.heightCm),
                ),
            )
            val adaptive = a.adaptive
            if (adaptive != null) {
                add(stringResource(R.string.assumption_tdee_measured, Fmt.int(a.tdee.roundToInt())))
                add(stringResource(R.string.assumption_adaptive, Fmt.int(adaptive.days), Fmt.int(adaptive.meanIntakeKcal.roundToInt()), rateLine(adaptive.rateKgPerWeek), Fmt.int(adaptive.previousKcal)))
            } else {
                add(stringResource(R.string.assumption_tdee, a.activityLevel.label(), Fmt.field(a.activityLevel.factor, 3), Fmt.int(a.tdee.roundToInt())))
            }
            add(
                when (a.goal) {
                    PrimaryGoal.FAT_LOSS -> stringResource(R.string.assumption_deficit, Fmt.int(-a.adjustmentPercent))
                    PrimaryGoal.MAINTAIN -> stringResource(R.string.assumption_maintain)
                    PrimaryGoal.MUSCLE_GAIN -> stringResource(R.string.assumption_surplus, Fmt.int(a.adjustmentPercent))
                },
            )
            if (a.raisedToFloor) add(stringResource(R.string.assumption_floor))
            add(stringResource(R.string.assumption_protein, Fmt.field(a.proteinPerKg), Fmt.field(a.fatPerKg), Fmt.kg(a.referenceWeightKg)))
            if (a.fatRaisedToMinimum) add(stringResource(R.string.assumption_fat_min))
            add(stringResource(R.string.assumption_rest))
        }
        Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        }
        Text(stringResource(R.string.assumption_disclaimer), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
}

@Composable
private fun rateLine(kgPerWeek: Double): String = when {
    kgPerWeek <= -0.05 -> stringResource(R.string.rate_down, Fmt.kg(-kgPerWeek))
    kgPerWeek >= 0.05 -> stringResource(R.string.rate_up, Fmt.kg(kgPerWeek))
    else -> stringResource(R.string.rate_flat)
}
