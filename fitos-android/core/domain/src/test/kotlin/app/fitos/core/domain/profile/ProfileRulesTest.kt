package app.fitos.core.domain.profile

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRulesTest {

    private val today = LocalDate.of(2026, 10, 20)

    @Test fun heightAndWeight() {
        assertEquals(InputError.REQUIRED, ProfileRules.checkHeight(null))
        assertEquals(InputError.HEIGHT_RANGE, ProfileRules.checkHeight(90.0))
        assertNull(ProfileRules.checkHeight(177.0))
        assertEquals(InputError.WEIGHT_RANGE, ProfileRules.checkWeight(20.0))
        assertNull(ProfileRules.checkWeight(115.0))
    }

    @Test fun ageFromBirthDate() {
        assertEquals(InputError.REQUIRED, ProfileRules.checkBirthDate(null, today))
        assertEquals(InputError.AGE_RANGE, ProfileRules.checkBirthDate(today.minusYears(10), today))
        assertNull(ProfileRules.checkBirthDate(today.minusYears(30), today))
        // Turns 16 tomorrow: still 15 today.
        assertEquals(InputError.AGE_RANGE, ProfileRules.checkBirthDate(today.minusYears(16).plusDays(1), today))
    }

    @Test fun targetWeightFollowsGoalDirection() {
        assertNull(ProfileRules.checkTargetWeight(null, 115.0, PrimaryGoal.FAT_LOSS))
        assertNull(ProfileRules.checkTargetWeight(99.0, 115.0, PrimaryGoal.FAT_LOSS))
        assertEquals(InputError.TARGET_NOT_BELOW_CURRENT, ProfileRules.checkTargetWeight(120.0, 115.0, PrimaryGoal.FAT_LOSS))
        assertEquals(InputError.TARGET_NOT_ABOVE_CURRENT, ProfileRules.checkTargetWeight(70.0, 75.0, PrimaryGoal.MUSCLE_GAIN))
        assertNull(ProfileRules.checkTargetWeight(115.0, 115.0, PrimaryGoal.MAINTAIN))
        assertEquals(InputError.TARGET_WEIGHT_RANGE, ProfileRules.checkTargetWeight(10.0, 115.0, PrimaryGoal.FAT_LOSS))
    }

    @Test fun reviewDateMustBeInTheFuture() {
        assertNull(ProfileRules.checkReviewDate(null, today))
        assertNull(ProfileRules.checkReviewDate(today.plusWeeks(12), today))
        assertEquals(InputError.REVIEW_DATE_NOT_FUTURE, ProfileRules.checkReviewDate(today, today))
    }

    @Test fun activitySuggestionFromSessions() {
        assertEquals(ActivityLevel.SEDENTARY, ActivityLevel.suggestedFor(0))
        assertEquals(ActivityLevel.LIGHT, ActivityLevel.suggestedFor(3))
        assertEquals(ActivityLevel.MODERATE, ActivityLevel.suggestedFor(5))
        assertEquals(ActivityLevel.ACTIVE, ActivityLevel.suggestedFor(6))
    }

    @Test fun ageOn() {
        val p = Profile(
            displayName = null, sex = Sex.MALE, birthDate = LocalDate.of(1999, 10, 21), heightCm = 177.0,
            activityLevel = ActivityLevel.MODERATE, trainingDaysPerWeek = 5, usualTrainingTime = null,
            homeWorkoutMinutes = 15, homeEquipment = emptySet(), primaryGoal = PrimaryGoal.FAT_LOSS, energyPace = EnergyPace.STANDARD,
        )
        assertEquals(26, p.ageOn(today))
        assertEquals(27, p.ageOn(today.plusDays(1)))
    }
}
