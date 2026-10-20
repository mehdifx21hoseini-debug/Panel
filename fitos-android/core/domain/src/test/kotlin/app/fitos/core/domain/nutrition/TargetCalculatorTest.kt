package app.fitos.core.domain.nutrition

import app.fitos.core.domain.profile.ActivityLevel
import app.fitos.core.domain.profile.EnergyPace
import app.fitos.core.domain.profile.PrimaryGoal
import app.fitos.core.domain.profile.Sex
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCalculatorTest {

    private val day = LocalDate.of(2026, 10, 20)

    private fun input(
        sex: Sex = Sex.MALE,
        age: Int = 30,
        weight: Double = 115.0,
        height: Double = 177.0,
        activity: ActivityLevel = ActivityLevel.MODERATE,
        goal: PrimaryGoal = PrimaryGoal.FAT_LOSS,
        pace: EnergyPace = EnergyPace.STANDARD,
        target: Double? = 100.0,
    ) = TargetInput(sex, age, weight, height, activity, goal, pace, target)

    @Test fun mifflinStJeor_matchesPublishedFormula() {
        // 10×115 + 6.25×177 − 5×30 + 5 = 2111.25
        assertEquals(2111.25, EnergyCalculator.bmr(Sex.MALE, 115.0, 177.0, 30), 1e-9)
        // 10×60 + 6.25×165 − 5×25 − 161 = 1345.25
        assertEquals(1345.25, EnergyCalculator.bmr(Sex.FEMALE, 60.0, 165.0, 25), 1e-9)
        assertEquals(1345.25 * 1.375, EnergyCalculator.tdee(1345.25, ActivityLevel.LIGHT), 1e-9)
    }

    @Test fun fatLoss_standardDeficit_andMacrosFromTargetWeight() {
        val t = TargetCalculator.suggest(input(), day)
        val a = t.assumptions!!
        // TDEE 2111.25 × 1.55 = 3272.4; × 0.8 = 2617.9 → 2600
        assertEquals(3272.4375, a.tdee, 1e-9)
        assertEquals(-20, a.adjustmentPercent)
        assertEquals(2600, t.kcal)
        // Reference weight is the target (100 kg) while losing towards it.
        assertEquals(100.0, a.referenceWeightKg, 1e-9)
        assertEquals(180, t.proteinG)
        assertEquals(70, t.fatG)
        // (2600 − 720 − 630) / 4 = 312.5 → 315 (nearest 5)
        assertEquals(315, t.carbsG)
        assertEquals(36, t.fiberG)
        assertEquals(3000, t.waterMl)
        assertEquals(450, t.sleepMinutes)
        assertEquals(TargetOrigin.CALCULATED, t.origin)
        assertFalse(a.raisedToFloor)
        assertEquals(day, t.effectiveFrom)
    }

    @Test fun deficitNeverGoesBelowBmr() {
        // Sedentary: TDEE = 1.2 × BMR, a 25% cut would be 0.9 × BMR.
        val t = TargetCalculator.suggest(input(activity = ActivityLevel.SEDENTARY, pace = EnergyPace.FIRM), day)
        val a = t.assumptions!!
        assertTrue(a.raisedToFloor)
        assertTrue(t.kcal >= a.bmr)
        assertEquals(2150, t.kcal) // BMR 2111.25, rounded up to 50
    }

    @Test fun absoluteFloorForSmallBodies() {
        val t = TargetCalculator.suggest(
            input(sex = Sex.FEMALE, age = 60, weight = 45.0, height = 150.0, activity = ActivityLevel.SEDENTARY, target = null),
            day,
        )
        assertEquals(1200, t.kcal)
        assertTrue(t.assumptions!!.raisedToFloor)
    }

    @Test fun maintenanceAndGain() {
        val keep = TargetCalculator.suggest(input(goal = PrimaryGoal.MAINTAIN, target = null), day)
        assertEquals(3250, keep.kcal) // 3272.4 → 3250
        assertEquals(115.0, keep.assumptions!!.referenceWeightKg, 1e-9)
        val gain = TargetCalculator.suggest(input(goal = PrimaryGoal.MUSCLE_GAIN, weight = 70.0, target = 75.0), day)
        assertEquals(10, gain.assumptions!!.adjustmentPercent)
        // Gaining: reference stays the current weight.
        assertEquals(70.0, gain.assumptions!!.referenceWeightKg, 1e-9)
        assertEquals(125, gain.proteinG) // 1.8 × 70 = 126 → 125
    }

    @Test fun fatIsRaisedToTwentyPercentOfEnergy() {
        // Light person with high energy: 0.7 g/kg would be under 20% of energy.
        val t = TargetCalculator.suggest(
            input(weight = 60.0, height = 190.0, age = 20, activity = ActivityLevel.VERY_ACTIVE, goal = PrimaryGoal.MUSCLE_GAIN, target = null),
            day,
        )
        assertTrue(t.assumptions!!.fatRaisedToMinimum)
        assertTrue(t.fatG * 9 >= t.kcal * 0.2 - 45)
    }

    @Test fun waterIsClampedAndStepped() {
        val small = TargetCalculator.suggest(input(weight = 50.0, target = null, goal = PrimaryGoal.MAINTAIN), day)
        assertEquals(2000, small.waterMl)
        val large = TargetCalculator.suggest(input(weight = 180.0, target = null, goal = PrimaryGoal.MAINTAIN), day)
        assertEquals(4000, large.waterMl)
    }

    @Test fun macrosAddUpCloseToEnergy() {
        val t = TargetCalculator.suggest(input(), day)
        val fromMacros = TargetCalculator.kcalFromMacros(t.proteinG, t.carbsG, t.fatG)
        assertTrue(kotlin.math.abs(fromMacros - t.kcal) <= 40)
    }

    @Test fun belowBmrWarning() {
        val t = TargetCalculator.suggest(input(), day)
        assertTrue(TargetRules.isBelowBmr(1800, t.assumptions))
        assertFalse(TargetRules.isBelowBmr(2600, t.assumptions))
        assertFalse(TargetRules.isBelowBmr(1000, null))
    }
}
