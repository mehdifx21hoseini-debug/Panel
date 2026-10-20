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

    private fun render(name: String, theme: ThemeMode = ThemeMode.LIGHT, setupDone: Boolean = true, steps: () -> Unit = {}) {
        val container = TestApp.container(theme)
        if (setupDone) TestApp.completeSetup(container)
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
    @Test fun profileEdit() = render("22-profile-edit") {
        compose.waitForDescription("تنظیمات").performClick()
        click("مشخصات و هدف")
        compose.field("قد")
    }
}
