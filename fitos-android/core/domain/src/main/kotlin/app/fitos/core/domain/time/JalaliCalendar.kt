package app.fitos.core.domain.time

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Persian (Solar Hijri / Jalali) calendar for display only.
 *
 * Dates are always stored as [LocalDate] (epochDay); Jalali is derived when shown.
 * The arithmetic is a Kotlin port of the jalaali-js algorithm (MIT, Behrang Noruzi Niya),
 * based on Kazimierz M. Borkowski's leap-year breaks, valid for Jalali years -61..3177.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {

    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..JalaliCalendar.monthLength(year, month)) { "day out of range: $year/$month/$day" }
    }

    fun toLocalDate(): LocalDate = JalaliCalendar.toLocalDate(year, month, day)

    val monthName: String get() = JalaliCalendar.MONTH_NAMES[month - 1]

    override fun compareTo(other: JalaliDate): Int =
        compareValuesBy(this, other, JalaliDate::year, JalaliDate::month, JalaliDate::day)

    companion object {
        fun of(date: LocalDate): JalaliDate = JalaliCalendar.fromLocalDate(date)
    }
}

object JalaliCalendar {

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** Persian week starts on Saturday. */
    val WEEK_ORDER = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
    }

    fun weekdayInitial(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "ش"
        DayOfWeek.SUNDAY -> "ی"
        DayOfWeek.MONDAY -> "د"
        DayOfWeek.TUESDAY -> "س"
        DayOfWeek.WEDNESDAY -> "چ"
        DayOfWeek.THURSDAY -> "پ"
        DayOfWeek.FRIDAY -> "ج"
    }

    /** The Saturday that starts the Persian week containing [date]. */
    fun startOfWeek(date: LocalDate): LocalDate {
        val offset = (date.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
        return date.minusDays(offset.toLong())
    }

    fun isLeapYear(year: Int): Boolean = jalCal(year).leap == 0

    fun monthLength(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeapYear(year) -> 30
        else -> 29
    }

    fun fromLocalDate(date: LocalDate): JalaliDate {
        val jdn = date.toEpochDay() + EPOCH_DAY_TO_JDN
        return d2j(jdn.toInt())
    }

    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate {
        val jdn = j2d(year, month, day)
        return LocalDate.ofEpochDay((jdn - EPOCH_DAY_TO_JDN).toLong())
    }

    // ---- jalaali-js port ----------------------------------------------------------------

    private const val EPOCH_DAY_TO_JDN = 2440588

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCal {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        require(jy >= jp && jy < BREAKS.last()) { "Jalali year out of range: $jy" }
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Int): JalaliDate {
        val gy = d2gYear(jdn)
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 + (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
        d = d - (((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 + 752
        return d
    }

    private fun d2gYear(jdn: Int): Int {
        var j = 4 * jdn + 139361631
        j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gm = ((i / 153) % 12) + 1
        return j / 1461 - 100100 + (8 - gm) / 6
    }
}
