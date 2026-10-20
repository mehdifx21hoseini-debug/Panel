package app.fitos.personal.feature.nutrition

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.Food
import app.fitos.core.domain.nutrition.FoodCategory
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.FoodRepository
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.MealPlanRepository
import app.fitos.core.domain.nutrition.SavedMeal
import app.fitos.core.domain.nutrition.SavedMealRepository
import app.fitos.core.domain.training.ExerciseFilter
import app.fitos.personal.R
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Log to a meal, pick a recipe ingredient, add to the meal plan, or just browse the bank. */
enum class PickerMode { LOG, INGREDIENT, PLAN, BROWSE }

enum class PickerTab { ALL, RECENT, FAVORITES, MINE, RECIPES, SAVED }

data class PickerFilter(val query: String = "", val tab: PickerTab = PickerTab.ALL, val category: FoodCategory? = null)

class FoodPickerViewModel(
    private val foods: FoodRepository,
    savedMeals: SavedMealRepository,
    private val log: FoodLogRepository,
    private val plans: MealPlanRepository,
    val mode: PickerMode,
    val date: LocalDate?,
    val meal: Meal?,
    private val planName: String,
) : ViewModel() {
    private val _filter = MutableStateFlow(PickerFilter(tab = if (mode == PickerMode.LOG) PickerTab.RECENT else PickerTab.ALL))
    val filter: StateFlow<PickerFilter> = _filter.asStateFlow()
    val saved: StateFlow<List<SavedMeal>> = savedMeals.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<List<Food>?> = combine(foods.all, foods.recent, _filter) { all, recent, f ->
        val q = ExerciseFilter.fold(f.query)
        val base = when {
            // A search looks through everything, whatever the tab.
            q.isNotEmpty() -> all
            f.tab == PickerTab.RECENT -> recent
            f.tab == PickerTab.FAVORITES -> all.filter { it.isFavorite }
            f.tab == PickerTab.MINE -> all.filter { it.isCustom && !it.isRecipe }
            f.tab == PickerTab.RECIPES -> all.filter { it.isRecipe }
            else -> all.filter { f.category == null || it.category == f.category }
        }
        val matched = if (q.isEmpty()) base else base.filter { food ->
            ExerciseFilter.fold(food.nameFa).contains(q) || food.nameEn?.let { ExerciseFilter.fold(it).contains(q) } == true
        }
        // Recipes cannot contain themselves or other recipes' nesting loops; keep ingredients simple.
        if (mode == PickerMode.INGREDIENT) matched.filter { !it.isRecipe } else matched
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFilter(f: PickerFilter) {
        _filter.value = f
    }

    fun toggleFavorite(food: Food) = viewModelScope.launch { foods.setFavorite(food.id, !food.isFavorite) }

    fun add(food: Food, amount: Amount, meal: Meal, onDone: () -> Unit) = viewModelScope.launch {
        when (mode) {
            PickerMode.LOG -> log.add(requireNotNull(date), meal, food.id, amount)
            PickerMode.PLAN -> plans.addItem(plans.ensureActive(planName), meal, food.id, amount.grams)
            PickerMode.INGREDIENT, PickerMode.BROWSE -> Unit
        }
        onDone()
    }

    fun addSaved(saved: SavedMeal, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(log.addSavedMeal(saved.id, requireNotNull(date), meal ?: Meal.SNACK))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerScreen(
    viewModel: FoodPickerViewModel,
    onBack: () -> Unit,
    onOpenFood: (String) -> Unit,
    onNewFood: () -> Unit,
    onNewRecipe: () -> Unit,
    onIngredient: (foodId: String, grams: Double) -> Unit,
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf<Food?>(null) }
    val mealName = viewModel.meal?.let { stringResource(it.label) }.orEmpty()
    val addedText = stringResource(R.string.food_added_to, "%s", mealName)
    val savedAddedText = stringResource(R.string.saved_meal_added, "%s", mealName)
    val title = when (viewModel.mode) {
        PickerMode.LOG -> stringResource(R.string.food_add_to, mealName)
        PickerMode.INGREDIENT -> stringResource(R.string.recipe_add_ingredient)
        PickerMode.PLAN -> stringResource(R.string.plan_add_to, mealName)
        PickerMode.BROWSE -> stringResource(R.string.foods_title)
    }
    val tabs = PickerTab.entries.filter { it != PickerTab.SAVED || viewModel.mode == PickerMode.LOG }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(FitIcons.Back, contentDescription = stringResource(R.string.action_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onSurface, navigationIconContentColor = colors.onSurface),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = FitSpacing.gutter, end = FitSpacing.gutter, bottom = FitSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s8),
        ) {
            item {
                FitTextField(
                    filter.query, { viewModel.setFilter(filter.copy(query = it.take(40))) },
                    label = stringResource(R.string.library_search), imeAction = ImeAction.Search,
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                    tabs.forEach { tab -> Pill(stringResource(tab.label), filter.tab == tab) { viewModel.setFilter(filter.copy(tab = tab, category = null)) } }
                }
            }
            if (filter.tab == PickerTab.ALL && filter.query.isEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                        FoodCategory.entries.forEach { c ->
                            Pill(c.nameFa, filter.category == c) { viewModel.setFilter(filter.copy(category = if (filter.category == c) null else c)) }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
                    FitTextButton(stringResource(R.string.food_new), onClick = onNewFood)
                    if (viewModel.mode != PickerMode.INGREDIENT) FitTextButton(stringResource(R.string.recipe_new), onClick = onNewRecipe)
                }
            }
            if (filter.tab == PickerTab.SAVED && filter.query.isEmpty()) {
                if (saved.isEmpty()) item { FitNote(stringResource(R.string.saved_meals_empty)) }
                items(saved, key = { "saved-" + it.id }) { s ->
                    SavedMealRow(s) {
                        viewModel.addSaved(s) { n -> scope.launch { snackbar.showSnackbar(savedAddedText.format(s.name) + " (" + app.fitos.personal.ui.Fmt.int(n) + ")") } }
                    }
                }
                return@LazyColumn
            }
            val list = results ?: return@LazyColumn
            if (list.isEmpty()) {
                item {
                    FitNote(
                        stringResource(
                            when {
                                filter.query.isNotEmpty() -> R.string.food_not_found
                                filter.tab == PickerTab.RECENT -> R.string.food_recent_empty
                                filter.tab == PickerTab.FAVORITES -> R.string.food_favorites_empty
                                else -> R.string.food_mine_empty
                            },
                        ),
                    )
                }
            }
            items(list, key = { it.id }) { food ->
                FoodRow(
                    food,
                    onClick = { if (viewModel.mode == PickerMode.BROWSE) onOpenFood(food.id) else picking = food },
                    onInfo = if (viewModel.mode == PickerMode.BROWSE) null else ({ onOpenFood(food.id) }),
                    onFavorite = { viewModel.toggleFavorite(food) },
                )
            }
            if (filter.tab == PickerTab.ALL && filter.query.isEmpty()) item { FitNote(stringResource(R.string.food_bank_gaps)) }
        }
    }

    picking?.let { food ->
        AmountSheet(
            food = food,
            initial = null,
            confirmLabel = stringResource(if (viewModel.mode == PickerMode.INGREDIENT) R.string.recipe_add_ingredient else R.string.food_add),
            meal = viewModel.meal.takeIf { viewModel.mode == PickerMode.LOG || viewModel.mode == PickerMode.PLAN },
            onDismiss = { picking = null },
            onConfirm = { amount, meal ->
                picking = null
                if (viewModel.mode == PickerMode.INGREDIENT) {
                    onIngredient(food.id, amount.grams)
                } else {
                    viewModel.add(food, amount, meal ?: viewModel.meal ?: Meal.SNACK) {
                        scope.launch { snackbar.showSnackbar(addedText.format(food.nameFa)) }
                    }
                }
            },
        )
    }
}

