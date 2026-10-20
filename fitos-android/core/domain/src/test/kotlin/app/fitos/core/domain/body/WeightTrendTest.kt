package app.fitos.core.domain.body

import app.fitos.core.domain.common.DataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightTrendTest {

    private val start = LocalDate.of(2026, 10, 3) // a Saturday
    private val zone = ZoneId.of("Asia/Tehran")

    private fun entry(day: LocalDate, kg: Double) =
        WeightEntry("id-$day-$kg", Instant.EPOCH, zone, day, kg, null, null, DataSource.MANUAL)

    @Test fun dailyAveragesSameDayEntries() {
        val daily = WeightTrend.daily(listOf(entry(start.plusDays(1), 91.0), entry(start, 90.0), entry(start, 91.0)))
        assertEquals(listOf(DailyWeight(start, 90.5), DailyWeight(start.plusDays(1), 91.0)), daily)
    }

    @Test fun emaStartsAtFirstValueAndMovesTenPercentPerDay() {
        val daily = listOf(DailyWeight(start, 100.0), DailyWeight(start.plusDays(1), 90.0))
        val trend = WeightTrend.ema(daily)
        assertEquals(100.0, trend[0].trendKg, 1e-9)
        assertEquals(99.0, trend[1].trendKg, 1e-9)
    }

    @Test fun emaGapCountsAsSeveralDays() {
        // Two days apart: 1 − 0.9² = 0.19 of the difference.
        val trend = WeightTrend.ema(listOf(DailyWeight(start, 100.0), DailyWeight(start.plusDays(2), 90.0)))
        assertEquals(98.1, trend[1].trendKg, 1e-9)
    }

    @Test fun oneHeavyDayBarelyMovesTheTrend() {
        val days = (0 until 10).map { DailyWeight(start.plusDays(it.toLong()), 100.0) } +
            DailyWeight(start.plusDays(10), 103.0)
        assertEquals(100.3, WeightTrend.ema(days).last().trendKg, 1e-9)
    }

    @Test fun weeklyAveragesUsePersianWeeks() {
        val daily = listOf(
            DailyWeight(start, 100.0), // Saturday
            DailyWeight(start.plusDays(6), 98.0), // Friday, same week
            DailyWeight(start.plusDays(7), 97.0), // next Saturday
        )
        val weeks = WeightTrend.weeklyAverages(daily)
        assertEquals(2, weeks.size)
        assertEquals(WeeklyAverage(start, 99.0, 2), weeks[0])
        assertEquals(WeeklyAverage(start.plusDays(7), 97.0, 1), weeks[1])
    }

    @Test fun rateIsKgPerWeekFromRegression() {
        // Exactly −0.1 kg/day for 21 days = −0.7 kg/week.
        val daily = (0..20).map { DailyWeight(start.plusDays(it.toLong()), 100.0 - 0.1 * it) }
        val rate = WeightTrend.weeklyRate(daily, today = start.plusDays(20))!!
        assertEquals(-0.7, rate, 1e-9)
    }

    @Test fun rateNeedsEnoughDaysAndSpan() {
        val fewDays = (0..4).map { DailyWeight(start.plusDays(it * 4L), 100.0 - it) }
        assertNull(WeightTrend.weeklyRate(fewDays, today = start.plusDays(16)))
        val shortSpan = (0..9).map { DailyWeight(start.plusDays(it.toLong()), 100.0) }
        assertNull(WeightTrend.weeklyRate(shortSpan, today = start.plusDays(9)))
        // Old data outside the 28-day window is ignored.
        val old = (0..20).map { DailyWeight(start.plusDays(it.toLong()), 100.0) }
        assertNull(WeightTrend.weeklyRate(old, today = start.plusDays(60)))
    }

    @Test fun goalProgressAndReview() {
        assertEquals(0.5, GoalProgress.fraction(110.0, 100.0, 105.0)!!, 1e-9)
        assertEquals(1.0, GoalProgress.fraction(110.0, 100.0, 98.0)!!, 1e-9)
        assertEquals(0.0, GoalProgress.fraction(110.0, 100.0, 112.0)!!, 1e-9)
        assertEquals(0.5, GoalProgress.fraction(70.0, 80.0, 75.0)!!, 1e-9) // gaining
        assertNull(GoalProgress.fraction(null, 100.0, 105.0))
        assertTrue(GoalProgress.isReviewDue(start, start))
        assertFalse(GoalProgress.isReviewDue(start.plusDays(1), start))
        assertFalse(GoalProgress.isReviewDue(null, start))
    }
}
