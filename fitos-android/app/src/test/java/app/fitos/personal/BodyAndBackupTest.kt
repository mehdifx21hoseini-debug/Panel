package app.fitos.personal

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
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
import app.fitos.core.domain.backup.BackupError
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.personal.feature.backup.BackupViewModel
import app.fitos.personal.ui.FitOsRoot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BodyAndBackupTest {

    @get:Rule val compose = createComposeRule()

    private val container = TestApp.container()

    private fun launch(startWithSetup: Boolean = false) {
        if (!startWithSetup) TestApp.completeSetup(container)
        compose.setContent {
            val mode by container.settingsRepository.themeMode.collectAsState(ThemeMode.LIGHT)
            FitOsRoot(themeMode = mode, container = container, startWithSetup = startWithSetup)
        }
    }

    private fun openBody() {
        compose.waitForText("سلام، آرش")
        compose.onNode(hasText("بدن") and isSelectable()).performClick()
    }

    @Test
    fun body_logWeight_showsLatestAndAsksForMoreDataForChart() {
        launch()
        openBody()
        compose.tapText("ثبت وزن")
        compose.field("وزن").performTextClearance()
        compose.field("وزن").performTextInput("89.4")
        compose.onNodeWithText("ناشتا").performScrollTo().performClick()
        compose.onNodeWithText("ذخیره").performScrollTo().performClick()
        compose.waitForText("۸۹٫۴")
        // Setup weigh-in (90) and this one are on the same day: one chart day only.
        compose.waitForDescription("برای نمودار، دست‌کم ۳ روز وزن‌کشی در این بازه لازم است. تا الان: ۱ روز.").assertExists()
        compose.waitForText("برای سرعت تغییر، دست‌کم ۶ روز وزن‌کشی در دو هفته لازم است.").assertExists()
        runBlocking {
            val latest = container.data.weights.latest.first()!!
            assertEquals(89.4, latest.weightKg, 1e-9)
            assertEquals(app.fitos.core.domain.body.WeightCondition.FASTED, latest.condition)
        }
    }

    @Test
    fun body_withThreeWeeks_showsChartRateAndWeeklyAverages() {
        TestApp.addWeights(container, 21)
        launch()
        openBody()
        compose.waitFor(hasText("کیلوگرم در هفته", substring = true)).assertExists()
        compose.waitForDescription("نمودار وزن", substring = true).assertExists()
        compose.waitForText("میانگین هفتگی").assertIsDisplayed()
    }

    @Test
    fun body_editAndDeleteWithUndo() {
        TestApp.addWeights(container, 3)
        launch()
        openBody()
        // Newest history row is the setup weigh-in today: 90 kg.
        compose.waitForText("همه‌ی ثبت‌ها")
        compose.waitFor(hasText("سه‌شنبه ۲۸ مهر", substring = true)).performClick()
        compose.waitForText("ویرایش وزن")
        compose.onNodeWithText("حذف").performScrollTo().performClick()
        compose.waitForText("وزن حذف شد.")
        runBlocking { assertEquals(90.8, container.data.weights.latest.first()!!.weightKg, 1e-9) }
        compose.onNodeWithText("بازگردانی").performClick()
        compose.waitUntil(5_000) { runBlocking { container.data.weights.latest.first()!!.weightKg == 90.0 } }
    }

    @Test
    fun measurements_logFromBodyTab() {
        launch()
        openBody()
        compose.tapText("ثبت اندازه‌ها")
        compose.field("دور شکم (روی ناف)").performTextInput("110")
        compose.field("بازو").performScrollTo().performTextInput("5") // out of range
        compose.onNodeWithText("ذخیره").performClick()
        compose.waitForText("عددی بین ۱۰ تا ۲۵۰ سانتی‌متر وارد کن.")
        compose.field("بازو").performTextClearance()
        compose.onNodeWithText("ذخیره").performClick()
        compose.waitForText("۱۱۰ سانتی‌متر").assertIsDisplayed()
    }

    @Test
    fun home_quickWeightLog() {
        launch()
        compose.waitForText("سلام، آرش")
        compose.tapText("ثبت وزن")
        compose.field("وزن").performTextClearance()
        compose.field("وزن").performTextInput("88.8")
        compose.onNodeWithText("ذخیره").performScrollTo().performClick()
        compose.waitForText("۸۸٫۸")
    }

    @Test
    fun welcome_offersRestoreWithoutTheBackupPart() {
        launch(startWithSetup = true)
        compose.onNodeWithText("بازیابی از فایل پشتیبان").performClick()
        compose.waitForText("انتخاب فایل پشتیبان").assertIsDisplayed()
        compose.onAllNodesWithText("ساخت فایل پشتیبان").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun settings_backupRowShowsNoBackupYet() {
        launch()
        compose.waitForDescription("تنظیمات").performClick()
        compose.waitForText("هنوز پشتیبانی نساخته‌ای.").performScrollTo().performClick()
        compose.waitForText("ساخت فایل پشتیبان").assertExists()
    }

    @Test
    fun backupViewModel_encryptedRoundTripIntoAnotherPhone() {
        TestApp.completeSetup(container)
        TestApp.addWeights(container, 10)
        val vm = BackupViewModel(container.data.backup, container.settingsRepository, TestApp.clock)
        assertEquals("FitOS-1405-07-28.fitbak", vm.suggestedFileName())

        vm.setPassword("کوتاه")
        assertFalse(vm.readyToExport())
        vm.setPassword("یک رمز خوب و بلند")
        vm.setRepeat("یک رمز متفاوت")
        assertFalse(vm.readyToExport())
        vm.setRepeat("یک رمز خوب و بلند")
        assertTrue(vm.readyToExport())

        val file = ByteArrayOutputStream()
        vm.export({ file }, "FitOS-1405-07-28.fitbak")
        awaitMain { vm.state.value.exportedName != null || vm.state.value.exportError != null }
        assertEquals("FitOS-1405-07-28.fitbak", vm.state.value.exportedName)
        assertNotNull(runBlocking { container.settingsRepository.lastBackupAt.first() })

        // A fresh phone: pick the file, give a wrong then the right password, restore.
        val phone = TestApp.container()
        val restoreVm = BackupViewModel(phone.data.backup, phone.settingsRepository, TestApp.clock)
        val bytes = file.toByteArray()
        restoreVm.pick { ByteArrayInputStream(bytes) }
        awaitMain { restoreVm.state.value.pendingEncrypted != null }
        restoreVm.setRestorePassword("اشتباه")
        restoreVm.unlock()
        awaitMain { restoreVm.state.value.restoreError != null }
        assertEquals(BackupError.WRONG_PASSWORD_OR_DAMAGED, restoreVm.state.value.restoreError)
        restoreVm.setRestorePassword("یک رمز خوب و بلند")
        restoreVm.unlock()
        awaitMain { restoreVm.state.value.staged != null }
        assertEquals(11, restoreVm.state.value.staged!!.summary.counts["weight_entry"])
        restoreVm.restore()
        awaitMain { restoreVm.state.value.restored }
        assertNull(restoreVm.state.value.restoreError)
        runBlocking {
            assertEquals(11, phone.data.weights.all.first().size)
            assertEquals("آرش", phone.data.profiles.profile.first()!!.displayName)
        }
    }
}
