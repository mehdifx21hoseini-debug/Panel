package app.fitos.core.domain.body

import app.fitos.core.domain.time.JalaliCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** The average of all weigh-ins of one calendar day. */
data class DailyWeight(val day: LocalDate, val weightKg: Double)

/** Smoothed weight on a day that has a weigh-in. */
data class TrendPoint(val day: LocalDate, val actualKg: Double, val trendKg: Double)

data class WeeklyAverage(val weekStart: LocalDate, val averageKg: Double, val days: Int)

/**
 * Weight trend rules (docs/formulas.md). Single days are noisy (water, salt, food in the gut),
 * so decisions use the trend, weekly averages and a regression rate, never one weigh-in.
 */
object WeightTrend {
    /** Exponential moving average weight per day (the "Hacker's Diet" 10%). */
    const val ALPHA = 0.1

    /** Rate window and the minimum data that makes a rate meaningful. */
    const val RATE_WINDOW_DAYS = 28
    const val RATE_MIN_DAYS = 6
    const val RATE_MIN_SPAN_DAYS = 14

    fun daily(entries: List<WeightEntry>): List<DailyWeight> =
        entries.groupBy { it.day }
            .map { (day, list) -> DailyWeight(day, list.sumOf { it.weightKg } / list.size) }
            .sortedBy { it.day }

    /**
     * EMA over the days that have data. A gap of g days counts as g steps of decay
     * (alpha_eff = 1 − (1 − alpha)^g), so a week away moves the trend more than one day does.
     */
    fun ema(daily: List<DailyWeight>, alpha: Double = ALPHA): List<TrendPoint> {
        if (daily.isEmpty()) return emptyList()
        var trend = daily.first().weightKg
        var previous = daily.first().day
        return daily.mapIndexed { index, d ->
            if (index > 0) {
                val gap = ChronoUnit.DAYS.between(previous, d.day).coerceAtLeast(1)
                val effective = 1 - (1 - alpha).pow(gap.toDouble())
                trend += effective * (d.weightKg - trend)
                previous = d.day
            }
            TrendPoint(d.day, d.weightKg, trend)
        }
    }

    /** Averages per Persian week (Saturday to Friday), oldest first. */
    fun weeklyAverages(daily: List<DailyWeight>): List<WeeklyAverage> =
        daily.groupBy { JalaliCalendar.startOfWeek(it.day) }
            .map { (start, days) -> WeeklyAverage(start, days.sumOf { it.weightKg } / days.size, days.size) }
            .sortedBy { it.weekStart }

    /**
     * Change in kg per week: least-squares slope of the daily weights of the last
     * [RATE_WINDOW_DAYS] days up to [today]. Null when there are fewer than [RATE_MIN_DAYS]
     * days or they span less than [RATE_MIN_SPAN_DAYS] days ("not enough data").
     */
    fun weeklyRate(daily: List<DailyWeight>, today: LocalDate): Double? {
        val from = today.minusDays(RATE_WINDOW_DAYS - 1L)
        val window = daily.filter { !it.day.isBefore(from) && !it.day.isAfter(today) }
        if (window.size < RATE_MIN_DAYS) return null
        val span = ChronoUnit.DAYS.between(window.first().day, window.last().day)
        if (span < RATE_MIN_SPAN_DAYS) return null
        return slopePerWeek(window)
    }

    /** Least-squares slope of [days] in kg per week; null for fewer than two distinct days. */
    fun slopePerWeek(days: List<DailyWeight>): Double? {
        if (days.size < 2) return null
        val origin = days.minOf { it.day }
        val xs = days.map { ChronoUnit.DAYS.between(origin, it.day).toDouble() }
        val ys = days.map { it.weightKg }
        val mx = xs.average()
        val my = ys.average()
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        return sxy / sxx * 7
    }
}

/** How far along the goal is, measured on the trend weight. */
object GoalProgress {
    /** 0..1 from start to target; null when the start is unknown or equals the target. */
    fun fraction(startKg: Double?, targetKg: Double, currentKg: Double): Double? {
        if (startKg == null || startKg == targetKg) return null
        return ((startKg - currentKg) / (startKg - targetKg)).coerceIn(0.0, 1.0)
    }

    fun isReviewDue(reviewDate: LocalDate?, today: LocalDate): Boolean = reviewDate != null && !today.isBefore(reviewDate)
}
