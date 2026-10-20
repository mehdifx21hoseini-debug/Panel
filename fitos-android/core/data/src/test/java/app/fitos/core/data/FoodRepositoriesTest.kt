package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomFoodLogRepository
import app.fitos.core.data.repository.RoomFoodRepository
import app.fitos.core.data.repository.RoomMealPlanRepository
import app.fitos.core.data.repository.RoomSavedMealRepository
import app.fitos.core.domain.nutrition.Accuracy
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.FoodInput
import app.fitos.core.domain.nutrition.FoodLibrary
import app.fitos.core.domain.nutrition.FoodPortion
import app.fitos.core.domain.nutrition.FoodSource
import app.fitos.core.domain.nutrition.FoodState
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.Nutrients
import app.fitos.core.domain.nutrition.RecipeInput
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FoodRepositoriesTest {

    private lateinit var db: FitDatabase
    private val clock = TestData.clockAt(TestData.today)
    private val today = TestData.today
    private lateinit var foods: RoomFoodRepository
    private lateinit var log: RoomFoodLogRepository
    private lateinit var saved: RoomSavedMealRepository
    private lateinit var plans: RoomMealPlanRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FitDatabase::class.java).allowMainThreadQueries().build()
        foods = RoomFoodRepository(db, clock)
        log = RoomFoodLogRepository(db, clock)
        saved = RoomSavedMealRepository(db, clock)
        plans = RoomMealPlanRepository(db, clock)
    }

    @After fun close() = db.close()

    /** A label food: 30 g serving = 120 kcal, 6 g protein, 15 g carbs, 4 g fat, no fibre on the label. */
    private suspend fun bar() = foods.addCustom(
        FoodInput(
            "  بیسکویت پروتئینی ", null, "برند", 30.0, Nutrients(120.0, 6.0, 15.0, 4.0, null),
            FoodState.NOT_APPLICABLE, FoodSource.LABEL, listOf(FoodPortion("یک بسته", 30.0)),
        ),
    )

    private suspend fun rice() = foods.addCustom(
        FoodInput("برنج", null, null, 100.0, Nutrients(130.0, 2.7, 28.2, 0.3, 0.4), FoodState.COOKED, FoodSource.USER, emptyList()),
    )

    @Test fun theBuiltInBankIsSeededOnceAndKeepsFavourites() = runBlocking {
        val all = foods.all.first()
        assertEquals(FoodLibrary.all.size, all.count { it.builtinKey != null })
        all.firstOrNull { it.builtinKey != null }?.let { f ->
            assertEquals(FoodSource.USDA, f.source)
            assertEquals(Accuracy.HIGH, f.accuracy)
            assertTrue(f.sourceRef!!.startsWith("FDC "))
            foods.setFavorite(f.id, true)
            // Next app start: the bank is checked again without losing the favourite or the id.
            val again = RoomFoodRepository(db, clock).all.first().single { it.builtinKey == f.builtinKey }
            assertEquals(f.id, again.id)
            assertTrue(again.isFavorite)
        }
        Unit
    }

    @Test fun ownFoodsAreStoredPer100g() = runBlocking {
        val f = bar()
        assertEquals("بیسکویت پروتئینی", f.nameFa)
        assertEquals(400.0, f.per100g.kcal!!, 1e-9)
        assertEquals(20.0, f.per100g.proteinG!!, 1e-9)
        assertNull(f.per100g.fiberG)
        assertEquals(Accuracy.MEDIUM, f.accuracy)
        assertEquals(listOf(FoodPortion("یک بسته", 30.0)), f.portions)
        foods.deleteCustom(f.id)
        assertTrue(foods.all.first().none { it.id == f.id })
    }

    @Test fun logsCopyNumbersSoEditingAFoodKeepsThePast() = runBlocking {
        val f = bar()
        val id = log.add(today, Meal.SNACK, f.id, Amount.of(f.portions.single(), 2.0))
        var day = log.day(today).first()
        assertEquals(240.0, day.single().nutrients.kcal!!, 1e-9)
        assertEquals("یک بسته", day.single().portionLabel)

        // The label changes later: yesterday's entry must not.
        foods.updateCustom(f.id, FoodInput("بیسکویت پروتئینی", null, null, 30.0, Nutrients(150.0, 6.0, 15.0, 4.0, null), FoodState.NOT_APPLICABLE, FoodSource.LABEL, emptyList()))
        assertEquals(240.0, log.day(today).first().single().nutrients.kcal!!, 1e-9)

        // Halving the amount scales the copied numbers.
        log.update(id, Meal.BREAKFAST, Amount(30.0))
        day = log.day(today).first()
        assertEquals(120.0, day.single().nutrients.kcal!!, 1e-9)
        assertEquals(Meal.BREAKFAST, day.single().meal)

        log.delete(id)
        assertTrue(log.day(today).first().isEmpty())
        log.restore(id)
        assertEquals(1, log.day(today).first().size)
    }

    @Test fun dayTotalsFlagMissingFibre() = runBlocking {
        val b = bar()
        val r = rice()
        log.add(today, Meal.LUNCH, r.id, Amount(200.0))
        log.add(today, Meal.SNACK, b.id, Amount(30.0))
        val t = log.totals(today.minusDays(1), today).first().getValue(today)
        assertEquals(260.0 + 120.0, t.kcal, 1e-9)
        assertEquals(0.8, t.fiberG, 1e-9)
        assertTrue(t.fiberMissing)
        assertFalse(t.kcalMissing)
        assertEquals(2, t.count)
        assertNull(log.totals(today.minusDays(1), today).first()[today.minusDays(1)])
    }

    @Test fun copyingADayOrAMeal() = runBlocking {
        val r = rice()
        val b = bar()
        val yesterday = today.minusDays(1)
        log.add(yesterday, Meal.LUNCH, r.id, Amount(150.0))
        log.add(yesterday, Meal.SNACK, b.id, Amount(30.0))
        assertEquals(1, log.copy(yesterday, today, Meal.LUNCH))
        assertEquals(listOf(Meal.LUNCH), log.day(today).first().map { it.meal })
        assertEquals(2, log.copy(yesterday, today))
        assertEquals(3, log.day(today).first().size)
        assertEquals(2, log.day(yesterday).first().size)
    }

    @Test fun savedMealsAddEverythingInOneGo() = runBlocking {
        val r = rice()
        val b = bar()
        log.add(today, Meal.LUNCH, r.id, Amount(200.0))
        log.add(today, Meal.LUNCH, b.id, Amount.of(b.portions.single(), 1.0))
        val id = saved.saveFrom(today, Meal.LUNCH, " ناهار همیشگی ")
        val meal = saved.all.first().single()
        assertEquals("ناهار همیشگی", meal.name)
        assertEquals(2, meal.items.size)
        assertEquals(380.0, meal.totals.kcal, 1e-9)

        val tomorrow = today.plusDays(1)
        assertEquals(2, log.addSavedMeal(id, tomorrow, Meal.DINNER))
        val added = log.day(tomorrow).first()
        assertTrue(added.all { it.meal == Meal.DINNER })
        assertEquals("یک بسته", added.last().portionLabel)
        saved.delete(id)
        assertTrue(saved.all.first().isEmpty())
    }

    @Test fun recipesWorkOutTheirNumbersFromIngredients() = runBlocking {
        val r = rice()
        val b = bar()
        val id = foods.saveRecipe(null, RecipeInput("غذای آزمایشی", servings = 2.0, cookedWeightG = 400.0, ingredients = listOf(r.id to 300.0, b.id to 30.0)))
        val recipe = foods.food(id).first()!!
        assertTrue(recipe.isRecipe)
        assertEquals(FoodSource.RECIPE, recipe.source)
        // (390 + 120) kcal in 400 g of finished dish.
        assertEquals(510.0 / 4, recipe.per100g.kcal!!, 1e-9)
        assertNull(recipe.per100g.fiberG) // the label food has no fibre data
        assertEquals(Accuracy.MEDIUM, recipe.accuracy)
        assertEquals(200.0, recipe.portions.single().grams, 1e-9)
        assertEquals(2, foods.ingredients(id).first().size)

        // Editing replaces the ingredients and the numbers.
        foods.saveRecipe(id, RecipeInput("غذای آزمایشی", 1.0, null, listOf(r.id to 100.0)))
        val edited = foods.food(id).first()!!
        assertEquals(130.0, edited.per100g.kcal!!, 1e-9)
        assertEquals(1, foods.ingredients(id).first().size)
        assertEquals(100.0, edited.portions.single().grams, 1e-9)
    }

    @Test fun theMealPlanCanBeBuiltFromADay() = runBlocking {
        val r = rice()
        val b = bar()
        val planId = plans.ensureActive("برنامه")
        assertEquals(planId, plans.ensureActive("دیگری"))
        plans.addItem(planId, Meal.BREAKFAST, b.id, 30.0)
        plans.addItem(planId, Meal.DINNER, r.id, 200.0, DayOfWeek.FRIDAY)
        log.add(today, Meal.LUNCH, r.id, Amount(250.0))
        plans.replaceFromDay(planId, today)
        val plan = plans.active.first()!!
        // Every-day items replaced by today's lunch; the Friday-only item stays.
        assertEquals(listOf(Meal.DINNER, Meal.LUNCH), plan.items.map { it.meal })
        assertEquals(1, plan.itemsOn(today).size)
        val lunch = plan.items.single { it.meal == Meal.LUNCH }
        plans.updateItem(lunch.id, Meal.LUNCH, 180.0)
        assertEquals(180.0, plans.active.first()!!.items.single { it.meal == Meal.LUNCH }.grams, 1e-9)
        plans.removeItem(lunch.id)
        assertEquals(1, plans.active.first()!!.items.size)
    }
}
