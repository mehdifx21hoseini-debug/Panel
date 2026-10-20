package app.fitos.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val zone = ZoneId.of("Asia/Tehran")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(zone).toInstant(), zone)

    private fun render(mode: ThemeMode, name: String, openSettings: Boolean = false) {
        val settings = object : SettingsRepository {
            override val themeMode = MutableStateFlow(mode)
            override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
        }
        compose.setContent {
            val current by settings.themeMode.collectAsState()
            FitOsRoot(themeMode = current, settingsRepository = settings, clock = clock)
        }
        if (openSettings) compose.onNodeWithContentDescription("تنظیمات").performClick()
        compose.waitForIdle()
        val out = File("build/screens").apply { mkdirs() }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeLight() = render(ThemeMode.LIGHT, "home-light")
    @Test fun homeDark() = render(ThemeMode.DARK, "home-dark")
    @Test fun settingsLight() = render(ThemeMode.LIGHT, "settings-light", openSettings = true)
    @Test fun settingsDark() = render(ThemeMode.DARK, "settings-dark", openSettings = true)
}
