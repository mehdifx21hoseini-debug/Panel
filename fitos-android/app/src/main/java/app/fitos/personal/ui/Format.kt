package app.fitos.personal.ui

import app.fitos.core.domain.format.PersianNumbers

/** Display helpers: Persian digits, at most one decimal, no grouping inside input fields. */
object Fmt {
    fun kg(value: Double): String = PersianNumbers.format(value, maxFractionDigits = 1)
    fun int(value: Int): String = PersianNumbers.format(value)
    fun liters(ml: Int): String = PersianNumbers.format(ml / 1000.0, maxFractionDigits = 2)
    fun hours(minutes: Int): String = PersianNumbers.format(minutes / 60.0, maxFractionDigits = 1)

    /** A number as it should appear inside an editable field. */
    fun field(value: Double, maxFractionDigits: Int = 1): String =
        PersianNumbers.format(value, maxFractionDigits = maxFractionDigits, grouped = false)

    fun field(value: Int): String = PersianNumbers.format(value, grouped = false)
}
