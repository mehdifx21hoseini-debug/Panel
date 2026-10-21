package app.fitos.personal

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.VariantKind
import app.fitos.personal.ui.FitOsRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrainingUiTest {

    @get:Rule val compose = createComposeRule()

    private val container = TestApp.container()
    private val programs get() = container.data.programs

    private fun launch() {
        TestApp.completeSetup(container)
        compose.setContent {
            val mode by container.settingsRepository.themeMode.collectAsState(ThemeMode.LIGHT)
            FitOsRoot(themeMode = mode, container = container, startWithSetup = false)
        }
        compose.waitForText("سلام، آرش")
    }

    /** A library row; the search field holding the same text is not one. */
    private fun result(name: String) = hasText(name) and hasClickAction() and !hasSetTextAction()

    private fun withTemplate(): String = runBlocking { programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet()) }

    private fun active(): Program = runBlocking { programs.active.first()!! }

    private fun openTraining() {
        compose.onNode(hasText("تمرین") and isSelectable()).performClick()
    }

    /** Opens the first day of the template ("سینه و پشت") from the Training tab. */
    private fun openFirstDay() {
        openTraining()
        compose.waitFor(hasText("سینه و پشت") and hasClickAction())
        compose.onAllNodes(hasText("سینه و پشت") and hasClickAction())[0].performClick()
        compose.waitForText("پرس سینه با هالتر")
    }

    @Test
    fun training_createFromTemplate_showsNextDayHereAndOnHome() {
        launch()
        // Before any program, Home points to the Training tab.
        compose.waitForText("برنامه‌ی تمرینی").assertExists()
        openTraining()
        compose.tapText("ساخت از برنامه‌ی پیشنهادی")
        compose.waitForText("۵ روزه: بالاتنه، سرشانه و پشت").assertExists()
        compose.waitForText("جلسه‌ی بعدی").assertExists()

        val program = active()
        assertEquals(7, program.days.size)
        assertEquals(5, program.trainingDays.size)
        // No home equipment: the home version uses body-weight moves only.
        val homeKeys = program.days.first().variant(VariantKind.HOME)!!.exercises.map { it.exercise.builtinKey }
        assertEquals(listOf("push_up", "inverted_row", "pike_push_up", "bench_dip"), homeKeys)

        compose.onNode(hasText("خانه") and isSelectable()).performClick()
        compose.waitForText("جلسه‌ی بعدی: سینه و پشت").assertExists()
    }

    @Test
    fun dayEditor_editTargets_savesAndLogsChange() {
        withTemplate()
        launch()
        openFirstDay()
        compose.tapText("پرس سینه با هالتر")
        compose.waitForText("تعداد ست")
        compose.onAllNodes(hasContentDescription("زیاد کردن"))[0].performClick()
        compose.tapText("ذخیره")
        compose.waitUntil(5_000) { active().days.first().full.first().sets == 5 }

        val changes = runBlocking { programs.changes(active().id).first() }
        assertTrue(changes.any { "پرس سینه با هالتر" in it.summary })
    }

    @Test
    fun library_pickAddsExerciseToTheDay() {
        withTemplate()
        launch()
        openFirstDay()
        compose.tapText("افزودن حرکت")
        compose.waitForText("انتخاب حرکت")
        compose.field("جست‌وجو").performTextInput("جلو بازو هالتر")
        compose.waitFor(result("جلو بازو هالتر")).performClick()
        // Back on the day, the new exercise sits at the end with sensible default targets.
        compose.waitForText("سینه و پشت")
        compose.waitUntil(5_000) { active().days.first().full.last().exercise.builtinKey == "barbell_curl" }
        val added = active().days.first().full.last()
        assertEquals(3, added.sets)
        assertEquals(8, added.repMin)
        assertEquals(12, added.repMax)
    }

    @Test
    fun library_replaceKeepsTargets() {
        withTemplate()
        launch()
        openFirstDay()
        compose.tapText("سیم‌کش زیربغل")
        compose.tapText("جایگزینی حرکت")
        compose.waitForText("جایگزین با…")
        compose.field("جست‌وجو").performTextInput("بارفیکس دست باز")
        compose.waitFor(result("بارفیکس دست باز")).performClick()
        compose.waitUntil(5_000) { active().days.first().full[1].exercise.builtinKey != "lat_pulldown" }
        val replaced = active().days.first().full[1]
        assertEquals("pull_up", replaced.exercise.builtinKey)
        assertEquals(4, replaced.sets)
        assertEquals(8, replaced.repMin)
    }

    @Test
    fun minimalVariant_canBeRebuiltFromTheGymVersion() {
        withTemplate()
        launch()
        openFirstDay()
        compose.onNode(hasText("حداقلی") and isSelectable()).performClick()
        compose.waitForText("شنا سوئدی").assertExists()
        runBlocking {
            val day = active().days.first()
            day.variant(VariantKind.MINIMAL)!!.exercises.forEach { programs.removePlanned(it.id) }
        }
        compose.tapText("ساخت از روی نسخه‌ی باشگاه")
        compose.waitUntil(5_000) { active().days.first().variant(VariantKind.MINIMAL)?.exercises?.size == 6 }
    }

    @Test
    fun exerciseDetail_showsAnimationThatPausesOnTap() {
        launch()
        openTraining()
        compose.waitForDescription("کتابخانه‌ی حرکات").performClick()
        compose.waitFor(result("پرس سینه با هالتر")).performClick()
        val animation = compose.waitForDescription("نمایش سه‌بعدی حرکت پرس سینه با هالتر").scrollIfNeeded()
        animation.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "در حال پخش"))
        animation.performClick()
        animation.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "متوقف"))
        compose.waitForText("برای توقف یا پخش، روی نمایش بزن.").assertExists()
    }

    @Test
    fun customExercise_validateCreateFavoriteAndDelete() {
        launch()
        openTraining()
        compose.waitForDescription("کتابخانه‌ی حرکات").performClick()
        compose.tapText("ساخت حرکت جدید")
        compose.waitForText("حرکت جدید")
        compose.tapText("ذخیره")
        compose.waitForText("نام حرکت لازم است.").assertExists()
        compose.waitForText("دست‌کم یک عضله‌ی اصلی انتخاب کن.").assertExists()

        compose.field("نام حرکت").performTextInput("پرس سینه دستگاه همر")
        compose.onAllNodesWithText("سینه")[0].performScrollTo().performClick()
        compose.tapText("ذخیره")
        compose.waitForText("کتابخانه‌ی حرکات")

        compose.tapText("حرکت‌های من")
        compose.waitFor(result("پرس سینه دستگاه همر"))
        compose.waitForDescription("افزودن پرس سینه دستگاه همر به علاقه‌مندی‌ها").performClick()
        compose.waitForDescription("حذف پرس سینه دستگاه همر از علاقه‌مندی‌ها").assertExists()
        val mine = runBlocking { container.data.exercises.all.first().single { it.isCustom } }
        assertTrue(mine.isFavorite)

        compose.waitFor(result("پرس سینه دستگاه همر")).performClick()
        compose.tapText("حذف حرکت")
        compose.waitForText("از کتابخانه پنهان می‌شود.").assertExists()
        compose.onNode(hasText("حذف") and hasClickAction()).performClick()
        compose.waitForDescription("حرکتی با این مشخصات پیدا نشد. فیلترها را کم کن یا حرکت خودت را بساز.")
        runBlocking { assertTrue(container.data.exercises.all.first().none { it.isCustom }) }
    }

    @Test
    fun programEditor_addDayAndChangeLog() {
        withTemplate()
        launch()
        openTraining()
        compose.tapText("ویرایش برنامه")
        compose.tapText("افزودن روز تمرین")
        // A new day opens straight in its editor.
        compose.waitForText("روز ۸")
        compose.waitForText("افزودن حرکت").assertExists()
        assertNotNull(active().days.firstOrNull { it.name == "روز ۸" })
        compose.waitForDescription("بازگشت").performClick()
        compose.tapText("تاریخچه‌ی تغییرات")
        compose.waitForText("روز «روز ۸» اضافه شد.").assertExists()
    }
}
