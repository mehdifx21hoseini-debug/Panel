package app.fitos.core.domain.nutrition

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

enum class FoodState(val code: String) {
    RAW("raw"),
    COOKED("cooked"),
    NOT_APPLICABLE("not_applicable");

    companion object {
        fun fromCode(code: String): FoodState = entries.firstOrNull { it.code == code } ?: NOT_APPLICABLE
    }
}

/** Where the numbers come from; shown next to every food so estimates never pass as exact data. */
enum class FoodSource(val code: String) {
    /** USDA FoodData Central, SR Legacy (public domain). */
    USDA("usda"),
    /** Copied from a package label by the user. */
    LABEL("label"),
    /** Entered by the user from another source. */
    USER("user"),
    /** The user's own estimate. */
    ESTIMATE("estimate"),
    /** Calculated from a recipe's ingredients. */
    RECIPE("recipe");

    companion object {
        fun fromCode(code: String): FoodSource = entries.firstOrNull { it.code == code } ?: USER
    }
}

enum class Accuracy(val code: String) {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromCode(code: String): Accuracy = entries.firstOrNull { it.code == code } ?: LOW
    }
}

enum class Meal(val code: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    SNACK("snack");

    companion object {
        fun fromCode(code: String): Meal = entries.firstOrNull { it.code == code } ?: SNACK

        /** The meal something eaten at [time] most likely belongs to. */
        fun at(time: LocalTime): Meal = when {
            time < LocalTime.of(11, 0) -> BREAKFAST
            time < LocalTime.of(16, 0) -> LUNCH
            time < LocalTime.of(18, 30) -> SNACK
            else -> DINNER
        }
    }
}

/** Groups of the built-in food bank (own foods and recipes have none). */
enum class FoodCategory(val code: String, val nameFa: String) {
    GRAINS("grains", "غلات و نان"),
    LEGUMES("legumes", "حبوبات"),
    MEAT("meat", "گوشت قرمز"),
    POULTRY("poultry", "مرغ و بوقلمون"),
    SEAFOOD("seafood", "ماهی و میگو"),
    EGGS("eggs", "تخم‌مرغ"),
    DAIRY("dairy", "لبنیات"),
    VEGETABLES("vegetables", "سبزیجات"),
    FRUITS("fruits", "میوه"),
    NUTS_DRIED("nuts_dried", "آجیل و خشکبار"),
    FATS("fats", "روغن و چربی"),
    SWEETS("sweets", "شیرینی و تنقلات"),
    DRINKS("drinks", "نوشیدنی"),
    CONDIMENTS("condiments", "چاشنی و سس"),
    SUPPLEMENTS("supplements", "مکمل");

