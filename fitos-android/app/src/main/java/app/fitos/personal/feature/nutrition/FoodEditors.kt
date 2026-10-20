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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitChips
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitNumberField
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.nutrition.Food
import app.fitos.core.domain.nutrition.FoodInput
import app.fitos.core.domain.nutrition.FoodPortion
import app.fitos.core.domain.nutrition.FoodRepository
import app.fitos.core.domain.nutrition.FoodRules
import app.fitos.core.domain.nutrition.FoodSource
import app.fitos.core.domain.nutrition.FoodState
import app.fitos.core.domain.nutrition.Nutrients
import app.fitos.core.domain.nutrition.RecipeIngredient
import app.fitos.core.domain.nutrition.RecipeInput
import app.fitos.core.domain.nutrition.RecipeMath
import app.fitos.personal.R
import app.fitos.personal.feature.profile.FieldHint
import app.fitos.personal.feature.profile.FieldLabel
import app.fitos.personal.ui.EditScaffold
import app.fitos.personal.ui.Fmt
import app.fitos.personal.ui.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---- detail ----

class FoodDetailViewModel(private val foods: FoodRepository, val foodId: String) : ViewModel() {
    val food: StateFlow<Food?> = foods.food(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ingredients: StateFlow<List<RecipeIngredient>> = foods.ingredients(foodId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite() = viewModelScope.launch { food.value?.let { foods.setFavorite(it.id, !it.isFavorite) } }
    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        foods.deleteCustom(foodId)
        onDone()
    }
}

@Composable
fun FoodDetailScreen(viewModel: FoodDetailViewModel, onBack: () -> Unit, onEdit: (Food) -> Unit) {
    val food by viewModel.food.collectAsStateWithLifecycle()
    val ingredients by viewModel.ingredients.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    var deleting by remember { mutableStateOf(false) }
    EditScaffold(title = food?.nameFa.orEmpty(), onBack = onBack) {
        val f = food ?: return@EditScaffold
        FitCard {
            Text(stringResource(R.string.per_100g_title), style = MaterialTheme.typography.titleSmall)
            Line(stringResource(R.string.field_kcal), kcalText(f.per100g.kcal))
            Line(stringResource(R.string.field_protein), grams(f.per100g.proteinG))
            Line(stringResource(R.string.field_carbs), grams(f.per100g.carbsG))
            Line(stringResource(R.string.field_fat), grams(f.per100g.fatG))
            Line(stringResource(R.string.field_fiber), grams(f.per100g.fiberG))
            f.sugarPer100g?.let { Line(stringResource(R.string.field_sugar), grams(it)) }
            f.sodiumMgPer100g?.let { Line(stringResource(R.string.field_sodium), stringResource(R.string.mg_value, PersianNumbers.format(it, maxFractionDigits = 0))) }
            f.state.label?.let { Line(stringResource(R.string.food_state), stringResource(it)) }
        }
        if (f.portions.isNotEmpty()) {
            FitCard {
                Text(stringResource(R.string.food_portions), style = MaterialTheme.typography.titleSmall)
                f.portions.forEach { Line(it.label, stringResource(R.string.grams_value, gramsText(it.grams))) }
            }
        }
        if (f.isRecipe) {
            FitCard {
                Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleSmall)
                ingredients.forEach { Line(it.food.nameFa, stringResource(R.string.grams_value, gramsText(it.grams))) }
                f.recipeServings?.let { Line(stringResource(R.string.recipe_servings), PersianNumbers.format(it, maxFractionDigits = 1)) }
            }
        }
        SectionTitle(stringResource(R.string.food_source_title))
        FitCard {
            Text(sourceText(f), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(f.source.explanation), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        FitSecondaryButton(
            stringResource(if (f.isFavorite) R.string.favorite_remove else R.string.favorite_add),
            onClick = viewModel::toggleFavorite,
            icon = if (f.isFavorite) FitIcons.StarFilled else FitIcons.Star,
            modifier = Modifier.fillMaxWidth(),
        )
        if (f.isCustom) {
            FitSecondaryButton(stringResource(R.string.exercise_edit), onClick = { onEdit(f) }, icon = FitIcons.Edit, modifier = Modifier.fillMaxWidth())
            FitTextButton(stringResource(if (f.isRecipe) R.string.recipe_delete else R.string.food_delete), onClick = { deleting = true })
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.food_delete_title)) },
            text = { Text(stringResource(R.string.food_delete_body)) },
            confirmButton = { FitTextButton(stringResource(R.string.action_delete), onClick = { deleting = false; viewModel.delete(onBack) }) },
            dismissButton = { FitTextButton(stringResource(R.string.action_cancel), onClick = { deleting = false }) },
        )
    }
}

