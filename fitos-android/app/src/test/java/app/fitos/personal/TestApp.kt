package app.fitos.personal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.fitos.core.data.DataGraph
import app.fitos.core.domain.nutrition.TargetCalculator
import app.fitos.core.domain.nutrition.TargetInput
import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.InitialSetup
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.Sex
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.settings.ThemeMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class FakeSettingsRepository(initial: ThemeMode = ThemeMode.LIGHT) : SettingsRepository {
    override val themeMode = MutableStateFlow(initial)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
    override val lastBackupAt = MutableStateFlow<Instant?>(null)
    override suspend fun setLastBackupAt(at: Instant) {
        lastBackupAt.value = at
    }
}

/** Fixed day for every UI test: Tuesday 20 Oct 2026 = سه‌شنبه ۲۸ مهر ۱۴۰۵, Tehran time. */
object TestApp {
    val zone: ZoneId = ZoneId.of("Asia/Tehran")
    val today: LocalDate = LocalDate.of(2026, 10, 20)
    val clock: Clock = Clock.fixed(today.atTime(9, 0).atZone(zone).toInstant(), zone)

    fun container(theme: ThemeMode = ThemeMode.LIGHT): AppContainer {
        val settings = FakeSettingsRepository(theme)
        return AppContainer(
            settingsRepository = settings,
            data = DataGraph.inMemory(ApplicationProvider.getApplicationContext<Context>(), clock, settings),
            clock = clock,
        )
    }

    /** Weigh-ins on the [days] before today (oldest first), losing 0.1 kg a day from 91 kg. */
    fun addWeights(container: AppContainer, days: Int) = runBlocking {
        (days downTo 1).forEach { ago ->
            container.data.weights.add(91.0 - (days - ago) * 0.1, today.minusDays(ago.toLong()), java.time.LocalTime.of(7, 30), null, null)
        }
    }

    val profile = Profile(
        displayName = "آرش",
        sex = Sex.MALE,
        birthDate = LocalDate.of(1996, 6, 30),
        heightCm = 180.0,
        activityLevel = ActivityLevel.MODERATE,
        trainingDaysPerWeek = 4,
        usualTrainingTime = null,
        homeWorkoutMinutes = 15,
        homeEquipment = emptySet(),
        primaryGoal = PrimaryGoal.FAT_LOSS,
        energyPace = EnergyPace.STANDARD,
    )

    /** The same person the onboarding test types in, saved directly. */
    fun completeSetup(container: AppContainer) = runBlocking {
        container.data.setup.completeSetup(
            InitialSetup(
                profile = profile,
                currentWeightKg = 90.0,
                targetWeightKg = 80.0,
                goalReviewDate = today.plusWeeks(12),
                target = TargetCalculator.suggest(
                    TargetInput(Sex.MALE, 30, 90.0, 180.0, ActivityLevel.MODERATE, PrimaryGoal.FAT_LOSS, EnergyPace.STANDARD, 80.0),
                    effectiveFrom = today,
                ),
            ),
        )
    }
}
