package app.fitos.core.domain.time

import app.fitos.core.domain.format.PersianNumbers
import java.time.LocalDate

/** Persian date strings built from [JalaliDate]; storage stays [LocalDate]. */
object DateFormats {

    /** "سه‌شنبه ۲۸ مهر ۱۴۰۵" */
    fun longDate(date: LocalDate): String {
        val j = JalaliDate.of(date)
        return "${JalaliCalendar.weekdayName(date.dayOfWeek)} ${PersianNumbers.digits(j.day.toString())} ${j.monthName} ${PersianNumbers.digits(j.year.toString())}"
    }

    /** "۲۸ مهر" */
    fun dayMonth(date: LocalDate): String {
        val j = JalaliDate.of(date)
        return "${PersianNumbers.digits(j.day.toString())} ${j.monthName}"
    }

    /** Day-of-month number only, Persian digits: "۲۸" */
    fun dayNumber(date: LocalDate): String = PersianNumbers.digits(JalaliDate.of(date).day.toString())
}
