package app.fitos.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the main screens in both themes to build/screens/ for a visual check (no emulator here). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenRenderTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(name: String, theme: ThemeMode = ThemeMode.LIGHT, setupDone: Boolean = true, steps: () -> Unit = {}) =
        renderWith(name, theme, setupDone, weights = 0, steps = steps)

    private fun renderWith(
        name: String,
        theme: ThemeMode = ThemeMode.LIGHT,
        setupDone: Boolean = true,
        weights: Int = 0,
        program: Boolean = false,
        steps: () -> Unit = {},
    ) {
        val container = TestApp.container(theme)
        if (setupDone) TestApp.completeSetup(container)
        if (weights > 0) TestApp.addWeights(container, weights)
        if (program) kotlinx.coroutines.runBlocking {
            container.data.programs.createFromTemplate(app.fitos.core.domain.training.ProgramTemplates.upperFocus5, emptySet())
        }
        compose.setContent { FitOsRoot(themeMode = theme, container = container, startWithSetup = !setupDone) }
        steps()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        val out = File("build/screens").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun click(text: String) = compose.tapText(text)
    private fun type(label: String, value: String) = compose.field(label).scrollIfNeeded().performTextInput(value)
    private fun pick(menu: String, option: String) = compose.pickFromMenu(menu, option)

    private fun fillAbout() {
        click("شروع کنیم")
        type("اسم", "آرش")
        click("مرد")
        pick("روز", "۳۰")
        pick("ماه", "خرداد")
        pick("سال", "۱۳۷۵")
        type("قد", "180")
        type("وزن امروز", "90")
    }

    @Test fun welcome() = render("01-welcome", setupDone = false)
    @Test fun welcomeDark() = render("01-welcome-dark", ThemeMode.DARK, setupDone = false)
    @Test fun aboutErrors() = render("02-about-errors", setupDone = false) { click("شروع کنیم"); click("ادامه") }
    @Test fun about() = render("02-about", setupDone = false) { fillAbout() }
    @Test fun activity() = render("03-activity", setupDone = false) { fillAbout(); click("ادامه") }
    @Test fun goal() = render("04-goal", setupDone = false) { fillAbout(); click("ادامه"); click("ادامه"); type("وزن هدف (اختیاری)", "80") }
    @Test fun targets() = render("05-targets", setupDone = false) {
        fillAbout(); click("ادامه"); click("ادامه"); type("وزن هدف (اختیاری)", "80"); click("ادامه")
    }
    @Test fun targetsDark() = render("05-targets-dark", ThemeMode.DARK, setupDone = false) {
        fillAbout(); click("ادامه"); click("ادامه"); type("وزن هدف (اختیاری)", "80"); click("ادامه")
    }
    @Test fun homeLight() = render("10-home") { compose.waitForText("هدف‌های امروز") }
    @Test fun homeLightScrolled() = render("10-home-scrolled") { compose.waitForText("هدف: ۸۰ کیلوگرم").performScrollTo() }
    @Test fun homeDark() = render("10-home-dark", ThemeMode.DARK) { compose.waitForText("هدف‌های امروز") }
    @Test fun settings() = render("20-settings") { compose.waitForDescription("تنظیمات").performClick() }
    @Test fun targetsEdit() = render("21-targets-edit") { click("ویرایش"); compose.waitForText("از سه‌شنبه ۲۸ مهر ۱۴۰۵") }
    @Test fun bodyEmptyGoal() = render("30-body-new") { compose.onNode(androidx.compose.ui.test.hasText("بدن") and androidx.compose.ui.test.isSelectable()).performClick(); compose.waitForText("آخرین وزن") }
    @Test fun bodyWithData() = renderWith("31-body-data", weights = 45) { compose.onNode(androidx.compose.ui.test.hasText("بدن") and androidx.compose.ui.test.isSelectable()).performClick(); compose.waitForText("آخرین وزن") }
    @Test fun bodyWithDataDark() = renderWith("31-body-data-dark", ThemeMode.DARK, weights = 45) { compose.onNode(androidx.compose.ui.test.hasText("بدن") and androidx.compose.ui.test.isSelectable()).performClick(); compose.waitForText("آخرین وزن") }
    @Test fun bodyScrolled() = renderWith("32-body-goal", weights = 45) {
        compose.onNode(androidx.compose.ui.test.hasText("بدن") and androidx.compose.ui.test.isSelectable()).performClick()
        compose.waitForText("هدف وزن")
    }
    @Test fun measurementsEntry() = render("34-measurements") {
        compose.onNode(androidx.compose.ui.test.hasText("بدن") and androidx.compose.ui.test.isSelectable()).performClick()
        click("ثبت اندازه‌ها")
        compose.field("دور شکم (روی ناف)")
    }
    @Test fun backup() = render("40-backup") {
        compose.waitForDescription("تنظیمات").performClick()
        click("پشتیبان‌گیری و بازیابی")
    }
    @Test fun profileEdit() = render("22-profile-edit") {
        compose.waitForDescription("تنظیمات").performClick()
        click("مشخصات و هدف")
        compose.field("قد")
    }

    private fun openTraining() = compose.onNode(androidx.compose.ui.test.hasText("تمرین") and androidx.compose.ui.test.isSelectable()).performClick()
    private fun openFirstDay() {
        openTraining()
        compose.waitFor(androidx.compose.ui.test.hasText("سینه و پشت") and androidx.compose.ui.test.hasClickAction())
        compose.onAllNodes(androidx.compose.ui.test.hasText("سینه و پشت") and androidx.compose.ui.test.hasClickAction())[0].performClick()
        compose.waitForText("پرس سینه با هالتر")
    }

    @Test fun trainingEmpty() = render("50-training-empty") { openTraining(); compose.waitForText("هنوز برنامه‌ای نداری") }
    @Test fun training() = renderWith("51-training", program = true) { openTraining(); compose.waitForText("جلسه‌ی بعدی") }
    @Test fun trainingDark() = renderWith("51-training-dark", ThemeMode.DARK, program = true) { openTraining(); compose.waitForText("جلسه‌ی بعدی") }
    @Test fun trainingScrolled() = renderWith("52-training-volume", program = true) {
        openTraining()
        compose.waitForText("ست‌های هر عضله در یک دور برنامه")
    }
    @Test fun dayEditor() = renderWith("53-day-editor", program = true) { openFirstDay() }
    @Test fun dayEditorDark() = renderWith("53-day-editor-dark", ThemeMode.DARK, program = true) { openFirstDay() }
    @Test fun dayEditorHome() = renderWith("54-day-home", program = true) {
        openFirstDay()
        compose.onNode(androidx.compose.ui.test.hasText("خانگی") and androidx.compose.ui.test.isSelectable()).performClick()
        compose.waitForText("شنا سوئدی")
    }
    @Test fun programEditor() = renderWith("55-program-editor", program = true) { openTraining(); click("ویرایش برنامه"); compose.waitForText("روزهای برنامه") }
    @Test fun library() = render("56-library") { openTraining(); compose.waitForDescription("کتابخانه‌ی حرکات").performClick(); compose.waitForText("۱۰۸ حرکت") }
    @Test fun libraryDark() = render("56-library-dark", ThemeMode.DARK) { openTraining(); compose.waitForDescription("کتابخانه‌ی حرکات").performClick(); compose.waitForText("۱۰۸ حرکت") }
    @Test fun libraryFiltered() = render("57-library-shoulders") {
        openTraining()
        compose.waitForDescription("کتابخانه‌ی حرکات").performClick()
        compose.waitForText("۱۰۸ حرکت")
        click("سرشانه")
    }
    @Test fun exerciseDetail() = render("58-exercise-detail") {
        openTraining()
        compose.waitForDescription("کتابخانه‌ی حرکات").performClick()
        compose.field("جست‌وجو").performTextInput("پرس سرشانه ایستاده با هالتر")
        compose.waitFor(androidx.compose.ui.test.hasText("پرس سرشانه ایستاده با هالتر") and androidx.compose.ui.test.hasClickAction() and !androidx.compose.ui.test.hasSetTextAction()).performClick()
        compose.waitForText("نحوه‌ی اجرا")
    }
    @Test fun exerciseForm() = render("59-exercise-form") {
        openTraining()
        compose.waitForDescription("کتابخانه‌ی حرکات").performClick()
        click("ساخت حرکت جدید")
        click("ذخیره")
    }
    @Test fun homeWithProgram() = renderWith("11-home-training", program = true) { compose.waitForText("جلسه‌ی بعدی: سینه و پشت") }
}
