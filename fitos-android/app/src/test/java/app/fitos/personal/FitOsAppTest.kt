package app.fitos.personal

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FitOsAppTest {

    @get:Rule val compose = createComposeRule()

    private val container = TestApp.container()

    private fun launch(startWithSetup: Boolean) = compose.setContent {
        val mode by container.settingsRepository.themeMode.collectAsState(ThemeMode.LIGHT)
        FitOsRoot(themeMode = mode, container = container, startWithSetup = startWithSetup)
    }

    private fun launchReady() {
        TestApp.completeSetup(container)
        launch(startWithSetup = false)
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())
    private fun field(label: String): SemanticsNodeInteraction = compose.field(label)
    private fun pick(menuLabel: String, option: String) = compose.pickFromMenu(menuLabel, option)
    private fun tapContinue() = compose.tapText("ادامه")

    @Test
    fun onboarding_fullFlow_savesEverythingAndOpensHome() {
        launch(startWithSetup = true)
        compose.onNodeWithText("به FitOS خوش آمدی").assertIsDisplayed()
        compose.onNodeWithText("شروع کنیم").performClick()

        field("اسم").performTextInput("آرش")
        compose.onNodeWithText("مرد").performClick()
        pick("روز", "۳۰")
        pick("ماه", "خرداد")
        pick("سال", "۱۳۷۵")
        field("قد").performTextInput("180")
        field("وزن امروز").scrollIfNeeded().performTextInput("90")
        compose.onNodeWithText("۱۸۰").assertExists() // typed Latin digits are shown as Persian
        tapContinue()

        compose.onNodeWithText("تمرین و فعالیت").assertIsDisplayed()
        tapContinue()

        compose.onNodeWithText("کاهش چربی").assertIsSelected()
        field("وزن هدف (اختیاری)").scrollIfNeeded().performTextInput("80")
        tapContinue()

        // Male, 30 y, 90 kg, 180 cm, moderate (4 sessions), 20% deficit, target 80 kg.
        compose.waitForText("هدف‌های روزانه").assertIsDisplayed()
        compose.onNodeWithText("۲۳۵۰").assertExists()
        compose.onNodeWithText("۱۴۵").assertExists()
        compose.onNodeWithText("ذخیره و شروع").performClick()

        compose.waitForText("سلام، آرش").assertIsDisplayed()
        compose.waitForText("۲٬۳۵۰").performScrollTo().assertIsDisplayed()
        runBlocking {
            assertNotNull(container.data.profiles.profile.first())
            assertEquals(90.0, container.data.weights.latest.first()!!.weightKg, 1e-9)
            assertEquals(80.0, container.data.goals.activeGoal.first()!!.targetWeightKg, 1e-9)
        }
    }

    @Test
    fun onboarding_errorsAppearAfterContinue_andUpdateWhileTyping() {
        launch(startWithSetup = true)
        compose.onNodeWithText("شروع کنیم").performClick()
        compose.onAllNodesWithText("این مورد لازم است.").assertCountEquals(0)
        tapContinue()
        // Sex, birth date, height and weight are missing.
        compose.onAllNodesWithText("این مورد لازم است.").assertCountEquals(4)
        field("قد").performTextInput("50")
        compose.onNodeWithText("قد را بین ۱۲۰ تا ۲۳۰ سانتی‌متر وارد کن.").assertExists()
        field("قد").performTextClearance()
        field("قد").performTextInput("180")
        compose.onAllNodesWithText("قد را بین ۱۲۰ تا ۲۳۰ سانتی‌متر وارد کن.").assertCountEquals(0)
        // Back returns to the welcome page without losing anything typed.
        compose.onNodeWithContentDescription("بازگشت").performClick()
        compose.onNodeWithText("شروع کنیم").performClick()
        compose.onNodeWithText("۱۸۰").assertExists()
    }

    @Test
    fun home_showsRealDate_targets_andGoal() {
        launchReady()
        compose.waitForText("سلام، آرش").assertIsDisplayed()
        compose.onNodeWithText("سه‌شنبه ۲۸ مهر ۱۴۰۵").assertIsDisplayed()
        compose.onNodeWithContentDescription("پیشرفت امروز ۰٪. هنوز چیزی ثبت نشده است.").assertExists()
        compose.onNodeWithContentDescription("شنبه ۲۵ مهر ۱۴۰۵").assertExists()
        compose.waitForText("هدف‌های امروز").performScrollTo().assertIsDisplayed()
        compose.waitForText("هدف: ۸۰ کیلوگرم").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("۱۰ کیلوگرم تا هدف").assertExists()
    }

    @Test
    fun bottomNavigation_opensEachTab() {
        launchReady()
        compose.waitForText("سلام، آرش")
        tab("تمرین").performClick()
        compose.onNodeWithText("برنامه و جلسه تمرین").assertIsDisplayed()
        tab("تغذیه").performClick()
        compose.onNodeWithText("غذا و کالری").assertIsDisplayed()
        tab("بدن").performClick()
        compose.waitForText("آخرین وزن").assertIsDisplayed()
    }

    @Test
    fun settings_savesThemeChoice() {
        launchReady()
        compose.waitForDescription("تنظیمات").performClick()
        compose.waitForText("روشن").performScrollTo().assertIsSelected()
        compose.onNodeWithText("تیره").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, (container.settingsRepository as FakeSettingsRepository).themeMode.value)
        compose.onNodeWithText("بدون دسترسی به اینترنت").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("بازگشت").performClick()
        compose.waitForText("سلام، آرش").assertIsDisplayed()
    }

    @Test
    fun targets_editStartsANewTargetToday() {
        launchReady()
        compose.tapText("ویرایش")
        compose.waitForText("از سه‌شنبه ۲۸ مهر ۱۴۰۵").assertIsDisplayed()
        field("کالری").performTextClearance()
        field("کالری").performTextInput("1800")
        compose.onNodeWithContentDescription("این مقدار از متابولیسم پایه‌ات (۱٬۸۸۰ کیلوکالری) کمتر است و توصیه نمی‌شود.").assertExists()
        field("کالری").performTextClearance()
        field("کالری").performTextInput("2200")
        compose.onNodeWithText("ذخیره").performClick()
        compose.waitForText("۲٬۲۰۰").performScrollTo().assertIsDisplayed()
        runBlocking {
            val saved = container.data.targets.effectiveOn(TestApp.today).first()!!
            assertEquals(2200, saved.kcal)
            assertEquals(app.fitos.core.domain.nutrition.TargetOrigin.MANUAL, saved.origin)
        }
    }

    @Test
    fun profileEdit_updatesGoal() {
        launchReady()
        compose.waitForDescription("تنظیمات").performClick()
        compose.tapText("مشخصات و هدف")
        field("وزن هدف (اختیاری)").scrollIfNeeded().performTextClearance()
        field("وزن هدف (اختیاری)").performTextInput("95")
        compose.onNodeWithText("ذخیره").performClick()
        // Losing fat towards a heavier target is rejected with a clear reason.
        compose.waitForText("برای کاهش چربی، وزن هدف باید کمتر از وزن فعلی باشد.").scrollIfNeeded().assertIsDisplayed()
        field("وزن هدف (اختیاری)").performTextClearance()
        field("وزن هدف (اختیاری)").performTextInput("85")
        compose.onNodeWithText("ذخیره").performClick()
        compose.waitForText("تنظیمات") // saved, back on Settings
        compose.onNodeWithContentDescription("بازگشت").performClick()
        compose.waitForText("هدف: ۸۵ کیلوگرم").performScrollTo().assertIsDisplayed()
    }
}
