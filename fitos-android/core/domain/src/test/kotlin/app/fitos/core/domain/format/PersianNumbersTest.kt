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
        assertEquals("۰٫۲", PersianNumbers.format(0.15)) // not 0.1 from binary 0.1499…
        assertEquals("۰", PersianNumbers.format(0.0))
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

    @Test fun sanitizeInputWhileTyping() {
        assertEquals("۱۱۵", PersianNumbers.sanitizeInput("115", allowDecimal = true))
        assertEquals("۱۱۵٫۴", PersianNumbers.sanitizeInput("115.4", allowDecimal = true))
        assertEquals("۱۱۵٫۴", PersianNumbers.sanitizeInput("۱۱۵,۴۵", allowDecimal = true)) // one fraction digit
        assertEquals("۱۱۵٫", PersianNumbers.sanitizeInput("١١٥/", allowDecimal = true)) // still typing
        assertEquals("۰٫۵", PersianNumbers.sanitizeInput(".5", allowDecimal = true))
        assertEquals("۱۱۵", PersianNumbers.sanitizeInput("1a1-5", allowDecimal = false))
        assertEquals("۱۲۳", PersianNumbers.sanitizeInput("12345", allowDecimal = false, maxIntegerDigits = 3))
        assertEquals("۱٫۲", PersianNumbers.sanitizeInput("1.2.3", allowDecimal = true))
        assertEquals("", PersianNumbers.sanitizeInput("abc", allowDecimal = true))
        assertEquals(115.4, PersianNumbers.parse(PersianNumbers.sanitizeInput("115.4", allowDecimal = true))!!, 1e-9)
    }
}
