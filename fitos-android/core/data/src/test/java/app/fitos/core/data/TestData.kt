package app.fitos.core.data

import app.fitos.core.domain.nutrition.TargetCalculator
import app.fitos.core.domain.nutrition.TargetInput
import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.profile.InitialSetup
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.Sex
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal object TestData {
    val zone: ZoneId = ZoneId.of("Asia/Tehran")
    val today: LocalDate = LocalDate.of(2026, 10, 20)

    fun clockAt(day: LocalDate, hour: Int = 9): Clock = Clock.fixed(day.atTime(hour, 0).atZone(zone).toInstant(), zone)

    val profile = Profile(
        displayName = "  تست  ",
        sex = Sex.MALE,
        birthDate = LocalDate.of(1996, 3, 1),
        heightCm = 180.0,
        activityLevel = ActivityLevel.MODERATE,
        trainingDaysPerWeek = 4,
        usualTrainingTime = LocalTime.of(12, 30),
        homeWorkoutMinutes = 15,
        homeEquipment = setOf(HomeEquipment.PULL_UP_BAR, HomeEquipment.DUMBBELLS),
        primaryGoal = PrimaryGoal.FAT_LOSS,
        energyPace = EnergyPace.STANDARD,
    )

    fun setup(day: LocalDate = today) = InitialSetup(
        profile = profile,
        currentWeightKg = 90.0,
        targetWeightKg = 80.0,
        goalReviewDate = day.plusWeeks(12),
        target = TargetCalculator.suggest(
            TargetInput(Sex.MALE, profile.ageOn(day), 90.0, 180.0, ActivityLevel.MODERATE, PrimaryGoal.FAT_LOSS, EnergyPace.STANDARD, 80.0),
            effectiveFrom = day,
        ),
    )
}
