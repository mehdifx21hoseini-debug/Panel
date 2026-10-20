package app.fitos.personal.feature.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.fitos.core.designsystem.component.FitCard
import app.fitos.core.designsystem.component.FitNote
import app.fitos.core.designsystem.component.FitPrimaryButton
import app.fitos.core.designsystem.component.FitSecondaryButton
import app.fitos.core.designsystem.component.FitTextButton
import app.fitos.core.designsystem.component.FitTextField
import app.fitos.core.designsystem.icon.FitIcons
import app.fitos.core.designsystem.theme.FitSpacing
import app.fitos.core.designsystem.theme.FitTheme
import app.fitos.core.designsystem.theme.numeralStyle
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.nutrition.Food
import app.fitos.core.domain.nutrition.FoodLogEntry
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.FoodRepository
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.MealPlan
import app.fitos.core.domain.nutrition.MealPlanRepository
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.nutrition.PlanComparison
import app.fitos.core.domain.nutrition.SavedMealRepository
import app.fitos.core.domain.time.DateFormats
import app.fitos.personal.R
import app.fitos.personal.ui.Fmt
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NutritionDay(
    val loaded: Boolean = false,
    val date: LocalDate = LocalDate.MIN,
    val entries: List<FoodLogEntry> = emptyList(),
    val target: NutritionTarget? = null,
    val plan: MealPlan? = null,
) {
    val totals: NutrientTotals get() = NutrientTotals.of(entries.map { it.nutrients })
    fun meal(m: Meal) = entries.filter { it.meal == m }
    val comparison: PlanComparison? get() = plan?.takeIf { it.itemsOn(date).isNotEmpty() }?.let { PlanComparison.of(it, date, entries) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    private val foods: FoodRepository,
    private val log: FoodLogRepository,
    private val savedMeals: SavedMealRepository,
    targets: NutritionTargetRepository,
    plans: MealPlanRepository,
    today: LocalDate,
) : ViewModel() {
    private val _date = MutableStateFlow(today)
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val day: StateFlow<NutritionDay> = _date.flatMapLatest { d ->
        combine(log.day(d), targets.effectiveOn(d), plans.active) { entries, target, plan -> NutritionDay(true, d, entries, target, plan) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionDay())

    fun show(date: LocalDate) {
        _date.value = date
    }

    suspend fun food(id: String): Food? = foods.food(id).first()

    fun update(entry: FoodLogEntry, amount: app.fitos.core.domain.nutrition.Amount, meal: Meal) = viewModelScope.launch { log.update(entry.id, meal, amount) }
    fun delete(entry: FoodLogEntry) = viewModelScope.launch { log.delete(entry.id) }
    fun restore(entry: FoodLogEntry) = viewModelScope.launch { log.restore(entry.id) }

    /** Copies a whole day, or one meal, from the day before; returns how many entries came over. */
    fun copyFromYesterday(meal: Meal?, onDone: (Int) -> Unit) = viewModelScope.launch {
        val d = _date.value
        onDone(log.copy(d.minusDays(1), d, meal))
    }

    fun saveMeal(meal: Meal, name: String) = viewModelScope.launch { savedMeals.saveFrom(_date.value, meal, name) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    today: LocalDate,
    onAdd: (date: LocalDate, meal: Meal) -> Unit,
    onOpenTargets: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenFoods: () -> Unit,
    onOpenSavedMeals: () -> Unit,
) {
    val state by viewModel.day.collectAsStateWithLifecycle()
    val colors = FitTheme.colors
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Pair<FoodLogEntry, Food>?>(null) }
    var saving by remember { mutableStateOf<Meal?>(null) }
    val deletedText = stringResource(R.string.food_deleted)
    val undoText = stringResource(R.string.action_undo)
    val copiedText = stringResource(R.string.food_copied)
    val nothingText = stringResource(R.string.food_copy_nothing)

    fun copied(count: Int) {
        scope.launch { snackbar.showSnackbar(if (count > 0) copiedText.format(PersianNumbers.format(count)) else nothingText) }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_nutrition), style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenFoods) { Icon(FitIcons.Book, contentDescription = stringResource(R.string.foods_title)) }
                    IconButton(onClick = onOpenTargets) { Icon(FitIcons.Target, contentDescription = stringResource(R.string.home_targets_title)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background, titleContentColor = colors.onSurface, actionIconContentColor = colors.onSurface),
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        val date = state.date
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = FitSpacing.gutter, end = FitSpacing.gutter, bottom = FitSpacing.s24),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            item { DateBar(date, today, viewModel::show) }
            item { TotalsCard(state.totals, state.target) }
            if (state.entries.isEmpty()) {
                item {
                    FitSecondaryButton(
                        stringResource(R.string.food_copy_day),
                        onClick = { viewModel.copyFromYesterday(null, ::copied) },
                        icon = FitIcons.Copy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Meal.entries.forEach { meal ->
                item(key = meal.code) {
                    MealCard(
                        meal = meal,
                        entries = state.meal(meal),
                        onAdd = { onAdd(date, meal) },
                        onEdit = { entry -> scope.launch { entry.foodId?.let { viewModel.food(it) }?.let { editing = entry to it } } },
                        onCopy = { viewModel.copyFromYesterday(meal, ::copied) },
                        onSave = { saving = meal },
                    )
                }
            }
            state.comparison?.let { c -> item { PlanCard(c, onOpenPlan) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(FitSpacing.s8)) {
                    if (state.comparison == null) FitSecondaryButton(stringResource(R.string.plan_title), onClick = onOpenPlan, icon = FitIcons.Calendar, modifier = Modifier.fillMaxWidth())
                    FitSecondaryButton(stringResource(R.string.saved_meals_title), onClick = onOpenSavedMeals, icon = FitIcons.Star, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    editing?.let { (entry, food) ->
        AmountSheet(
            food = food,
            initial = app.fitos.core.domain.nutrition.Amount(entry.grams, entry.portionLabel, entry.portionQuantity),
            confirmLabel = stringResource(R.string.action_save),
            meal = entry.meal,
            onDismiss = { editing = null },
            onConfirm = { amount, meal -> viewModel.update(entry, amount, meal ?: entry.meal); editing = null },
            onDelete = {
                editing = null
                viewModel.delete(entry)
                scope.launch {
                    val result = snackbar.showSnackbar(deletedText.format(entry.foodName), undoText, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(entry)
                }
            },
        )
    }
    saving?.let { meal ->
        SaveMealSheet(
            defaultName = stringResource(meal.label),
            onDismiss = { saving = null },
            onSave = { name ->
                viewModel.saveMeal(meal, name)
                saving = null
            },
        )
    }
}

/** Name a meal to reuse it later; starts with the meal's own name ("ناهار"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveMealSheet(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = FitTheme.colors.surface) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = FitSpacing.gutter, vertical = FitSpacing.s8),
            verticalArrangement = Arrangement.spacedBy(FitSpacing.s12),
        ) {
            Text(stringResource(R.string.saved_meal_save_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FitTextField(name, { name = it.take(40) }, stringResource(R.string.saved_meal_name))
            FitPrimaryButton(stringResource(R.string.action_save), onClick = { onSave(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DateBar(date: LocalDate, today: LocalDate, onShow: (LocalDate) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShow(date.minusDays(1)) }) { Icon(FitIcons.ChevronLeft, contentDescription = stringResource(R.string.day_previous)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DateFormats.longDate(date), style = MaterialTheme.typography.titleMedium)
            if (date != today) FitTextButton(stringResource(R.string.days_ago_today), onClick = { onShow(today) })
        }
        IconButton(onClick = { onShow(date.plusDays(1)) }, enabled = date < today) { Icon(FitIcons.ChevronRight, contentDescription = stringResource(R.string.day_next)) }
    }
}

/** Eaten against the target that applied that day; unknown values are called out, never hidden. */
@Composable
private fun TotalsCard(t: NutrientTotals, target: NutritionTarget?) {
    val colors = FitTheme.colors
    FitCard(hero = true) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.module_calories), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                target?.let {
                    val left = it.kcal - t.kcal.roundToInt()
                    Text(
                        if (left >= 0) stringResource(R.string.kcal_left, Fmt.int(left)) else stringResource(R.string.kcal_over, Fmt.int(-left)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.int(t.kcal.roundToInt()), style = numeralStyle(32), color = colors.calories.ink)
                target?.let { Text(stringResource(R.string.of_target, Fmt.int(it.kcal)), style = MaterialTheme.typography.bodySmall, color = colors.subtle) }
            }
        }
        target?.let { Bar(t.kcal / it.kcal, colors.calories.fill, stringResource(R.string.module_calories)) }
        Row(horizontalArrangement = Arrangement.spacedBy(FitSpacing.s12)) {
            Macro(stringResource(R.string.field_protein), t.proteinG, target?.proteinG, colors.protein.fill, Modifier.weight(1f))
            Macro(stringResource(R.string.field_carbs), t.carbsG, target?.carbsG, colors.sleep.fill, Modifier.weight(1f))
            Macro(stringResource(R.string.field_fat), t.fatG, target?.fatG, colors.water.fill, Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.fiber_total, gramsText(t.fiberG)) + (target?.fiberG?.let { " / " + Fmt.int(it) } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (t.count > 0 && t.anyMissing) FitNote(stringResource(R.string.totals_incomplete))
    }
}

@Composable
private fun Macro(label: String, grams: Double, target: Int?, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitSpacing.s4)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FitTheme.colors.subtle)
        Text(
            Fmt.int(grams.roundToInt()) + (target?.let { " / " + Fmt.int(it) } ?: "") + " " + stringResource(R.string.unit_g),
            style = MaterialTheme.typography.labelLarge,
        )
        target?.let { Bar(grams / it, color, label) }
    }
}

@Composable
private fun Bar(fraction: Double, color: androidx.compose.ui.graphics.Color, label: String) {
    val colors = FitTheme.colors
    val percent = (fraction * 100).roundToInt()
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceContainerHigh)
            .semantics { contentDescription = "$label ${PersianNumbers.format(percent)}٪" },
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat()).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (fraction > 1.1) colors.error else color))
    }
}

