package app.fitos.personal

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.Food
import app.fitos.core.domain.nutrition.FoodInput
import app.fitos.core.domain.nutrition.FoodPortion
import app.fitos.core.domain.nutrition.FoodSource
import app.fitos.core.domain.nutrition.FoodState
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.Nutrients
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class NutritionUiTest {

    @get:Rule val compose = createComposeRule()

    private val container = TestApp.container()
    private val data get() = container.data
    private val today = TestApp.today

    private fun launch() {
        TestApp.completeSetup(container)
        compose.setContent { FitOsRoot(themeMode = ThemeMode.LIGHT, container = container, startWithSetup = false) }
        compose.waitForText("سلام، آرش")
    }

    private fun openNutrition() {
        compose.onNode(hasText("تغذیه") and isSelectable()).performClick()
        compose.waitForText("صبحانه")
    }

    private fun result(name: String) = hasText(name) and hasClickAction() and !hasSetTextAction()

    /** 100 g cooked rice = 130 kcal, 2.7 g protein (entered as an own food so the test does not depend on the bank). */
    private fun rice(): Food = runBlocking {
        data.foods.addCustom(FoodInput("برنج کته", null, null, 100.0, Nutrients(130.0, 2.7, 28.2, 0.3, 0.4), FoodState.COOKED, FoodSource.USER, listOf(FoodPortion("کفگیر", 80.0))))
    }

    @Test
    fun ownFoodFromALabelIsCreatedAndLogged() {
        launch()
        openNutrition()
        compose.waitForDescription("افزودن به صبحانه").performClick()
        compose.waitForText("افزودن به صبحانه")
        compose.tapText("غذای جدید")
        compose.field("نام غذا").performTextInput("بیسکویت پروتئینی")
        // The label is per 30 g serving.
        compose.field("مقادیر برای").performTextClearance()
        compose.field("مقادیر برای").performTextInput("30")
        compose.field("کالری").performTextInput("120")
        compose.field("پروتئین").performTextInput("6")
        compose.field("کربوهیدرات").performTextInput("15")
        compose.field("چربی").performTextInput("4")
        compose.tapText("ذخیره")

        compose.waitForText("افزودن به صبحانه")
        compose.field("جست‌وجو").performTextInput("بیسکویت")
        compose.waitFor(result("بیسکویت پروتئینی")).performClick()
        compose.waitForText("برچسب بسته · دقت متوسط")
        compose.field("مقدار").performTextClearance()
        compose.field("مقدار").performTextInput("60")
        compose.waitForText("۲۴۰ کیلوکالری · پروتئین ۱۲ · کربوهیدرات ۳۰ · چربی ۸")
        compose.tapText("افزودن")
        compose.waitUntil(5_000) { runBlocking { data.foodLog.day(today).first().size == 1 } }
        val entry = runBlocking { data.foodLog.day(today).first().single() }
        assertEquals(Meal.BREAKFAST, entry.meal)
        assertEquals(240.0, entry.nutrients.kcal!!, 1e-9)

        compose.waitForDescription("بازگشت").performClick()
        compose.waitForText("بیسکویت پروتئینی")
        compose.waitForText("۲۴۰")
    }

    @Test
    fun portionsQuickEditAndUndoDelete() {
        val r = rice()
        runBlocking { data.foodLog.add(today, Meal.LUNCH, r.id, Amount.of(r.portions.single(), 2.0)) }
        launch()
        openNutrition()
        compose.waitForText("۲ کفگیر")
        compose.tapText("برنج کته")
        compose.waitForText("تعداد کفگیر")
        compose.field("تعداد کفگیر").performTextClearance()
        compose.field("تعداد کفگیر").performTextInput("3")
        compose.tapText("ذخیره")
        compose.waitForText("۳ کفگیر")
        assertEquals(312.0, runBlocking { data.foodLog.day(today).first().single().nutrients.kcal!! }, 1e-9)

        compose.tapText("برنج کته")
        compose.tapText("حذف")
        compose.waitForText("بازگردانی").performClick()
        compose.waitForText("۳ کفگیر")
        assertEquals(1, runBlocking { data.foodLog.day(today).first().size })
    }

    @Test
    fun copyYesterdayAndSaveAMeal() {
        val r = rice()
        runBlocking { data.foodLog.add(today.minusDays(1), Meal.LUNCH, r.id, Amount(200.0)) }
        launch()
        openNutrition()
        compose.tapText("کپی کل روز از دیروز")
        // The snackbar is not in the list; wait for it without the helper that scrolls lists.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("۱ قلم از دیروز کپی شد.")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForText("۲۰۰ گرم")

        compose.waitForDescription("گزینه‌های ناهار").performClick()
        compose.tapText("ذخیره به‌عنوان وعده")
        // The name starts as the meal's name.
        compose.waitFor(hasText("ناهار") and hasSetTextAction())
        compose.tapText("ذخیره")
        compose.waitUntil(5_000) { runBlocking { data.savedMeals.all.first().singleOrNull()?.name == "ناهار" } }

        // Add the saved meal to dinner in one tap.
        compose.waitForDescription("افزودن به شام").performClick()
        compose.tapText("وعده‌های ذخیره‌شده")
        compose.waitFor(hasText("ناهار") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.waitUntil(5_000) { runBlocking { data.foodLog.day(today).first().count { it.meal == Meal.DINNER } == 1 } }
    }

    @Test
    fun aRecipeIsBuiltFromIngredients() {
        val r = rice()
        launch()
        openNutrition()
        compose.waitForDescription("افزودن به ناهار").performClick()
        compose.tapText("دستور غذای جدید")
        compose.field("نام غذا").performTextInput("ته‌چین ساده")
        compose.field("تعداد سهم").performTextClearance()
        compose.field("تعداد سهم").performTextInput("2")
        compose.tapText("افزودن ماده")
        compose.field("جست‌وجو").performTextInput("برنج کته")
        compose.waitFor(result("برنج کته")).performClick()
        compose.tapText("گرم")
        compose.field("مقدار").performTextClearance()
        compose.field("مقدار").performTextInput("300")
        compose.onNode(hasText("افزودن ماده") and hasClickAction()).performClick()
        compose.waitForText("وزن هر سهم")
        compose.waitForText("۱۵۰ گرم")
        compose.tapText("ذخیره")
        compose.waitUntil(5_000) { runBlocking { data.foods.all.first().any { it.isRecipe } } }
        val recipe = runBlocking { data.foods.all.first().single { it.isRecipe } }
        assertEquals(130.0, recipe.per100g.kcal!!, 1e-9)
        assertEquals(150.0, recipe.portions.single().grams, 1e-9)
        assertTrue(r.id != recipe.id)
    }

    @Test
    fun planIsComparedWithWhatWasEaten() {
        val r = rice()
        runBlocking {
            val plan = data.mealPlans.ensureActive("برنامه")
            data.mealPlans.addItem(plan, Meal.LUNCH, r.id, 200.0)
            data.foodLog.add(today, Meal.LUNCH, r.id, Amount(300.0))
        }
        launch()
        openNutrition()
        compose.waitForText("برنامه در برابر مصرف")
        compose.waitForText("۲۶۰ · ۵")
        compose.waitForText("۳۹۰ · ۸")
    }

    @Test
    fun homeShowsCaloriesAndProteinFromTheLog() {
        val r = rice()
        runBlocking { data.foodLog.add(today, Meal.LUNCH, r.id, Amount(300.0)) }
        launch()
        // 390 kcal of the day's target; the plate meter counts it.
        compose.waitForText("۳۹۰")
        compose.waitForText("۸ گرم")
        compose.waitFor(hasContentDescription("پیشرفت امروز", substring = true))
    }

    @Test
    fun aBankFoodIsLoggedByHouseholdUnitWithItsSource() {
        launch()
        openNutrition()
        compose.waitForDescription("افزودن به ناهار").performClick()
        compose.field("جست‌وجو").performTextInput("برنج سفید")
        compose.waitFor(result("برنج سفید دانه‌بلند، پخته")).performClick()
        compose.waitForText("پخته · USDA (FDC 168878) · دقت بالا")
        // The household unit comes first: one cup = 158 g = 205 kcal.
        compose.waitForText("تعداد پیمانه")
        compose.waitForText("۲۰۵ کیلوکالری · پروتئین ۴٫۳ · کربوهیدرات ۴۴٫۶ · چربی ۰٫۴")
        compose.tapText("افزودن")
        compose.waitUntil(5_000) { runBlocking { data.foodLog.day(today).first().size == 1 } }
        val e = runBlocking { data.foodLog.day(today).first().single() }
        assertEquals(158.0, e.grams, 1e-9)
        assertEquals("پیمانه", e.portionLabel)
        assertEquals(205.4, e.nutrients.kcal!!, 1e-9)
    }
}
