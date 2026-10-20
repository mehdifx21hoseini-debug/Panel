package app.fitos.core.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class JalaliCalendarTest {

    private fun check(gregorian: String, jy: Int, jm: Int, jd: Int) {
        val date = LocalDate.parse(gregorian)
        assertEquals("to Jalali: $gregorian", JalaliDate(jy, jm, jd), JalaliDate.of(date))
        assertEquals("to Gregorian: $jy/$jm/$jd", date, JalaliDate(jy, jm, jd).toLocalDate())
    }

    @Test fun knownDates() {
        check("2026-03-21", 1405, 1, 1)   // Nowruz 1405
        check("2025-03-21", 1404, 1, 1)
        check("2025-03-20", 1403, 12, 30) // 1403 is leap
        check("2024-03-20", 1403, 1, 1)
        check("2020-03-20", 1399, 1, 1)
        check("2021-03-20", 1399, 12, 30) // 1399 is leap
        check("2026-10-20", 1405, 7, 28)
        check("2026-09-23", 1405, 7, 1)
        check("1979-02-11", 1357, 11, 22)
        check("2000-01-01", 1378, 10, 11)
    }

    @Test fun leapYears() {
        listOf(1399, 1403, 1408, 1412).forEach { assertTrue("$it leap", JalaliCalendar.isLeapYear(it)) }
        listOf(1400, 1401, 1402, 1404, 1405).forEach { assertFalse("$it not leap", JalaliCalendar.isLeapYear(it)) }
        assertEquals(30, JalaliCalendar.monthLength(1403, 12))
        assertEquals(29, JalaliCalendar.monthLength(1404, 12))
        assertEquals(31, JalaliCalendar.monthLength(1405, 6))
        assertEquals(30, JalaliCalendar.monthLength(1405, 7))
    }

    @Test fun roundTripEveryDayFrom1300To1500() {
        var date = JalaliDate(1300, 1, 1).toLocalDate()
        val end = JalaliDate(1500, 12, JalaliCalendar.monthLength(1500, 12)).toLocalDate()
        var previous: JalaliDate? = null
        while (!date.isAfter(end)) {
            val j = JalaliDate.of(date)
            assertEquals(date, j.toLocalDate())
            previous?.let { assertTrue("monotonic at $date", it < j) }
            previous = j
            date = date.plusDays(1)
        }
    }

    @Test fun weekStartsOnSaturday() {
        val tuesday = LocalDate.parse("2026-10-20")
        assertEquals(DayOfWeek.TUESDAY, tuesday.dayOfWeek)
        assertEquals(LocalDate.parse("2026-10-17"), JalaliCalendar.startOfWeek(tuesday))
        val saturday = LocalDate.parse("2026-10-17")
        assertEquals(saturday, JalaliCalendar.startOfWeek(saturday))
        assertEquals(saturday, JalaliCalendar.startOfWeek(LocalDate.parse("2026-10-23")))
    }

    @Test fun longDateIsPersian() {
        assertEquals("سه‌شنبه ۲۸ مهر ۱۴۰۵", DateFormats.longDate(LocalDate.parse("2026-10-20")))
        assertEquals("۱ فروردین", DateFormats.dayMonth(LocalDate.parse("2026-03-21")))
    }
}