@Composable
private fun MealCard(meal: Meal, entries: List<FoodLogEntry>, onAdd: () -> Unit, onEdit: (FoodLogEntry) -> Unit, onCopy: () -> Unit, onSave: () -> Unit) {
    val colors = FitTheme.colors
    var menu by remember { mutableStateOf(false) }
    val totals = NutrientTotals.of(entries.map { it.nutrients })
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(meal.label), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (entries.isNotEmpty()) {
                    Text(
                        kcalText(totals.kcal) + " · " + stringResource(R.string.macro_p_short, gramsText(totals.proteinG)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.subtle,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(FitIcons.MoreV, contentDescription = stringResource(R.string.meal_more, stringResource(meal.label))) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.food_copy_meal)) }, onClick = { menu = false; onCopy() })
                    if (entries.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.saved_meal_save_title)) }, onClick = { menu = false; onSave() })
                }
            }
            IconButton(onClick = onAdd) { Icon(FitIcons.Plus, contentDescription = stringResource(R.string.food_add_to, stringResource(meal.label))) }
        }
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = e.foodId != null) { onEdit(e) }
                    .heightIn(min = 48.dp).padding(vertical = FitSpacing.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FitSpacing.s8),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.foodName, style = MaterialTheme.typography.bodyLarge)
                    Text(amountText(e.grams, e.portionLabel, e.portionQuantity), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(kcalText(e.nutrients.kcal), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.macro_p_short, gramsText(e.nutrients.proteinG)), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(c: PlanComparison, onOpenPlan: () -> Unit) {
    val colors = FitTheme.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_vs_eaten), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            FitTextButton(stringResource(R.string.plan_edit), onClick = onOpenPlan)
        }
        Row {
            Text("", Modifier.weight(1.4f))
            Text(stringResource(R.string.plan_planned), style = MaterialTheme.typography.labelMedium, color = colors.subtle, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.plan_eaten), style = MaterialTheme.typography.labelMedium, color = colors.subtle, modifier = Modifier.weight(1f))
        }
        (Meal.entries.map { stringResource(it.label) to (c.planned.getValue(it) to c.eaten.getValue(it)) } +
            listOf(stringResource(R.string.plan_day_total) to (c.plannedDay to c.eatenDay))).forEach { (label, pair) ->
            val (planned, eaten) = pair
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                Text(Fmt.int(planned.kcal.roundToInt()) + " · " + Fmt.int(planned.proteinG.roundToInt()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(Fmt.int(eaten.kcal.roundToInt()) + " · " + Fmt.int(eaten.proteinG.roundToInt()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.plan_columns_hint), style = MaterialTheme.typography.bodySmall, color = colors.subtle)
    }
}