val FoodSource.explanation: Int
    get() = when (this) {
        FoodSource.USDA -> R.string.source_usda_about
        FoodSource.LABEL -> R.string.source_label_about
        FoodSource.USER -> R.string.source_user_about
        FoodSource.ESTIMATE -> R.string.source_estimate_about
        FoodSource.RECIPE -> R.string.source_recipe_about
    }

@Composable
private fun grams(g: Double?): String = g?.let { stringResource(R.string.grams_value, gramsText(it)) } ?: stringResource(R.string.value_unknown)

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = FitTheme.colors.subtle, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---- own food ----

class FoodFormViewModel(private val foods: FoodRepository, val foodId: String?) : ViewModel() {
    val existing: StateFlow<Food?> = (if (foodId == null) MutableStateFlow(null) else foods.food(foodId))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var saving = false

    fun save(input: FoodInput, onSaved: (String) -> Unit) {
        if (saving || FoodRules.check(input).isNotEmpty()) return
        saving = true
        viewModelScope.launch {
            val id = if (foodId == null) foods.addCustom(input).id else foodId.also { foods.updateCustom(it, input) }
            onSaved(id)
        }
    }
}

private class PortionRow(label: String, grams: String) {
    var label by mutableStateOf(label)
    var grams by mutableStateOf(grams)
}

