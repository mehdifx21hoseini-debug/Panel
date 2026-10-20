package app.fitos.core.domain.nutrition

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionRulesTest {

    private fun food(id: String, kcal: Double?, p: Double?, c: Double?, f: Double?, fiber: Double?, accuracy: Accuracy = Accuracy.HIGH) = Food(
        id, null, id, null, null, Nutrients(kcal, p, c, f, fiber), null, null, FoodState.RAW, FoodSource.USER, null, accuracy, null,
        false, null, null, false, emptyList(),
    )

    private val rice = food("rice", 365.0, 7.1, 80.0, 0.7, 1.3)
    private val oil = food("oil", 884.0, 0.0, 0.0, 100.0, null)

    @Test fun nutrientsScaleWithGrams() {
        val n = rice.nutrientsFor(150.0)
        assertEquals(547.5, n.kcal!!, 1e-9)
        assertEquals(10.65, n.proteinG!!, 1e-9)
        assertNull(oil.nutrientsFor(10.0).fiberG)
    }

    @Test fun totalsCountKnownValuesAndFlagMissingOnes() {
        val t = NutrientTotals.of(listOf(rice.nutrientsFor(100.0), oil.nutrientsFor(10.0)))
        assertEquals(365.0 + 88.4, t.kcal, 1e-9)
        assertEquals(1.3, t.fiberG, 1e-9)
        assertTrue(t.fiberMissing)
        assertFalse(t.kcalMissing)
        assertEquals(2, t.count)
        val sum = t + NutrientTotals.of(listOf(rice.nutrientsFor(100.0)))
        assertEquals(818.4, sum.kcal, 1e-9)
        assertTrue(sum.fiberMissing)
    }

    @Test fun recipesDivideByTheCookedWeight() {
        // 200 g dry rice + 10 g oil, cooked to 500 g.
        val r = RecipeMath.compute(listOf(RecipeIngredient(rice, 200.0), RecipeIngredient(oil, 10.0)), cookedWeightG = 500.0)
        assertEquals(730.0 + 88.4, r.total.kcal!!, 1e-9)
        assertEquals((730.0 + 88.4) / 5, r.per100g.kcal!!, 1e-9)
        assertEquals(125.0, r.servingGrams(4.0), 1e-9)
        // Oil has no fibre data, so the dish has none either rather than a too-low number.
        assertNull(r.per100g.fiberG)
        // Without a cooked weight the raw weight is used.
        assertEquals(210.0, RecipeMath.compute(listOf(RecipeIngredient(rice, 200.0), RecipeIngredient(oil, 10.0)), null).totalWeightG, 1e-9)
    }

    @Test fun ownFoodsAreValidatedAndLabelServingsConvertedTo100g() {
        // A label: 30 g serving = 120 kcal, 3 g protein, 20 g carbs, 3 g fat.
        val label = FoodInput("بیسکویت", null, null, 30.0, Nutrients(120.0, 3.0, 20.0, 3.0, null), FoodState.NOT_APPLICABLE, FoodSource.LABEL, emptyList())
        assertEquals(400.0, label.per100g.kcal!!, 1e-9)
        assertEquals(10.0, label.per100g.proteinG!!, 1e-9)
        assertEquals(Accuracy.MEDIUM, label.accuracy)
        assertTrue(FoodRules.check(label).isEmpty())

        assertTrue(FoodRules.Problem.NAME in FoodRules.check(label.copy(nameFa = " ")))
        assertTrue(FoodRules.Problem.KCAL in FoodRules.check(label.copy(atBasis = label.atBasis.copy(kcal = null))))
        // 30 g that would hold 40 g of carbs is impossible.
        assertTrue(FoodRules.Problem.MACRO_SUM in FoodRules.check(label.copy(atBasis = label.atBasis.copy(carbsG = 40.0))))
        assertEquals(Accuracy.LOW, label.copy(source = FoodSource.ESTIMATE).accuracy)
    }

    @Test fun energyMismatchIsAWarningForLargeGaps() {
        assertFalse(FoodRules.energyMismatch(Nutrients(365.0, 7.1, 80.0, 0.7, null)))
        // 100 kcal written instead of 400.
        assertTrue(FoodRules.energyMismatch(Nutrients(100.0, 10.0, 66.0, 10.0, null)))
        assertFalse(FoodRules.energyMismatch(Nutrients(100.0, null, 66.0, 10.0, null)))
    }

    @Test fun planComparisonUsesEveryDayAndWeekdayItems() {
        val plan = MealPlan(
            "p", "plan", true,
            listOf(
                MealPlanItem("1", null, Meal.LUNCH, rice, 100.0),
                MealPlanItem("2", DayOfWeek.FRIDAY, Meal.DINNER, oil, 10.0),
            ),
        )
        val tuesday = LocalDate.of(2026, 10, 20)
        assertEquals(1, plan.itemsOn(tuesday).size)
        assertEquals(2, plan.itemsOn(tuesday.plusDays(3)).size)
        val eaten = listOf(FoodLogEntry("e", tuesday, Meal.LUNCH, "rice", "rice", 150.0, null, null, rice.nutrientsFor(150.0), Instant.EPOCH, null))
        val c = PlanComparison.of(plan, tuesday, eaten)
        assertEquals(365.0, c.planned.getValue(Meal.LUNCH).kcal, 1e-9)
        assertEquals(547.5, c.eaten.getValue(Meal.LUNCH).kcal, 1e-9)
        assertEquals(0, c.planned.getValue(Meal.DINNER).count)
        assertEquals(547.5, c.eatenDay.kcal, 1e-9)
    }

    @Test fun portionsTurnIntoGrams() {
        val a = Amount.of(FoodPortion("پیمانه", 158.0), 1.5)
        assertEquals(237.0, a.grams, 1e-9)
        assertEquals("پیمانه", a.portionLabel)
        assertEquals(1.5, a.portionQuantity!!, 1e-9)
    }

    @Test fun theFoodBankIsComplete() {
        val all = FoodLibrary.all
        assertTrue(all.size >= 200)
        assertEquals(all.size, all.map { it.key }.toSet().size)
        assertEquals(all.size, all.map { it.fdcId }.toSet().size)
        all.forEach { f ->
            assertTrue("${f.key} name", f.nameFa.isNotBlank() && !f.nameFa.contains('ي') && !f.nameFa.contains('ك'))
            assertTrue("${f.key} values", f.kcal >= 0 && f.proteinG >= 0 && f.carbsG >= 0 && f.fatG >= 0)
            assertTrue("${f.key} macros", f.proteinG + f.carbsG + f.fatG <= 100.5)
            f.portions.forEach { p -> assertTrue("${f.key} portion", p.grams > 0 && p.label.isNotBlank()) }
        }
        // Spot check against USDA FDC 168878: cooked long-grain white rice.
        val rice = FoodLibrary.byKey.getValue("rice_white_long_cooked")
        assertEquals(168878, rice.fdcId)
        assertEquals(130.0, rice.kcal, 1e-9)
        assertEquals(2.69, rice.proteinG, 1e-9)
        assertEquals(158.0, rice.portions.single().grams, 1e-9)
    }
}
