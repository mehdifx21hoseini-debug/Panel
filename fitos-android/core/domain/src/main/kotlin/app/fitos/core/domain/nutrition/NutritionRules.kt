package app.fitos.core.domain.nutrition

import java.time.LocalDate
import kotlin.math.abs

/** Accepted ranges for own foods and logged amounts. */
object FoodRules {
    const val NAME_MAX = 60
    val KCAL_PER_100G = 0.0..900.0
    val MACRO_PER_100G = 0.0..100.0
    val BASIS_G = 1.0..1000.0
    val LOG_G = 0.5..5000.0
    val PORTION_G = 0.5..2000.0
    val SERVINGS = 0.5..100.0

    enum class Problem { NAME, BASIS, KCAL, PROTEIN, CARBS, FAT, FIBER, MACRO_SUM }

    fun check(input: FoodInput): Set<Problem> = buildSet {
        if (input.nameFa.isBlank() || input.nameFa.trim().length > NAME_MAX) add(Problem.NAME)
        if (input.basisGrams !in BASIS_G) add(Problem.BASIS)
        val p = if (input.basisGrams in BASIS_G) input.per100g else return@buildSet
        if (p.kcal == null || p.kcal !in KCAL_PER_100G) add(Problem.KCAL)
        if (p.proteinG == null || p.proteinG !in MACRO_PER_100G) add(Problem.PROTEIN)
        if (p.carbsG == null || p.carbsG !in MACRO_PER_100G) add(Problem.CARBS)
        if (p.fatG == null || p.fatG !in MACRO_PER_100G) add(Problem.FAT)
        if (p.fiberG != null && p.fiberG !in MACRO_PER_100G) add(Problem.FIBER)
        // Protein, carbohydrate and fat cannot weigh more than the food itself (fibre is part of carbs).
        if ((p.proteinG ?: 0.0) + (p.carbsG ?: 0.0) + (p.fatG ?: 0.0) > 100.5) add(Problem.MACRO_SUM)
    }

    /**
     * True when the calories differ from 4·protein + 4·carbs + 9·fat by more than 20 % (and 40 kcal):
     * often a typo, sometimes alcohol, polyols or rounding on the label. Shown as a warning only.
     */
    fun energyMismatch(n: Nutrients): Boolean {
        val kcal = n.kcal ?: return false
        val expected = 4 * (n.proteinG ?: return false) + 4 * (n.carbsG ?: return false) + 9 * (n.fatG ?: return false)
        val diff = abs(kcal - expected)
        return diff > 40 && diff > 0.2 * maxOf(kcal, expected)
    }
}

/** Nutrients of a dish made of other foods. */
object RecipeMath {
    data class Result(val totalWeightG: Double, val per100g: Nutrients, val total: Nutrients) {
        fun servingGrams(servings: Double): Double = totalWeightG / servings
    }

    /**
     * Totals are the sums of the ingredients; per 100 g divides by the cooked weight when given
     * (water lost or gained in cooking), otherwise by the raw weight. A nutrient is unknown when
     * any ingredient lacks it.
     */
    fun compute(ingredients: List<RecipeIngredient>, cookedWeightG: Double?): Result {
        val parts = ingredients.map { it.food.nutrientsFor(it.grams) }
        fun sum(pick: (Nutrients) -> Double?): Double? = if (parts.any { pick(it) == null }) null else parts.sumOf { pick(it)!! }
        val total = Nutrients(sum { it.kcal }, sum { it.proteinG }, sum { it.carbsG }, sum { it.fatG }, sum { it.fiberG })
        val weight = cookedWeightG ?: ingredients.sumOf { it.grams }
        val per100 = if (weight > 0) total * (100.0 / weight) else Nutrients(null, null, null, null, null)
        return Result(weight, per100, total)
    }
}

/** Planned versus eaten, per meal and for the day. */
data class PlanComparison(val planned: Map<Meal, NutrientTotals>, val eaten: Map<Meal, NutrientTotals>) {
    val plannedDay: NutrientTotals get() = planned.values.fold(NutrientTotals.EMPTY, NutrientTotals::plus)
    val eatenDay: NutrientTotals get() = eaten.values.fold(NutrientTotals.EMPTY, NutrientTotals::plus)

    companion object {
        fun of(plan: MealPlan, date: LocalDate, entries: List<FoodLogEntry>): PlanComparison {
            val items = plan.itemsOn(date)
            return PlanComparison(
                planned = Meal.entries.associateWith { meal -> NutrientTotals.of(items.filter { it.meal == meal }.map { it.food.nutrientsFor(it.grams) }) },
                eaten = Meal.entries.associateWith { meal -> NutrientTotals.of(entries.filter { it.meal == meal }.map { it.nutrients }) },
            )
        }
    }
}