@Composable
fun FoodFormScreen(viewModel: FoodFormViewModel, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val existing by viewModel.existing.collectAsStateWithLifecycle()
    val title = stringResource(if (viewModel.foodId == null) R.string.food_new else R.string.food_edit)
    if (viewModel.foodId != null && existing == null) {
        EditScaffold(title = title, onBack = onBack) {}
        return
    }
    val start = existing
    var name by remember { mutableStateOf(start?.nameFa.orEmpty()) }
    var brand by remember { mutableStateOf(start?.brand.orEmpty()) }
    var state by remember { mutableStateOf(start?.state ?: FoodState.NOT_APPLICABLE) }
    var source by remember { mutableStateOf(start?.source?.takeIf { it in OwnSources } ?: FoodSource.LABEL) }
    var basis by remember { mutableStateOf(Fmt.field(100)) }
    fun field(v: Double?) = v?.let { Fmt.field(it, 2) }.orEmpty()
    var kcal by remember { mutableStateOf(field(start?.per100g?.kcal)) }
    var protein by remember { mutableStateOf(field(start?.per100g?.proteinG)) }
    var carbs by remember { mutableStateOf(field(start?.per100g?.carbsG)) }
    var fat by remember { mutableStateOf(field(start?.per100g?.fatG)) }
    var fiber by remember { mutableStateOf(field(start?.per100g?.fiberG)) }
    val portions = remember { mutableStateListOf<PortionRow>().apply { start?.portions?.forEach { add(PortionRow(it.label, Fmt.field(it.grams, 1))) } } }
    var tried by remember { mutableStateOf(false) }

    fun num(s: String) = PersianNumbers.parse(s)
    fun input() = FoodInput(
        nameFa = name, nameEn = null, brand = brand, basisGrams = num(basis) ?: 0.0,
        atBasis = Nutrients(num(kcal), num(protein), num(carbs), num(fat), num(fiber)),
        state = state, source = source,
        portions = portions.mapNotNull { r -> num(r.grams)?.takeIf { r.label.isNotBlank() && it in FoodRules.PORTION_G }?.let { FoodPortion(r.label.trim(), it) } },
    )
    val problems = if (tried) FoodRules.check(input()) else emptySet()
    @Composable
    fun err(p: FoodRules.Problem, text: Int) = if (p in problems) stringResource(text) else null

    EditScaffold(
        title = title,
        onBack = onBack,
        bottomBar = { FitPrimaryButton(stringResource(R.string.action_save), onClick = { tried = true; viewModel.save(input(), onSaved) }, modifier = Modifier.fillMaxWidth()) },
    ) {
        FitTextField(name, { name = it.take(FoodRules.NAME_MAX) }, stringResource(R.string.food_name), error = err(FoodRules.Problem.NAME, R.string.error_food_name))
        FitTextField(brand, { brand = it.take(40) }, stringResource(R.string.food_brand) + " (" + stringResource(R.string.optional) + ")")
        FieldLabel(stringResource(R.string.food_source_title))
        FitChips(OwnSources, setOf(source), { source = it }, label = { stringResource(it.ownLabel) }, multi = false)
        FieldLabel(stringResource(R.string.food_state))
        FitChips(FoodState.entries, setOf(state), { state = it }, label = { stringResource(it.label ?: R.string.food_state_na) }, multi = false)

        SectionTitle(stringResource(R.string.food_values_title))
        FitNumberField(basis, { basis = it }, stringResource(R.string.food_basis), suffix = stringResource(R.string.unit_g), maxIntegerDigits = 4,
            hint = stringResource(R.string.food_basis_hint), error = err(FoodRules.Problem.BASIS, R.string.error_food_basis))
        FitNumberField(kcal, { kcal = it }, stringResource(R.string.field_kcal), suffix = stringResource(R.string.unit_kcal), maxIntegerDigits = 4, maxFractionDigits = 2,
            error = err(FoodRules.Problem.KCAL, R.string.error_food_kcal))
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FitNumberField(protein, { protein = it }, stringResource(R.string.field_protein), suffix = stringResource(R.string.unit_g), maxIntegerDigits = 3, maxFractionDigits = 2,
                modifier = Modifier.weight(1f), error = err(FoodRules.Problem.PROTEIN, R.string.error_food_macro))
            FitNumberField(carbs, { carbs = it }, stringResource(R.string.field_carbs), suffix = stringResource(R.string.unit_g), maxIntegerDigits = 3, maxFractionDigits = 2,
                modifier = Modifier.weight(1f), error = err(FoodRules.Problem.CARBS, R.string.error_food_macro))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FitNumberField(fat, { fat = it }, stringResource(R.string.field_fat), suffix = stringResource(R.string.unit_g), maxIntegerDigits = 3, maxFractionDigits = 2,
                modifier = Modifier.weight(1f), error = err(FoodRules.Problem.FAT, R.string.error_food_macro))
            FitNumberField(fiber, { fiber = it }, stringResource(R.string.field_fiber) + " (" + stringResource(R.string.optional) + ")", suffix = stringResource(R.string.unit_g),
                maxIntegerDigits = 3, maxFractionDigits = 2, modifier = Modifier.weight(1f), error = err(FoodRules.Problem.FIBER, R.string.error_food_macro))
        }
        if (FoodRules.Problem.MACRO_SUM in problems) FitNote(stringResource(R.string.error_food_macro_sum), warning = true)
        val current = input()
        if (FoodRules.check(current).isEmpty() && FoodRules.energyMismatch(current.atBasis)) FitNote(stringResource(R.string.food_energy_mismatch), warning = true)

        SectionTitle(stringResource(R.string.food_portions))
        FieldHint(stringResource(R.string.food_portions_hint))
        portions.forEachIndexed { i, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8), verticalAlignment = Alignment.CenterVertically) {
                FitTextField(row.label, { row.label = it.take(30) }, stringResource(R.string.portion_label), modifier = Modifier.weight(1.4f))
                FitNumberField(row.grams, { row.grams = it }, stringResource(R.string.unit_g), modifier = Modifier.weight(1f), maxIntegerDigits = 4)
                IconButton(onClick = { portions.removeAt(i) }) { Icon(FitIcons.Trash, contentDescription = stringResource(R.string.portion_remove)) }
            }
        }
        FitTextButton(stringResource(R.string.portion_add), onClick = { portions.add(PortionRow("", "")) })
    }
}

