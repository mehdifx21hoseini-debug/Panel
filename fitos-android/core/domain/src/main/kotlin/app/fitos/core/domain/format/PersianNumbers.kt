package app.fitos.core.domain.format

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Number display and input for Persian.
 * Display uses Persian digits, '٫' as decimal and '٬' as thousands separator.
 * Input accepts Persian, Arabic-Indic and Latin digits with any of '.', ',', '٫', '/' as decimal.
 * Storage is always plain numbers; this is presentation only.
 */
object PersianNumbers {

    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"
    const val DECIMAL_SEPARATOR = '٫'
    const val GROUP_SEPARATOR = '٬'

    /** Replaces Latin digits with Persian ones; everything else is kept. */
    fun digits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') PERSIAN[c - '0'] else c)
    }

    fun format(value: Int, grouped: Boolean = true): String = format(value.toLong(), grouped)

    fun format(value: Long, grouped: Boolean = true): String {
        val raw = kotlin.math.abs(value).toString()
        val body = if (grouped) group(raw) else raw
        return (if (value < 0) "−" else "") + digits(body)
    }

    /**
     * Formats a decimal with at most [maxFractionDigits], dropping trailing zeros:
     * 113.40 → "۱۱۳٫۴", 2500.0 → "۲٬۵۰۰".
     */
    fun format(value: Double, maxFractionDigits: Int = 1, grouped: Boolean = true): String {
        require(!value.isNaN() && !value.isInfinite()) { "not a finite number" }
        // valueOf uses the shortest decimal form (0.15, not 0.1499…), so halves round as people expect.
        val rounded = BigDecimal.valueOf(value).setScale(maxFractionDigits, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = rounded.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        val body = (if (grouped) group(intPart) else intPart) +
            if (fraction.isNotEmpty()) "$DECIMAL_SEPARATOR$fraction" else ""
        val negative = rounded.signum() < 0
        return (if (negative) "−" else "") + digits(body)
    }

    /** Parses user input in any digit system; returns null when it is not a number. */
    fun parse(input: String): Double? {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    /** Normalizes to Latin digits with '.' decimal; strips grouping and spaces. */
    fun normalize(input: String): String = buildString {
        for (c in input.trim()) {
            when {
                c in '0'..'9' -> append(c)
                PERSIAN.indexOf(c) >= 0 -> append('0' + PERSIAN.indexOf(c))
                ARABIC.indexOf(c) >= 0 -> append('0' + ARABIC.indexOf(c))
                c == '.' || c == DECIMAL_SEPARATOR || c == '/' || c == ',' && !looksGrouped(input) -> append('.')
                c == '-' || c == '−' -> if (isEmpty()) append('-')
                c == GROUP_SEPARATOR || c == ',' || c == ' ' || c == '‌' -> Unit
                else -> append(c)
            }
        }
    }

    /**
     * Cleans text typed into a number field as the user types: digits of any script become
     * Persian, '.', ',', '/' and '٫' become a single '٫' (only when [allowDecimal]), everything
     * else is dropped. Integer and fraction lengths are capped so values stay plausible.
     */
    fun sanitizeInput(raw: String, allowDecimal: Boolean, maxIntegerDigits: Int = 4, maxFractionDigits: Int = 1): String {
        val intPart = StringBuilder()
        val fraction = StringBuilder()
        var seenSeparator = false
        for (c in raw) {
            val digit = when {
                c in '0'..'9' -> c - '0'
                PERSIAN.indexOf(c) >= 0 -> PERSIAN.indexOf(c)
                ARABIC.indexOf(c) >= 0 -> ARABIC.indexOf(c)
                else -> -1
            }
            when {
                digit >= 0 && !seenSeparator && intPart.length < maxIntegerDigits -> intPart.append(PERSIAN[digit])
                digit >= 0 && seenSeparator && fraction.length < maxFractionDigits -> fraction.append(PERSIAN[digit])
                digit < 0 && allowDecimal && !seenSeparator && (c == '.' || c == ',' || c == '/' || c == DECIMAL_SEPARATOR) -> seenSeparator = true
            }
        }
        return when {
            !seenSeparator -> intPart.toString()
            else -> (if (intPart.isEmpty()) "۰" else intPart.toString()) + DECIMAL_SEPARATOR + fraction
        }
    }

    /** "2,500" is grouping; "113,4" is a decimal comma. */
    private fun looksGrouped(input: String): Boolean {
        val afterComma = input.substringAfterLast(',', "")
        return afterComma.length == 3 && afterComma.all { it.isDigit() || PERSIAN.indexOf(it) >= 0 || ARABIC.indexOf(it) >= 0 }
    }

    private fun group(digitsOnly: String): String {
        if (digitsOnly.length <= 3) return digitsOnly
        val sb = StringBuilder()
        val firstGroup = digitsOnly.length % 3
        digitsOnly.forEachIndexed { i, c ->
            if (i != 0 && (i - firstGroup) % 3 == 0) sb.append(GROUP_SEPARATOR)
            sb.append(c)
        }
        return sb.toString()
    }
}
