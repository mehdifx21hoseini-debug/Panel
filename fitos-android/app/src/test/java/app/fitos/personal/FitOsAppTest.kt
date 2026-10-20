package app.fitos.personal

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.ui.FitOsRoot
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeSettingsRepository : SettingsRepository {
    override val themeMode = MutableStateFlow(ThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FitOsAppTest {

    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Tehran")
    // Tuesday 20 Oct 2026 = سه‌شنبه ۲۸ مهر ۱۴۰۵
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 20).atTime(9, 0).atZone(zone).toInstant(), zone)
    private val settings = FakeSettingsRepository()

    private fun launch() = compose.setContent {
        val mode by settings.themeMode.collectAsState()
        FitOsRoot(themeMode = mode, settingsRepository = settings, clock = clock)
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable())

    @Test
    fun home_showsRealJalaliDate_andNothingLogged() {
        launch()
        compose.onNodeWithText("سلام").assertIsDisplayed()
        compose.onNodeWithText("سه‌شنبه ۲۸ مهر ۱۴۰۵").assertIsDisplayed()
        compose.onNodeWithContentDescription("پیشرفت امروز ۰٪. هنوز چیزی ثبت نشده است.").assertExists()
        // Week strip starts on Saturday 25 Mehr and reads every day aloud.
        compose.onNodeWithContentDescription("شنبه ۲۵ مهر ۱۴۰۵").assertExists()
        compose.onNodeWithContentDescription("جمعه ۱ آبان ۱۴۰۵").assertExists()
    }

    @Test
    fun bottomNavigation_opensEachTab() {
        launch()
        tab("تمرین").performClick()
        compose.onNodeWithText("برنامه و جلسه تمرین").assertIsDisplayed()
        tab("تغذیه").performClick()
        compose.onNodeWithText("غذا و کالری").assertIsDisplayed()
        tab("بدن").performClick()
        compose.onNodeWithText("وزن و اندازه‌ها").assertIsDisplayed()
    }

    @Test
    fun settings_savesThemeChoice() {
        launch()
        compose.onNodeWithContentDescription("تنظیمات").performClick()
        compose.onNodeWithText("روشن").assertIsSelected()
        compose.onNodeWithText("تیره").performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, settings.themeMode.value)
        compose.onNodeWithText("بدون دسترسی به اینترنت").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("بازگشت").performClick()
        compose.onNodeWithText("سلام").assertIsDisplayed()
    }
}
