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
import kotlinx.coroutines.flow.first
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
        setup: (suspend (AppContainer) -> Unit)? = null,
        seed: suspend (AppContainer, TestApp.SettableClock) -> Unit = { _, _ -> },
        steps: () -> Unit = {},
    ) {
        val clock = TestApp.SettableClock()
        val container = TestApp.container(theme, clock = clock)
        if (setup != null) kotlinx.coroutines.runBlocking { setup(container) } else if (setupDone) TestApp.completeSetup(container)
        if (weights > 0) TestApp.addWeights(container, weights)
        if (program) kotlinx.coroutines.runBlocking {
            container.data.programs.createFromTemplate(app.fitos.core.domain.training.ProgramTemplates.upperFocus5, emptySet())
        }
        kotlinx.coroutines.runBlocking { seed(container, clock) }
        clock.now = TestApp.clock.instant()
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

    /** Three chest-and-back sessions a week apart, bench press going up 2.5 kg each time. */
    private suspend fun history(container: AppContainer, clock: TestApp.SettableClock, open: Boolean = false) {
        val programs = container.data.programs
        val workouts = container.data.workouts
        val program = programs.active.first()!!
        val chest = program.days.first().id
        listOf(21L, 14L, 7L).forEachIndexed { i, ago ->
            clock.now = TestApp.today.minusDays(ago).atTime(18, 0).atZone(TestApp.zone).toInstant()
            val id = workouts.start(program.id, chest, app.fitos.core.domain.training.VariantKind.FULL)
            val s = workouts.session(id).first()!!
            repeat(4) { workouts.logSet(s.exercises[0].id, app.fitos.core.domain.training.SetInput(weightKg = 55.0 + i * 2.5, reps = 8, rir = 2)) }
            repeat(4) { workouts.logSet(s.exercises[1].id, app.fitos.core.domain.training.SetInput(weightKg = 50.0, reps = 9 + i / 2)) }
            clock.now = clock.now.plusSeconds(52 * 60)
            workouts.finish(id, 7, null)
        }
        if (open) {
            clock.now = TestApp.clock.instant().minusSeconds(14 * 60)
            val id = workouts.start(program.id, chest, app.fitos.core.domain.training.VariantKind.FULL)
            val bench = workouts.session(id).first()!!.exercises[0]
            workouts.logSet(bench.id, app.fitos.core.domain.training.SetInput(weightKg = 62.5, reps = 8, rir = 2))
            clock.now = TestApp.clock.instant().minusSeconds(40)
            workouts.logSet(bench.id, app.fitos.core.domain.training.SetInput(weightKg = 62.5, reps = 7, rir = 1))
        }
    }

    private fun openSessionFromHome() {
        compose.tapText("جلسه‌ی نیمه‌تمام: سینه و پشت")
        compose.waitForText("حرکت ۱ از ۶")
    }

    @Test fun sessionFirstSet() = renderWith("60-session-first", program = true) {
        openTraining()
        click("شروع تمرین")
        compose.waitForText("حرکت ۱ از ۶")
    }
    @Test fun sessionResting() = renderWith("61-session-rest", program = true, seed = { c, k -> history(c, k, open = true) }) { openSessionFromHome() }
    @Test fun sessionRestingDark() = renderWith("61-session-rest-dark", ThemeMode.DARK, program = true, seed = { c, k -> history(c, k, open = true) }) { openSessionFromHome() }
    @Test fun sessionInputs() = renderWith("62-session-inputs", program = true, seed = { c, k -> history(c, k, open = true) }) {
        openSessionFromHome()
        compose.waitForText("ست‌های امروز").performScrollTo()
    }
    @Test fun sessionSummary() = renderWith("63-summary", program = true, seed = { c, k -> history(c, k) }) {
        openTraining()
        click("تاریخچه‌ی جلسه‌ها · ۳ جلسه انجام شده")
        compose.onAllNodes(androidx.compose.ui.test.hasText("سینه و پشت · باشگاه") and androidx.compose.ui.test.hasClickAction())[0].performClick()
        compose.waitForText("رکوردهای تازه")
    }
    @Test fun sessionHistory() = renderWith("64-history", program = true, seed = { c, k -> history(c, k) }) {
        openTraining()
        click("تاریخچه‌ی جلسه‌ها · ۳ جلسه انجام شده")
        compose.waitForText("۳ جلسه انجام شده")
    }
    @Test fun exerciseProgress() = renderWith("65-exercise-progress", program = true, seed = { c, k -> history(c, k) }) {
        openTraining()
        compose.waitForDescription("کتابخانه‌ی حرکات").performClick()
        compose.field("جست‌وجو").performTextInput("پرس سینه با هالتر")
        compose.waitFor(androidx.compose.ui.test.hasText("پرس سینه با هالتر") and androidx.compose.ui.test.hasClickAction() and !androidx.compose.ui.test.hasSetTextAction()).performClick()
        compose.waitForText("رکوردها").performScrollTo()
    }
    @Test fun trainingWithHistory() = renderWith("66-training-next", program = true, seed = { c, k -> history(c, k) }) {
        openTraining()
        compose.waitForText("جلسه‌ی بعدی")
    }
    @Test fun homeOpenSession() = renderWith("12-home-open-session", program = true, seed = { c, k -> history(c, k, open = true) }) {
        compose.waitForText("جلسه‌ی نیمه‌تمام: سینه و پشت")
    }

    /** A realistic day: breakfast, lunch and a snack from the built-in bank, plus a plan for lunch. */
    private suspend fun foodDay(container: AppContainer, clock: TestApp.SettableClock) {
        val foods = container.data.foods.all.first().associateBy { it.builtinKey }
        val log = container.data.foodLog
        fun f(key: String) = foods.getValue(key)
        fun unit(key: String, n: Double, i: Int = 0) = app.fitos.core.domain.nutrition.Amount.of(f(key).portions[i], n)
        val day = TestApp.today
        clock.now = TestApp.clock.instant().minusSeconds(3 * 3600)
        log.add(day, app.fitos.core.domain.nutrition.Meal.BREAKFAST, f("egg_hard_boiled").id, unit("egg_hard_boiled", 2.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.BREAKFAST, f("bread_pita_white").id, unit("bread_pita_white", 1.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.BREAKFAST, f("cheese_feta").id, app.fitos.core.domain.nutrition.Amount(40.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.BREAKFAST, f("tea_black_brewed").id, unit("tea_black_brewed", 1.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.LUNCH, f("rice_white_long_cooked").id, unit("rice_white_long_cooked", 1.5))
        log.add(day, app.fitos.core.domain.nutrition.Meal.LUNCH, f("chicken_breast_grilled").id, app.fitos.core.domain.nutrition.Amount(180.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.LUNCH, f("yogurt_plain_lowfat").id, app.fitos.core.domain.nutrition.Amount(150.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.SNACK, f("apple").id, unit("apple", 1.0))
        log.add(day, app.fitos.core.domain.nutrition.Meal.SNACK, f("almonds").id, app.fitos.core.domain.nutrition.Amount(25.0))
        val plans = container.data.mealPlans
        val plan = plans.ensureActive("برنامه‌ی روزانه")
        plans.addItem(plan, app.fitos.core.domain.nutrition.Meal.LUNCH, f("rice_white_long_cooked").id, 200.0)
        plans.addItem(plan, app.fitos.core.domain.nutrition.Meal.LUNCH, f("chicken_breast_grilled").id, 200.0)
        plans.addItem(plan, app.fitos.core.domain.nutrition.Meal.DINNER, f("lentils_boiled").id, 250.0)
    }

    private fun openNutrition() {
        compose.onNode(androidx.compose.ui.test.hasText("تغذیه") and androidx.compose.ui.test.isSelectable()).performClick()
        compose.waitForText("صبحانه")
    }

    @Test fun nutritionDay() = renderWith("70-nutrition", seed = { c, k -> foodDay(c, k) }) { openNutrition(); compose.waitForText("تخم‌مرغ آب‌پز (سفت)") }
    @Test fun nutritionDayDark() = renderWith("70-nutrition-dark", ThemeMode.DARK, seed = { c, k -> foodDay(c, k) }) { openNutrition(); compose.waitForText("تخم‌مرغ آب‌پز (سفت)") }
    @Test fun nutritionMeals() = renderWith("71-nutrition-meals", seed = { c, k -> foodDay(c, k) }) {
        openNutrition()
        compose.waitForText("برنامه در برابر مصرف")
    }
    @Test fun nutritionEmpty() = render("72-nutrition-empty") { openNutrition() }
    @Test fun foodPicker() = render("73-food-picker") {
        openNutrition()
        compose.waitForDescription("افزودن به ناهار").performClick()
        click("همه")
        compose.waitForText("برنج سفید دانه‌بلند، خام")
    }
    @Test fun foodDetail() = render("74-food-detail") {
        openNutrition()
        compose.waitForDescription("بانک غذا").performClick()
        compose.field("جست‌وجو").performTextInput("سینه‌ی مرغ")
        compose.waitFor(androidx.compose.ui.test.hasText("سینه‌ی مرغ بدون پوست، کبابی (گریل)") and androidx.compose.ui.test.hasClickAction() and !androidx.compose.ui.test.hasSetTextAction()).performClick()
        compose.waitForText("منبع داده")
    }
    @Test fun foodForm() = render("75-food-form") {
        openNutrition()
        compose.waitForDescription("بانک غذا").performClick()
        click("غذای جدید")
        compose.field("نام غذا")
    }
    @Test fun mealPlan() = renderWith("76-meal-plan", seed = { c, k -> foodDay(c, k) }) {
        openNutrition()
        compose.waitForText("برنامه در برابر مصرف")
        click("ویرایش")
        compose.waitForText("کل روز")
    }
    @Test fun homeWithFood() = renderWith("13-home-food", seed = { c, k -> foodDay(c, k) }) { compose.waitForText("هدف‌های امروز") }

    // ---- Stage 6: habits, reminders, return to plan, automatic backup ----

    /** Three weeks of habits: some days missed, one skipped, water, sleep and a walk today. */
    private suspend fun habitWeeks(container: AppContainer, clock: TestApp.SettableClock) {
        val d = container.data
        val today = TestApp.today
        clock.now = today.minusDays(20).atTime(8, 0).atZone(TestApp.zone).toInstant()
        val names = mapOf(
            app.fitos.core.domain.habits.SystemHabit.WATER to "نوشیدن آب",
            app.fitos.core.domain.habits.SystemHabit.SLEEP to "خواب کافی",
            app.fitos.core.domain.habits.SystemHabit.WEIGH_IN to "ثبت وزن",
        )
        names.forEach { (s, n) -> d.habits.add(app.fitos.personal.feature.habits.systemInput(s, n, 4)) }
        val read = d.habits.add(app.fitos.core.domain.habits.HabitInput("مطالعه", "BULB", app.fitos.core.domain.habits.HabitKind.QUANTITY, null, app.fitos.core.domain.habits.HabitFrequency.DAILY, 10.0, "صفحه", emptySet()))
        val calm = d.habits.add(app.fitos.core.domain.habits.HabitInput("مدیتیشن", "LOTUS", app.fitos.core.domain.habits.HabitKind.BOOLEAN, null, app.fitos.core.domain.habits.HabitFrequency.DAILY, null, null, emptySet()))
        val walk = d.habits.add(app.fitos.core.domain.habits.HabitInput("پیاده‌روی با دوست", "SHOE", app.fitos.core.domain.habits.HabitKind.BOOLEAN, null, app.fitos.core.domain.habits.HabitFrequency.WEEKLY, 3.0, null, emptySet()))
        (1L..20L).forEach { ago ->
            val day = today.minusDays(ago)
            d.habits.setLog(read, day, if (ago % 4 == 0L) app.fitos.core.domain.habits.HabitStatus.NOT_DONE else app.fitos.core.domain.habits.HabitStatus.DONE, if (ago % 4 == 0L) 5.0 else 12.0)
            if (ago % 6 != 0L) d.habits.setLog(calm, day, app.fitos.core.domain.habits.HabitStatus.DONE)
            if (ago == 9L) d.habits.setLog(calm, day, app.fitos.core.domain.habits.HabitStatus.SKIPPED)
            if (ago % 3 == 0L) d.habits.setLog(walk, day, app.fitos.core.domain.habits.HabitStatus.DONE)
            d.water.add(day, if (ago % 5 == 0L) 1500 else 2500)
            if (ago % 7 != 3L) d.sleep.save(day, java.time.LocalTime.of(23, 30), java.time.LocalTime.of(if (ago % 2 == 0L) 7 else 6, 30), 4, null)
        }
        clock.now = TestApp.clock.instant()
        d.water.add(today, 500)
        d.water.add(today, 250)
        d.sleep.save(today, java.time.LocalTime.of(23, 45), java.time.LocalTime.of(7, 0), 4, null)
        d.habits.setLog(read, today, app.fitos.core.domain.habits.HabitStatus.NOT_DONE, 4.0)
        d.activity.add(today, app.fitos.core.domain.habits.ActivityInput(app.fitos.core.domain.habits.ActivityKind.WALK, 4200, 35, null))
        d.reminders.save(null, app.fitos.core.domain.habits.ReminderInput(app.fitos.core.domain.habits.ReminderType.HABIT, calm, java.time.LocalTime.of(21, 30), emptySet()))
        d.reminders.save(null, app.fitos.core.domain.habits.ReminderInput(app.fitos.core.domain.habits.ReminderType.WEIGH_IN, null, java.time.LocalTime.of(7, 30), emptySet()))
        d.reminders.save(null, app.fitos.core.domain.habits.ReminderInput(app.fitos.core.domain.habits.ReminderType.BACKUP, null, java.time.LocalTime.of(20, 0), setOf(java.time.DayOfWeek.FRIDAY)))
    }

    private fun openHabits() {
        compose.onNode(androidx.compose.ui.test.hasText("عادت‌ها") and androidx.compose.ui.test.isSelectable()).performClick()
        compose.waitForText("آب")
    }

    @Test fun habits() = renderWith("80-habits", seed = { c, k -> habitWeeks(c, k) }) { openHabits(); compose.waitForText("پایبندی ۴ هفته‌ی اخیر") }
    @Test fun habitsDark() = renderWith("80-habits-dark", ThemeMode.DARK, seed = { c, k -> habitWeeks(c, k) }) { openHabits(); compose.waitForText("پایبندی ۴ هفته‌ی اخیر") }
    @Test fun habitsList() = renderWith("80-habits-list", seed = { c, k -> habitWeeks(c, k) }) { openHabits(); compose.waitForText("مدیتیشن") }
    @Test fun habitsLogs() = renderWith("81-habits-logs", seed = { c, k -> habitWeeks(c, k) }) {
        openHabits()
        compose.waitForText("پیاده‌روی · ۳۵ دقیقه · ۴٬۲۰۰ قدم").performScrollTo()
    }
    @Test fun habitsEmpty() = render("82-habits-empty") { openHabits(); compose.waitForText("هنوز عادتی نساخته‌ای") }
    @Test fun habitEditor() = render("83-habit-editor") {
        openHabits()
        click("عادت جدید")
        type("نام عادت", "مطالعه")
        click("مقدار")
        type("هدف روزانه", "10")
        type("واحد (مثلاً صفحه)", "صفحه")
    }
    @Test fun habitDetail() = renderWith("84-habit-detail", seed = { c, k -> habitWeeks(c, k) }) {
        openHabits()
        click("مدیتیشن")
        click("جزئیات و سابقه")
        compose.waitForText("۵ هفته‌ی اخیر")
    }
    @Test fun reminders() = renderWith("87-reminders", seed = { c, k -> habitWeeks(c, k) }) {
        compose.waitForDescription("تنظیمات").performClick()
        click("یادآورها و برگشت به برنامه")
        compose.waitForText("۷:۳۰ · هر روز")
    }
    @Test fun autoBackup() = render("88-auto-backup") {
        compose.waitForDescription("تنظیمات").performClick()
        click("پشتیبان‌گیری و بازیابی")
        compose.waitForText("پشتیبان خودکار هفتگی").performScrollTo()
    }
    @Test fun homeWithHabits() = renderWith("14-home-habits", seed = { c, k -> habitWeeks(c, k) }) { compose.waitForText("عادت‌های امروز").performScrollTo() }

    /** The last workout six days ago. */
    private suspend fun breakOfSixDays(container: AppContainer, clock: TestApp.SettableClock) {
        val program = container.data.programs.active.first()!!
        clock.now = TestApp.today.minusDays(6).atTime(18, 0).atZone(TestApp.zone).toInstant()
        val id = container.data.workouts.start(program.id, program.days.first().id, app.fitos.core.domain.training.VariantKind.FULL)
        container.data.workouts.finish(id, 6, null)
    }

    @Test fun homeReturn() = renderWith("15-home-return", program = true, seed = { c, k -> breakOfSixDays(c, k) }) { compose.waitForText("۵ روز تمرینی ثبت نشده") }
    @Test fun trainingEasy() = renderWith("67-training-easy", program = true, seed = { c, k -> breakOfSixDays(c, k) }) {
        click("دو جلسه‌ی سبک‌تر")
        openTraining()
        compose.waitForText("شروع جلسه‌ی سبک‌تر")
    }

    /** Setup with a target from 1 September, so the calorie check can judge the last three weeks. */
    private suspend fun oldTarget(container: AppContainer) {
        container.data.setup.completeSetup(
            app.fitos.core.domain.profile.InitialSetup(
                TestApp.profile, 90.0, 80.0, TestApp.today.plusWeeks(12),
                app.fitos.core.domain.nutrition.NutritionTarget(
                    java.time.LocalDate.of(2026, 9, 1), 2500, 180, 285, 70, 35, 3000, 450, app.fitos.core.domain.nutrition.TargetOrigin.CALCULATED, null,
                ),
            ),
        )
    }

    /** Three weeks of weigh-ins (−0.3 kg a week), food at 2500 kcal, the waist and some nights; workouts from [history]. */
    private suspend fun reportWeeks(container: AppContainer, clock: TestApp.SettableClock) {
        val data = container.data
        history(container, clock)
        val meal = data.foods.addCustom(
            app.fitos.core.domain.nutrition.FoodInput(
                "خوراک روزانه", null, null, 100.0, app.fitos.core.domain.nutrition.Nutrients(250.0, 16.0, 30.0, 7.0, 3.5),
                app.fitos.core.domain.nutrition.FoodState.COOKED, app.fitos.core.domain.nutrition.FoodSource.USER, emptyList(),
            ),
        )
        val start = java.time.LocalDate.of(2026, 9, 26)
        (0 until 21).forEach { i ->
            val day = start.plusDays(i.toLong())
            data.weights.add(90.6 - 0.3 / 7 * i + if (i % 3 == 0) 0.2 else 0.0, day, java.time.LocalTime.of(7, 30), null, null)
            data.foodLog.add(day, app.fitos.core.domain.nutrition.Meal.LUNCH, meal.id, app.fitos.core.domain.nutrition.Amount(if (i % 4 == 0) 1100.0 else 980.0))
            if (i >= 14 && i % 2 == 0) data.sleep.save(day, java.time.LocalTime.of(23, 30), java.time.LocalTime.of(6, 45), null, null)
        }
        val abdomen = data.measurements.sites.first().first { it.builtinKey == "abdomen" }.id
        data.measurements.add(java.time.LocalDate.of(2026, 10, 2), mapOf(abdomen to 108.5), null)
        data.measurements.add(java.time.LocalDate.of(2026, 10, 15), mapOf(abdomen to 107.6), null)
    }

    private fun openReport() {
        click("دیدن گزارش")
        compose.waitForText("مهم‌ترین پیشرفت هفته")
    }

    @Test fun homeReport() = renderWith("16-home-report", program = true, setup = { oldTarget(it) }, seed = { c, k -> reportWeeks(c, k) }) {
        compose.waitForText("دیدن گزارش").performScrollTo()
    }
    @Test fun report() = renderWith("90-report", program = true, setup = { oldTarget(it) }, seed = { c, k -> reportWeeks(c, k) }) { openReport() }
    @Test fun reportDark() = renderWith("90-report-dark", ThemeMode.DARK, program = true, setup = { oldTarget(it) }, seed = { c, k -> reportWeeks(c, k) }) { openReport() }
    @Test fun reportMetrics() = renderWith("91-report-metrics", program = true, setup = { oldTarget(it) }, seed = { c, k -> reportWeeks(c, k) }) {
        openReport()
        compose.waitForText("تغذیه").performScrollTo()
    }
    @Test fun reportCheck() = renderWith("92-report-check", program = true, setup = { oldTarget(it) }, seed = { c, k -> reportWeeks(c, k) }) {
        openReport()
        compose.waitFor(androidx.compose.ui.test.hasText("تقریبی است:", substring = true)).performScrollTo()
    }
}