private val OwnSources = listOf(FoodSource.LABEL, FoodSource.USER, FoodSource.ESTIMATE)

private val FoodSource.ownLabel: Int
    get() = when (this) {
        FoodSource.LABEL -> R.string.own_source_label
        FoodSource.USER -> R.string.own_source_user
        else -> R.string.own_source_estimate
    }

// ---- recipe ----

class RecipeEditorViewModel(private val foods: FoodRepository, val recipeId: String?) : ViewModel() {
    val existing: StateFlow<Food?> = (if (recipeId == null) MutableStateFlow(null) else foods.food(recipeId))
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val existingIngredients: StateFlow<List<RecipeIngredient>?> = (if (recipeId == null) emptyFlow() else foods.ingredients(recipeId))
        .stateIn(viewModelScope, SharingStarted.Eagerly, if (recipeId == null) emptyList() else null)

    /** The ingredients being edited; survives trips to the food picker. */
    val ingredients = mutableStateListOf<RecipeIngredient>()
    private var loaded = recipeId == null
    private var saving = false

    fun load(list: List<RecipeIngredient>) {
        if (loaded) return
        loaded = true
        ingredients.addAll(list)
    }

    fun addIngredient(foodId: String, grams: Double) = viewModelScope.launch {
        foods.food(foodId).first()?.let { ingredients.add(RecipeIngredient(it, grams)) }
    }

    fun save(name: String, servings: Double, cookedWeight: Double?, onSaved: (String) -> Unit) {
        if (saving || name.isBlank() || ingredients.isEmpty() || servings !in FoodRules.SERVINGS) return
        saving = true
        viewModelScope.launch {
            onSaved(foods.saveRecipe(recipeId, RecipeInput(name, servings, cookedWeight, ingredients.map { it.food.id to it.grams })))
        }
    }
}

