package app.fitos.core.domain.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersianNumbersTest {

    @Test fun digits() {
        assertEquals("۱۲۳ کیلوگرم", PersianNumbers.digits("123 کیلوگرم"))
    }

    @Test fun formatIntegers() {
        assertEquals("۲٬۵۰۰", PersianNumbers.format(2500))
        assertEquals("۱۸۰", PersianNumbers.format(180))
        assertEquals("۱٬۲۳۴٬۵۶۷", PersianNumbers.format(1234567L))
        assertEquals("−۱۵", PersianNumbers.format(-15))
        assertEquals("۲۵۰۰", PersianNumbers.format(2500, grouped = false))
    }

    @Test fun formatDecimals() {
        assertEquals("۱۱۳٫۴", PersianNumbers.format(113.4))
        assertEquals("۱۱۳٫۴", PersianNumbers.format(113.40))
        assertEquals("۲٬۵۰۰", PersianNumbers.format(2500.0))
        assertEquals("۲۲٫۵", PersianNumbers.format(22.5))
        assertEquals("۰٫۴۵", PersianNumbers.format(0.454, maxFractionDigits = 2))
        assertEquals("−۱٫۶", PersianNumbers.format(-1.6))
    }

    @Test fun parseAnyDigitSystem() {
        assertEquals(113.4, PersianNumbers.parse("۱۱۳٫۴")!!, 1e-9)
        assertEquals(113.4, PersianNumbers.parse("113.4")!!, 1e-9)
        assertEquals(113.4, PersianNumbers.parse("١١٣٫٤")!!, 1e-9)
        assertEquals(113.4, PersianNumbers.parse("۱۱۳/۴")!!, 1e-9)
        assertEquals(113.4, PersianNumbers.parse("113,4")!!, 1e-9)
        assertEquals(2500.0, PersianNumbers.parse("۲٬۵۰۰")!!, 1e-9)
        assertEquals(2500.0, PersianNumbers.parse("2,500")!!, 1e-9)
        assertEquals(-3.0, PersianNumbers.parse("-۳")!!, 1e-9)
    }

    @Test fun parseRejectsNonNumbers() {
        assertNull(PersianNumbers.parse(""))
        assertNull(PersianNumbers.parse("   "))
        assertNull(PersianNumbers.parse("abc"))
        assertNull(PersianNumbers.parse("۱۱۳٫۴kg"))
    }
}
