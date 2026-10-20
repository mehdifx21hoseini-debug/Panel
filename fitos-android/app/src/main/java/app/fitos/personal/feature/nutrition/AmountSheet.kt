package app.fitos.personal.feature.nutrition

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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.nutrition.Accuracy
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.Food
import app.fitos.core.domain.nutrition.FoodPortion
import app.fitos.core.domain.nutrition.FoodRules
import app.fitos.core.domain.nutrition.FoodSource
import app.fitos.core.domain.nutrition.FoodState
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.Nutrients
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.feature.session.StepperField
import app.fitos.personal.ui.Fmt
import kotlin.math.roundToInt

val Meal.label: Int
    get() = when (this) {
        Meal.BREAKFAST -> R.string.meal_breakfast
        Meal.LUNCH -> R.string.meal_lunch
        Meal.DINNER -> R.string.meal_dinner
        Meal.SNACK -> R.string.meal_snack
    }

val FoodState.label: Int?
    get() = when (this) {
        FoodState.RAW -> R.string.food_state_raw
        FoodState.COOKED -> R.string.food_state_cooked
        FoodState.NOT_APPLICABLE -> null
    }

val Accuracy.label: Int
    get() = when (this) {
        Accuracy.HIGH -> R.string.accuracy_high
        Accuracy.MEDIUM -> R.string.accuracy_medium
        Accuracy.LOW -> R.string.accuracy_low
    }

/** "USDA (FDC 168878) · دقت بالا", "برچسب بسته · دقت متوسط"… shown wherever a food's numbers are used. */
@Composable
fun sourceText(food: Food): String {
    val source = when (food.source) {
        FoodSource.USDA -> stringResource(R.string.source_usda, food.sourceRef.orEmpty())
        FoodSource.LABEL -> stringResource(R.string.source_label)
        FoodSource.USER -> stringResource(R.string.source_user)
        FoodSource.ESTIMATE -> stringResource(R.string.source_estimate)
        FoodSource.RECIPE -> stringResource(R.string.source_recipe)
    }
    return source + " · " + stringResource(food.accuracy.label)
}

/** "۱۲۰ کیلوکالری" with no decimals; "—" when unknown. */
@Composable
fun kcalText(kcal: Double?): String = kcal?.let { stringResource(R.string.kcal_value, Fmt.int(it.roundToInt())) } ?: "—"

fun gramsText(g: Double?): String = g?.let { PersianNumbers.format(it, maxFractionDigits = 1) } ?: "—"

/** "۱۵۰ گرم" or "۱٫۵ پیمانه". */
@Composable
fun amountText(grams: Double, portionLabel: String?, quantity: Double?): String =
    if (portionLabel != null && quantity != null) "${PersianNumbers.format(quantity, maxFractionDigits = 2)} $portionLabel"
    else stringResource(R.string.grams_value, PersianNumbers.format(grams, maxFractionDigits = 1))

/** Kcal and the three macros in one line: "۲۲۰ کیلوکالری · پ ۱۲ · ک ۳۰ · چ ۵". */
@Composable
fun macroLine(n: Nutrients): String = listOf(
    kcalText(n.kcal),
    stringResource(R.string.macro_p_short, gramsText(n.proteinG)),
    stringResource(R.string.macro_c_short, gramsText(n.carbsG)),
    stringResource(R.string.macro_f_short, gramsText(n.fatG)),
).joinToString(" · ")

/**
 * Choose how much: grams or one of the food's household units, with the nutrients updating as
 * you type. [meal] is shown when the entry can move to another meal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountSheet(
    food: Food,
    initial: Amount?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Amount, Meal?) -> Unit,
    meal: Meal? = null,
    onDelete: (() -> Unit)? = null,
) {
    val grams = remember { FoodPortion("", 1.0) }
    val units = listOf(grams) + food.portions
    var unit by remember { mutableStateOf(units.firstOrNull { it.label == initial?.portionLabel && initial?.portionQuantity != null } ?: if (initial == null && food.portions.isNotEmpty()) food.portions.first() else grams) }
    var text by remember {
        mutableStateOf(
            when {
                initial == null -> if (unit === grams) Fmt.field(100) else Fmt.field(1)
                unit === grams -> Fmt.field(initial.grams, 1)
                else -> Fmt.field(initial.portionQuantity ?: 1.0, 2)
            },
        )
    }
    var chosenMeal by remember { mutableStateOf(meal) }
    val quantity = PersianNumbers.parse(text)
    val amount = quantity?.let { if (unit === grams) Amount(it) else Amount.of(unit, it) }?.takeIf { it.grams in FoodRules.LOG_G }
    val colors = FitTheme.colors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            Text(food.nameFa, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                listOfNotNull(food.state.label?.let { stringResource(it) }, sourceText(food)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtle,
            )
            if (units.size > 1) {
                FieldLabel(stringResource(R.string.amount_unit))
                FitChips(
                    units, setOf(unit),
                    onToggle = { picked ->
                        if (picked !== unit) {
                            // Keep the same weight when switching units.
                            val g = amount?.grams
                            unit = picked
                            text = when {
                                g == null -> ""
                                picked === grams -> Fmt.field(g, 1)
                                else -> Fmt.field(g / picked.grams, 2)
                            }
                        }
                    },
                    label = { u -> if (u === grams) stringResource(R.string.unit_g) else stringResource(R.string.portion_with_grams, u.label, gramsText(u.grams)) },
                    multi = false,
                )
            }
            StepperField(
                text, { text = it },
                label = if (unit === grams) stringResource(R.string.amount_grams) else stringResource(R.string.amount_count, unit.label),
                step = if (unit === grams) 10.0 else 0.5,
                decimals = if (unit === grams) 1 else 2,
                max = if (unit === grams) FoodRules.LOG_G.endInclusive else 50.0,
                suffix = if (unit === grams) stringResource(R.string.unit_g) else null,
            )
            val n = amount?.let { food.nutrientsFor(it.grams) }
            Text(
                n?.let { macroLine(it) } ?: stringResource(R.string.amount_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = if (n == null) colors.error else colors.onSurface,
            )
            if (n != null && unit !== grams) Text(stringResource(R.string.grams_value, gramsText(amount.grams)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            if (n?.fiberG == null && n != null) Text(stringResource(R.string.fiber_unknown), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            if (chosenMeal != null) {
                FieldLabel(stringResource(R.string.meal_label))
                FitChips(Meal.entries, setOfNotNull(chosenMeal), { chosenMeal = it }, label = { stringResource(it.label) }, multi = false)
            }
            FitPrimaryButton(confirmLabel, onClick = { amount?.let { onConfirm(it, chosenMeal) } }, enabled = amount != null, icon = FitIcons.Check, modifier = Modifier.fillMaxWidth())
            if (onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                    FitSecondaryButton(stringResource(R.string.action_delete), onClick = onDelete, icon = FitIcons.Trash, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