@Composable
fun RecipeEditorScreen(viewModel: RecipeEditorViewModel, pickedIngredient: Pair<String, Double>?, onPicked: () -> Unit, onBack: () -> Unit, onAddIngredient: () -> Unit, onSaved: (String) -> Unit) {
    val existing by viewModel.existing.collectAsStateWithLifecycle()
    val existingIngredients by viewModel.existingIngredients.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val title = stringResource(if (viewModel.recipeId == null) R.string.recipe_new else R.string.recipe_edit)
    LaunchedEffect(pickedIngredient) {
        pickedIngredient?.let { (id, g) -> viewModel.addIngredient(id, g); onPicked() }
    }
    val list = existingIngredients
    if (viewModel.recipeId != null && (existing == null || list == null)) {
        EditScaffold(title = title, onBack = onBack) {}
        return
    }
    LaunchedEffect(list) { list?.let(viewModel::load) }
    // Saveable: the screen leaves composition while the food picker is open for an ingredient.
    var name by rememberSaveable { mutableStateOf(existing?.nameFa.orEmpty()) }
    var servings by rememberSaveable { mutableStateOf(existing?.recipeServings?.let { Fmt.field(it, 1) } ?: Fmt.field(1)) }
    var cooked by rememberSaveable { mutableStateOf(existing?.recipeCookedWeightG?.let { Fmt.field(it, 0) }.orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val servingsValue = PersianNumbers.parse(servings)
    val cookedValue = PersianNumbers.parse(cooked)?.takeIf { it > 0 }
    val result = RecipeMath.compute(viewModel.ingredients.toList(), cookedValue)

    EditScaffold(
        title = title,
        onBack = onBack,
        bottomBar = {
            FitPrimaryButton(stringResource(R.string.action_save), onClick = {
                tried = true
                viewModel.save(name, servingsValue ?: 0.0, cookedValue, onSaved)
            }, modifier = Modifier.fillMaxWidth())
        },
    ) {
        FitTextField(name, { name = it.take(FoodRules.NAME_MAX) }, stringResource(R.string.recipe_name),
            error = if (tried && name.isBlank()) stringResource(R.string.error_food_name) else null)
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
            FitNumberField(servings, { servings = it }, stringResource(R.string.recipe_servings), maxIntegerDigits = 3, modifier = Modifier.weight(1f),
                error = if (tried && (servingsValue == null || servingsValue !in FoodRules.SERVINGS)) stringResource(R.string.error_recipe_servings) else null)
            FitNumberField(cooked, { cooked = it }, stringResource(R.string.recipe_cooked_weight), suffix = stringResource(R.string.unit_g),
                maxIntegerDigits = 5, allowDecimal = false, modifier = Modifier.weight(1f))
        }
        FieldHint(stringResource(R.string.recipe_cooked_hint))

        SectionTitle(stringResource(R.string.recipe_ingredients))
        if (viewModel.ingredients.isEmpty()) {
            FitNote(stringResource(if (tried) R.string.error_recipe_empty else R.string.recipe_empty), warning = tried)
        }
        FitCard {
            viewModel.ingredients.forEachIndexed { i, ing ->
                if (i > 0) HorizontalDivider(color = colors.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = i }.heightIn(min = 48.dp).padding(vertical = FitSpacing.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ing.food.nameFa, style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.grams_value, gramsText(ing.grams)) + " · " + kcalText(ing.food.nutrientsFor(ing.grams).kcal),
                            style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                    }
                    IconButton(onClick = { viewModel.ingredients.removeAt(i) }) { Icon(FitIcons.Trash, contentDescription = stringResource(R.string.recipe_remove_ingredient, ing.food.nameFa)) }
                }
            }
            FitSecondaryButton(stringResource(R.string.recipe_add_ingredient), onClick = onAddIngredient, icon = FitIcons.Plus, modifier = Modifier.fillMaxWidth())
        }
        if (viewModel.ingredients.isNotEmpty()) {
            FitCard {
                Text(stringResource(R.string.recipe_result), style = MaterialTheme.typography.titleSmall)
                Line(stringResource(R.string.recipe_total_weight), stringResource(R.string.grams_value, gramsText(result.totalWeightG)))
                if (servingsValue != null && servingsValue in FoodRules.SERVINGS) {
                    Line(stringResource(R.string.recipe_serving_weight), stringResource(R.string.grams_value, gramsText(result.servingGrams(servingsValue))))
                    Text(stringResource(R.string.recipe_per_serving) + ": " + macroLine(result.total * (1 / servingsValue)), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.per_100g_title) + ": " + macroLine(result.per100g), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (result.per100g.fiberG == null) Text(stringResource(R.string.fiber_unknown), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
        }
    }
    editing?.let { i ->
        val ing = viewModel.ingredients.getOrNull(i) ?: return@let
        AmountSheet(
            food = ing.food,
            initial = app.fitos.core.domain.nutrition.Amount(ing.grams),
            confirmLabel = stringResource(R.string.action_save),
            onDismiss = { editing = null },
            onConfirm = { amount, _ -> viewModel.ingredients[i] = ing.copy(grams = amount.grams); editing = null },
            onDelete = { viewModel.ingredients.removeAt(i); editing = null },
        )
    }
}
