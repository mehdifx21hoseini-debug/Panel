package app.fitos.personal.feature.nutrition

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.MealPlan
import app.fitos.core.domain.nutrition.MealPlanItem
import app.fitos.core.domain.nutrition.MealPlanRepository
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.nutrition.SavedMeal
import app.fitos.core.domain.nutrition.SavedMealRepository
import app.fitos.personal.R
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---- saved meals ----

class SavedMealsViewModel(private val meals: SavedMealRepository) : ViewModel() {
    val all: StateFlow<List<SavedMeal>?> = meals.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun rename(id: String, name: String) = viewModelScope.launch { if (name.isNotBlank()) meals.rename(id, name) }
    fun delete(id: String) = viewModelScope.launch { meals.delete(id) }
}

@Composable
fun SavedMealsScreen(viewModel: SavedMealsViewModel, onBack: () -> Unit) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var renaming by remember { mutableStateOf<SavedMeal?>(null) }
    var deleting by remember { mutableStateOf<SavedMeal?>(null) }
    EditScaffold(title = stringResource(R.string.saved_meals_title), onBack = onBack) {
        val list = all ?: return@EditScaffold
        if (list.isEmpty()) FitNote(stringResource(R.string.saved_meals_empty))
        list.forEach { m ->
            FitCard {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                m.meal?.let { Text(stringResource(it.label), style = MaterialTheme.typography.labelMedium, color = colors.subtle) }
                m.items.forEach { item ->
                    Text(item.food.nameFa + " · " + amountText(item.amount.grams, item.amount.portionLabel, item.amount.portionQuantity), style = MaterialTheme.typography.bodySmall)
                }
                Text(macroLine(m.totals.let { app.fitos.core.domain.nutrition.Nutrients(it.kcal, it.proteinG, it.carbsG, it.fatG, it.fiberG) }), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                    FitTextButton(stringResource(R.string.saved_meal_rename), onClick = { renaming = m })
                    FitTextButton(stringResource(R.string.action_delete), onClick = { deleting = m })
                }
            }
        }
        FitNote(stringResource(R.string.saved_meals_hint))
    }
    renaming?.let { m ->
        var name by remember(m.id) { mutableStateOf(m.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.saved_meal_rename)) },
            text = { FitTextField(name, { name = it.take(40) }, stringResource(R.string.saved_meal_name)) },
            confirmButton = { FitTextButton(stringResource(R.string.action_save), onClick = { viewModel.rename(m.id, name); renaming = null }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { renaming = null }) },
        )
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.saved_meal_delete_title)) },
            text = { Text(stringResource(R.string.saved_meal_delete_body, m.name)) },
            confirmButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { viewModel.delete(m.id); deleting = null }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { deleting = null }) },
        )
    }
}

// ---- meal plan ----

data class PlanState(val loaded: Boolean = false, val plan: MealPlan? = null, val target: NutritionTarget? = null)

class MealPlanViewModel(private val plans: MealPlanRepository, targets: NutritionTargetRepository, private val today: LocalDate, private val planName: String) : ViewModel() {
    val state: StateFlow<PlanState> = combine(plans.active, targets.effectiveOn(today)) { plan, target -> PlanState(true, plan, target) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState())

    fun update(item: MealPlanItem, amount: Amount, meal: Meal) = viewModelScope.launch { plans.updateItem(item.id, meal, amount.grams) }
    fun remove(item: MealPlanItem) = viewModelScope.launch { plans.removeItem(item.id) }
    fun fromToday() = viewModelScope.launch { plans.replaceFromDay(plans.ensureActive(planName), today) }
}

@Composable
fun MealPlanScreen(viewModel: MealPlanViewModel, onBack: () -> Unit, onAdd: (Meal) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var editing by remember { mutableStateOf<MealPlanItem?>(null) }
    var replacing by remember { mutableStateOf(false) }
    EditScaffold(title = stringResource(R.string.plan_title), onBack = onBack) {
        if (!state.loaded) return@EditScaffold
        val items = state.plan?.items.orEmpty().filter { it.weekday == null }
        FitNote(stringResource(R.string.plan_about))
        val day = NutrientTotals.of(items.map { it.food.nutrientsFor(it.grams) })
        FitCard {
            Text(stringResource(R.string.plan_day_total), style = MaterialTheme.typography.titleSmall)
            Text(
                kcalText(day.kcal) + (state.target?.let { " / " + Fmt.int(it.kcal) } ?: "") + " · " +
                    stringResource(R.string.macro_p_short, gramsText(day.proteinG)) + (state.target?.let { " / " + Fmt.int(it.proteinG) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Meal.entries.forEach { meal ->
            val mealItems = items.filter { it.meal == meal }
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(meal.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = { onAdd(meal) }) { Icon(FitIcons.Plus, contentDescription = stringResource(R.string.plan_add_to, stringResource(meal.label))) }
                }
                mealItems.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = item }.heightIn(min = 48.dp).padding(vertical = FitSpacing.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.food.nameFa, style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.grams_value, gramsText(item.grams)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                        }
                        Text(kcalText(item.food.nutrientsFor(item.grams).kcal), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (mealItems.isEmpty()) Text(stringResource(R.string.plan_meal_empty), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
        }
        FitSecondaryButton(stringResource(R.string.plan_from_today), onClick = { replacing = true }, icon = FitIcons.Copy, modifier = Modifier.fillMaxWidth())
    }
    editing?.let { item ->
        AmountSheet(
            food = item.food,
            initial = Amount(item.grams),
            confirmLabel = stringResource(R.string.action_save),
            meal = item.meal,
            onDismiss = { editing = null },
            onConfirm = { amount, meal -> viewModel.update(item, amount, meal ?: item.meal); editing = null },
            onDelete = { viewModel.remove(item); editing = null },
        )
    }
    if (replacing) {
        AlertDialog(
            onDismissRequest = { replacing = false },
            title = { Text(stringResource(R.string.plan_from_today)) },
            text = { Text(stringResource(R.string.plan_from_today_body)) },
            confirmButton = { FitTextButton(stringResource(R.string.plan_replace), onClick = { viewModel.fromToday(); replacing = false }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { replacing = false }) },
        )
    }
}
