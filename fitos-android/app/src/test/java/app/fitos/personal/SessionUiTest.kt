package app.fitos.personal

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.fitos.core.domain.settings.ThemeMode
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.SetInput
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutSession
import app.fitos.personal.feature.session.RestAlarm
import app.fitos.personal.ui.FitOsRoot
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class RecordingRestAlarm : RestAlarm {
    val scheduled = mutableListOf<Pair<Instant, String>>()
    var cancels = 0
    override fun schedule(endsAt: Instant, next: String) {
        scheduled += endsAt to next
    }
    override fun cancel() {
        cancels++
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SessionUiTest {

    @get:Rule val compose = createComposeRule()

    private val alarm = RecordingRestAlarm()
    private val container = TestApp.container(restAlarm = alarm)
    private val workouts get() = container.data.workouts
    private lateinit var programId: String
    private lateinit var chestDay: String

    private fun launch() {
        TestApp.completeSetup(container)
        runBlocking {
            programId = container.data.programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet())
            chestDay = container.data.programs.program(programId).first()!!.days.first().id
        }
        compose.setContent {
            val mode by container.settingsRepository.themeMode.collectAsState(ThemeMode.LIGHT)
            FitOsRoot(themeMode = mode, container = container, startWithSetup = false)
        }
        compose.waitForText("سلام، آرش")
    }

    private fun open(): WorkoutSession? = runBlocking { workouts.active.first() }

    private fun openTraining() = compose.onNode(hasText("تمرین") and isSelectable()).performClick()

    /** Starts the gym version of the next day from the Training tab. */
    private fun startFromTraining() {
        openTraining()
        compose.tapText("شروع تمرین")
        compose.waitForText("حرکت ۱ از ۶")
    }

    @Test
    fun oneTapSavesTheSetAndStartsTheRest() {
        launch()
        startFromTraining()
        compose.waitForText("پرس سینه با هالتر")
        compose.waitForText("اولین بار است که این حرکت را ثبت می‌کنی.")
        // No start weight in the plan: the weight field starts empty and the button waits for it.
        compose.field("وزنه").performTextInput("60")
        compose.field("تکرار").performTextClearance()
        compose.field("تکرار").performTextInput("8")
        compose.tapText("ثبت ست")

        compose.waitUntil(5_000) { open()?.workingSets == 1 }
        val set = open()!!.exercises.first().working.single()
        assertEquals(60.0, set.weightKg!!, 1e-9)
        assertEquals(8, set.reps)
        compose.waitForText("استراحت")
        compose.waitForText("ست ۲ از ۴")
        // Rest after bench press: 150 s, and the alarm knows what comes next.
        assertEquals(TestApp.clock.instant().plusSeconds(150), alarm.scheduled.last().first)
        assertEquals("پرس سینه با هالتر", alarm.scheduled.last().second)

        compose.tapText("رد کردن")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("استراحت")).fetchSemanticsNodes().isEmpty() }
        assertTrue(alarm.cancels > 0)
    }

    @Test
    fun anOpenSessionIsOfferedAgainAfterTheAppWasClosed() {
        TestApp.completeSetup(container)
        // Sets saved before the "crash".
        runBlocking {
            programId = container.data.programs.createFromTemplate(ProgramTemplates.upperFocus5, emptySet())
            chestDay = container.data.programs.program(programId).first()!!.days.first().id
            val id = workouts.start(programId, chestDay, VariantKind.FULL)
            val bench = workouts.session(id).first()!!.exercises.first()
            repeat(4) { workouts.logSet(bench.id, SetInput(weightKg = 60.0, reps = 8)) }
        }
        compose.setContent { FitOsRoot(themeMode = ThemeMode.LIGHT, container = container, startWithSetup = false) }
        compose.waitForText("جلسه‌ی نیمه‌تمام: سینه و پشت")
        compose.tapText("جلسه‌ی نیمه‌تمام: سینه و پشت")
        // Bench is done, so the session opens on the second exercise.
        compose.waitForText("حرکت ۲ از ۶")
        compose.waitForText("سیم‌کش زیربغل")
    }

    @Test
    fun finishingShowsTheSummaryAndHistory() {
        launch()
        startFromTraining()
        compose.field("وزنه").performTextInput("60")
        compose.tapText("ثبت ست")
        compose.waitUntil(5_000) { open()?.workingSets == 1 }
        compose.tapText("پایان")
        compose.waitForText("پایان جلسه")
        compose.onNode(hasText("۷") and hasClickAction()).performClick()
        compose.tapText("پایان و ذخیره")
        compose.waitForText("خلاصه‌ی جلسه")
        compose.waitForText("اولین ثبت: پرس سینه با هالتر. از جلسه‌ی بعد با همین مقایسه می‌شود.")
        compose.waitForText("سختی: ۷ از ۱۰")
        assertNull(open())
        compose.tapText("تمام")
        compose.tapText("تاریخچه‌ی جلسه‌ها · ۱ جلسه انجام شده")
        compose.waitForText("سینه و پشت · باشگاه")
        // The next day moves on after the finished one.
        compose.waitForDescription("بازگشت").performClick()
        compose.waitForText("پایین‌تنه")
        compose.waitForDescription("جلسه‌ی امروز ثبت شد")
    }

    @Test
    fun suggestionsExplainTheNextStep() {
        launch()
        // Last time every set reached the top of the range (6–8) with reserve.
        runBlocking {
            val id = workouts.start(programId, chestDay, VariantKind.FULL)
            val bench = workouts.session(id).first()!!.exercises.first()
            repeat(4) { workouts.logSet(bench.id, SetInput(weightKg = 60.0, reps = 8, rir = 2)) }
            workouts.finish(id, null, null)
        }
        compose.onNode(hasText("تمرین") and isSelectable()).performClick()
        compose.tapText("سینه و پشت")
        compose.tapText("شروع این روز (باشگاه)")
        compose.waitForText("پیشنهاد امروز")
        compose.waitForText("۶۲٫۵ × ۶")
        compose.waitForText("همه‌ی ست‌ها به سقف تکرار رسید و ذخیره‌ی کافی داشتی؛ یک گام وزنه اضافه شد.")
        compose.waitFor(hasText("۶۲٫۵") and hasSetTextAction())
        // The user can say no.
        compose.tapText("همان وزنه‌ی قبلی (۶۰)")
        compose.waitFor(hasText("۶۰") and hasSetTextAction())
    }

    @Test
    fun pauseAndAddAnExerciseDuringTheSession() {
        launch()
        startFromTraining()
        compose.waitForDescription("توقف موقت").performClick()
        compose.waitForDescription("جلسه متوقف است. زمان توقف جزو مدت تمرین حساب نمی‌شود.")
        compose.waitUntil(5_000) { open()?.isPaused == true }
        compose.waitForDescription("ادامه").performClick()
        compose.waitUntil(5_000) { open()?.isPaused == false }

        compose.tapText("همه‌ی حرکت‌ها")
        compose.tapText("افزودن حرکت")
        compose.waitForText("افزودن به جلسه")
        compose.field("جست‌وجو").performTextInput("جلو بازو هالتر")
        compose.waitFor(hasText("جلو بازو هالتر") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.waitUntil(5_000) { open()?.exercises?.size == 7 }
        assertEquals("barbell_curl", open()!!.exercises.last().exercise.builtinKey)
    }

    @Test
    fun aSetFromHistoryCanBeFixed() {
        launch()
        val sessionId = runBlocking {
            val id = workouts.start(programId, chestDay, VariantKind.FULL)
            val bench = workouts.session(id).first()!!.exercises.first()
            workouts.logSet(bench.id, SetInput(weightKg = 600.0 / 10, reps = 80))
            workouts.finish(id, null, null)
            id
        }
        openTraining()
        compose.tapText("تاریخچه‌ی جلسه‌ها · ۱ جلسه انجام شده")
        compose.tapText("سینه و پشت · باشگاه")
        compose.tapText("۶۰ × ۸۰")
        compose.waitForText("ویرایش ست ۱")
        compose.field("تکرار").performTextClearance()
        compose.field("تکرار").performTextInput("8")
        compose.tapText("ذخیره")
        compose.waitUntil(5_000) { runBlocking { workouts.session(sessionId).first()!!.exercises.first().sets.single().reps == 8 } }
        compose.waitForText("۶۰ × ۸")
    }
}