    companion object {
        fun fromCode(code: String): FoodCategory? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Energy and macros of an amount of food. Null = no reliable data: it is never counted as zero,
 * and totals say when a part is missing.
 */
data class Nutrients(
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
) {
    operator fun times(factor: Double) = Nutrients(kcal?.times(factor), proteinG?.times(factor), carbsG?.times(factor), fatG?.times(factor), fiberG?.times(factor))

    companion object {
        val ZERO = Nutrients(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/** Sums of logged amounts, with a flag per nutrient when some entries had no data for it. */
data class NutrientTotals(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val kcalMissing: Boolean,
    val proteinMissing: Boolean,
    val carbsMissing: Boolean,
    val fatMissing: Boolean,
    val fiberMissing: Boolean,
    val count: Int,
) {
    val anyMissing: Boolean get() = kcalMissing || proteinMissing || carbsMissing || fatMissing || fiberMissing

    operator fun plus(o: NutrientTotals) = NutrientTotals(
        kcal + o.kcal, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG, fiberG + o.fiberG,
        kcalMissing || o.kcalMissing, proteinMissing || o.proteinMissing, carbsMissing || o.carbsMissing,
        fatMissing || o.fatMissing, fiberMissing || o.fiberMissing, count + o.count,
    )

    companion object {
        val EMPTY = NutrientTotals(0.0, 0.0, 0.0, 0.0, 0.0, false, false, false, false, false, 0)

        fun of(items: List<Nutrients>) = NutrientTotals(
            kcal = items.sumOf { it.kcal ?: 0.0 },
            proteinG = items.sumOf { it.proteinG ?: 0.0 },
            carbsG = items.sumOf { it.carbsG ?: 0.0 },
            fatG = items.sumOf { it.fatG ?: 0.0 },
            fiberG = items.sumOf { it.fiberG ?: 0.0 },
            kcalMissing = items.any { it.kcal == null },
            proteinMissing = items.any { it.proteinG == null },
            carbsMissing = items.any { it.carbsG == null },
            fatMissing = items.any { it.fatG == null },
            fiberMissing = items.any { it.fiberG == null },
            count = items.size,
        )
    }
}

/** A household unit, e.g. "1 cup = 158 g". */
data class FoodPortion(val label: String, val grams: Double)

data class Food(
    val id: String,
    val builtinKey: String?,
    val nameFa: String,
    val nameEn: String?,
    val brand: String?,
    val per100g: Nutrients,
    val sugarPer100g: Double?,
    val sodiumMgPer100g: Double?,
    val state: FoodState,
    val source: FoodSource,
    /** E.g. "FDC 168878" for USDA foods. */
    val sourceRef: String?,
    val accuracy: Accuracy,
    val category: FoodCategory?,
    val isRecipe: Boolean,
    val recipeServings: Double?,
    val recipeCookedWeightG: Double?,
    val isFavorite: Boolean,
    val portions: List<FoodPortion>,
) {
    val isCustom: Boolean get() = builtinKey == null

    fun nutrientsFor(grams: Double): Nutrients = per100g * (grams / 100.0)
}

/** What the user enters for an own food. Values are per [basisGrams] (100 g, or one serving from a label). */
data class FoodInput(
    val nameFa: String,
    val nameEn: String?,
    val brand: String?,
    val basisGrams: Double,
    val atBasis: Nutrients,
    val state: FoodState,
    val source: FoodSource,
    val portions: List<FoodPortion>,
) {
    val per100g: Nutrients get() = atBasis * (100.0 / basisGrams)

    /** A package label is as good as the manufacturer's numbers; own estimates are rough. */
    val accuracy: Accuracy
        get() = when (source) {
            FoodSource.USDA -> Accuracy.HIGH
            FoodSource.LABEL, FoodSource.USER -> Accuracy.MEDIUM
            FoodSource.ESTIMATE, FoodSource.RECIPE -> Accuracy.LOW
        }
}

data class RecipeIngredient(val food: Food, val grams: Double)

data class RecipeInput(
    val nameFa: String,
    val servings: Double,
    /** Weight of the finished dish; null = sum of the raw ingredients. */
    val cookedWeightG: Double?,
    val ingredients: List<Pair<String, Double>>,
)

/** One logged amount. The nutrients were copied when it was logged, so later food edits keep the past. */
data class FoodLogEntry(
    val id: String,
    val day: LocalDate,
    val meal: Meal,
    val foodId: String?,
    val foodName: String,
    val grams: Double,
    val portionLabel: String?,
    val portionQuantity: Double?,
    val nutrients: Nutrients,
    val loggedAt: Instant,
    val note: String?,
)

/** How much of a food: grams, or a number of one of its portions. */
data class Amount(val grams: Double, val portionLabel: String? = null, val portionQuantity: Double? = null) {
    companion object {
        fun of(portion: FoodPortion, quantity: Double) = Amount(portion.grams * quantity, portion.label, quantity)
    }
}

data class SavedMealItem(val food: Food, val amount: Amount)

data class SavedMeal(val id: String, val name: String, val meal: Meal?, val items: List<SavedMealItem>) {
    val totals: NutrientTotals get() = NutrientTotals.of(items.map { it.food.nutrientsFor(it.amount.grams) })
}

data class MealPlanItem(val id: String, val weekday: DayOfWeek?, val meal: Meal, val food: Food, val grams: Double)

data class MealPlan(val id: String, val name: String, val isActive: Boolean, val items: List<MealPlanItem>) {
    /** Items planned for [date]: every-day items plus those for its weekday. */
    fun itemsOn(date: LocalDate): List<MealPlanItem> = items.filter { it.weekday == null || it.weekday == date.dayOfWeek }
}

interface FoodRepository {
    /** Built-in bank (created on first read), own foods and recipes; deleted own foods excluded. */
    val all: Flow<List<Food>>

    /** Foods logged lately, newest first, without repeats. */
    val recent: Flow<List<Food>>

    fun food(id: String): Flow<Food?>
    fun ingredients(recipeId: String): Flow<List<RecipeIngredient>>

    suspend fun addCustom(input: FoodInput): Food
    suspend fun updateCustom(id: String, input: FoodInput)
    /** Hides an own food or recipe; logs keep their copied numbers. */
    suspend fun deleteCustom(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)

    /** Creates ([id] null) or replaces a recipe; its nutrients per 100 g are worked out from the ingredients. */
    suspend fun saveRecipe(id: String?, input: RecipeInput): String
}

interface FoodLogRepository {
    fun day(date: LocalDate): Flow<List<FoodLogEntry>>

    /** Totals per day in [from]..[to], for Home and reports. */
    fun totals(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, NutrientTotals>>

    suspend fun add(date: LocalDate, meal: Meal, foodId: String, amount: Amount, note: String? = null): String
    suspend fun update(id: String, meal: Meal, amount: Amount)
    suspend fun delete(id: String)
    suspend fun restore(id: String)

    /** Copies the entries of [from] (one meal, or all) to [to]; returns how many were added. */
    suspend fun copy(from: LocalDate, to: LocalDate, meal: Meal? = null, toMeal: Meal? = null): Int
    suspend fun addSavedMeal(savedMealId: String, date: LocalDate, meal: Meal): Int
}

interface SavedMealRepository {
    val all: Flow<List<SavedMeal>>

    /** Saves the entries of one meal of [date] under [name]. */
    suspend fun saveFrom(date: LocalDate, meal: Meal, name: String): String
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
}

interface MealPlanRepository {
    val active: Flow<MealPlan?>

    /** Creates the plan if there is none and makes it active. */
    suspend fun ensureActive(name: String): String
    suspend fun addItem(planId: String, meal: Meal, foodId: String, grams: Double, weekday: DayOfWeek? = null): String
    suspend fun updateItem(itemId: String, meal: Meal, grams: Double)
    suspend fun removeItem(itemId: String)
    /** Replaces the plan's every-day items with what was eaten on [date]. */
    suspend fun replaceFromDay(planId: String, date: LocalDate)
}