val PickerTab.label: Int
    get() = when (this) {
        PickerTab.ALL -> R.string.food_tab_all
        PickerTab.RECENT -> R.string.food_tab_recent
        PickerTab.FAVORITES -> R.string.library_favorites
        PickerTab.MINE -> R.string.food_tab_mine
        PickerTab.RECIPES -> R.string.food_tab_recipes
        PickerTab.SAVED -> R.string.saved_meals_title
    }

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = FitTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (selected) {
            { Icon(FitIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface, labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.onPrimaryContainer, selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = colors.outline, selectedBorderColor = colors.primary),
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

@Composable
fun FoodRow(food: Food, onClick: () -> Unit, onInfo: (() -> Unit)?, onFavorite: () -> Unit) {
    val colors = FitTheme.colors
    FitCard(contentPadding = PaddingValues(start = FitSpacing.s12, end = FitSpacing.s4, top = FitSpacing.s4, bottom = FitSpacing.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = FitSpacing.s4),
                verticalArrangement = Arrangement.spacedBy(FitSpacing.s2),
            ) {
                Text(food.nameFa, style = MaterialTheme.typography.titleSmall)
                // Accuracy is only called out when it is not the reference bank's.
                Text(
                    listOfNotNull(
                        stringResource(R.string.per_100g, kcalText(food.per100g.kcal)),
                        stringResource(R.string.macro_p_short, gramsText(food.per100g.proteinG)),
                        if (food.accuracy != app.fitos.core.domain.nutrition.Accuracy.HIGH) stringResource(food.accuracy.label) else null,
                        if (food.isRecipe) stringResource(R.string.source_recipe) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (onInfo != null) IconButton(onClick = onInfo) { Icon(FitIcons.Info, contentDescription = stringResource(R.string.library_details, food.nameFa)) }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (food.isFavorite) FitIcons.StarFilled else FitIcons.Star,
                    contentDescription = stringResource(if (food.isFavorite) R.string.favorite_remove_named else R.string.favorite_add_named, food.nameFa),
                    tint = if (food.isFavorite) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SavedMealRow(saved: SavedMeal, onAdd: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdd).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(saved.name, style = MaterialTheme.typography.titleSmall)
                Text(saved.items.joinToString("، ") { it.food.nameFa }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
                Text(kcalText(saved.totals.kcal) + " · " + stringResource(R.string.macro_p_short, gramsText(saved.totals.proteinG)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
            }
            Icon(FitIcons.Plus, contentDescription = null, tint = colors.primary)
        }
    }
}
