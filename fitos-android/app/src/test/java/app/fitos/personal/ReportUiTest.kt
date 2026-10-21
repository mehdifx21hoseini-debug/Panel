package app.fitos.personal

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.FoodInput
import app.fitos.core.domain.nutrition.FoodSource
import app.fitos.core.domain.nutrition.FoodState
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.Nutrients
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.TargetOrigin
import app.fitos.core.domain.profile.InitialSetup
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReportUiTest {

    @get:Rule val compose = createComposeRule()

    private val container = TestApp.container()
    private val data get() = container.data
    private val today = TestApp.today

    /** Last week: Saturday 10 to Friday 16 October; the calorie check looks at 26 September to 16 October. */
    private val lastWeek = LocalDate.of(2026, 10, 10)
    private val windowStart = LocalDate.of(2026, 9, 26)

    private fun launch() {
        compose.setContent { FitOsRoot(themeMode = ThemeMode.LIGHT, container = container, startWithSetup = false) }
        compose.waitForText("سلام، آرش")
    }

    /**
     * Three weeks of daily weigh-ins going down 0.3 kg a week and 2500 kcal logged every day, with a
     * 2500 kcal target set long before: slower than the chosen 20% pace, so the check suggests one step down.
     */
    private fun threeLoggedWeeks() = runBlocking {
        data.setup.completeSetup(
            InitialSetup(
                profile = TestApp.profile,
                currentWeightKg = 90.0,
                targetWeightKg = 80.0,
                goalReviewDate = today.plusWeeks(12),
                target = NutritionTarget(LocalDate.of(2026, 9, 1), 2500, 180, 285, 70, 35, 3000, 450, TargetOrigin.CALCULATED, null),
            ),
        )
        val meal = data.foods.addCustom(FoodInput("خوراک روزانه", null, null, 100.0, Nutrients(250.0, 18.0, 28.5, 7.0, 3.5), FoodState.COOKED, FoodSource.USER, emptyList()))
        generateSequence(windowStart) { it.plusDays(1) }.takeWhile { it < lastWeek.plusDays(7) }.forEachIndexed { i, day ->
            data.weights.add(90.6 - 0.3 / 7 * i, day, LocalTime.of(7, 30), null, null)
            data.foodLog.add(day, Meal.LUNCH, meal.id, Amount(1000.0))
        }
    }

    @Test
    fun lastWeeksReport_opensFromHome_andAppliesTheCalorieStep() {
        threeLoggedWeeks()
        launch()
        compose.waitForText("گزارش هفته آماده است")
        compose.tapText("دیدن گزارش")

        compose.waitForText("مهم‌ترین پیشرفت هفته")
        compose.waitForText("میانگین وزن این هفته ۰٫۳ کیلوگرم کمتر از هفته‌ی قبل بود.").assertExists()
        compose.waitForText("روزهای ثبت‌شده: ۷ از ۷").assertExists()
        compose.waitForText("پیشنهاد هدف کالری: ۲٬۳۵۰ به جای ۲٬۵۰۰").assertExists()

        compose.tapText("اعمال هدف تازه")
        compose.waitForText("کالری روزانه: ۲٬۵۰۰ ← ۲٬۳۵۰")
        compose.waitForText("کربوهیدرات: ۲۸۵ ← ۲۵۰ گرم").assertExists()
        compose.tapText("اعمال")
        compose.waitForDescription("هدف تازه از امروز اعمال شد. دو هفته‌ی دیگر دوباره بررسی می‌شود.").assertExists()
        val next = runBlocking { data.targets.effectiveOn(today).first()!! }
        assertEquals(TargetOrigin.ADAPTIVE, next.origin)
        assertEquals(2350, next.kcal)
        assertEquals(180, next.proteinG)
        assertEquals(250, next.carbsG)
        assertEquals(today, next.effectiveFrom)
        // The new target is too new for another step.
        compose.waitFor(hasText("دست‌کم ۱۴ روز از آخرین تغییر هدف (اکنون: ۰ روز)", substring = true)).assertExists()
        // Opened, so Home no longer announces it.
        assertEquals(lastWeek, runBlocking { container.settingsRepository.reportSeenWeek.first() })
    }

    @Test
    fun aCalorieSuggestionCanBeSetAsideForTheWeek() {
        threeLoggedWeeks()
        launch()
        compose.tapText("دیدن گزارش")
        compose.tapText("فعلاً نه")
        compose.waitForText("این پیشنهاد را برای این هفته کنار گذاشتی. هفته‌ی بعد دوباره بررسی می‌شود.").assertExists()
        assertEquals(lastWeek, runBlocking { container.settingsRepository.adaptiveDismissedWeek.first() })
        assertEquals(2500, runBlocking { data.targets.effectiveOn(today).first()!!.kcal })
    }

    @Test
    fun anEmptyWeekSaysSo_andTheCurrentWeekShowsItsDaysSoFar() {
        TestApp.completeSetup(container)
        launch()
        compose.onNode(hasText("بدن") and isSelectable()).performClick()
        compose.tapText("گزارش‌های هفتگی")
        compose.waitForDescription("برای این هفته هنوز چیزی ثبت نشده است. گزارش فقط از داده‌های ثبت‌شده ساخته می‌شود.").assertExists()
        compose.waitForDescription("هفته‌ی بعد").performClick()
        compose.waitForText("هفته‌ی جاری، تا امروز. مهم‌ترین پیشرفت و پیشنهادها جمعه آماده می‌شوند.").assertExists()
        compose.waitForText("میانگین این هفته: ۹۰ کیلوگرم (۱ وزن‌کشی)").assertExists()
    }
}
