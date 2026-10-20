package app.fitos.core.domain.training

/** Library search and filters. Empty filter = everything. */
data class ExerciseFilter(
    val query: String = "",
    /** Matches the exercise's primary muscles only. */
    val muscle: Muscle? = null,
    val equipment: Equipment? = null,
    val favoritesOnly: Boolean = false,
    val mineOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = this == ExerciseFilter()

    /** Keeps the input order, except that names starting with the query come first. */
    fun apply(exercises: List<Exercise>): List<Exercise> {
        val q = fold(query)
        val kept = exercises.filter { e ->
            (muscle == null || muscle in e.primary) &&
                (equipment == null || e.equipment == equipment) &&
                (!favoritesOnly || e.isFavorite) &&
                (!mineOnly || e.isCustom) &&
                (q.isEmpty() || fold(e.nameFa).contains(q) || e.nameEn?.let { fold(it).contains(q) } == true)
        }
        if (q.isEmpty()) return kept
        return kept.sortedBy { if (fold(it.nameFa).startsWith(q) || it.nameEn?.let { n -> fold(n).startsWith(q) } == true) 0 else 1 }
    }

    companion object {
        private const val ALPHABET = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی"

        /**
         * Persian alphabetical order (پ چ ژ گ in place, unlike code-point order), the same on every
         * device; other characters follow the letters, a space precedes everything.
         */
        val nameOrder: Comparator<String> = Comparator { a, b ->
            val x = fold(a)
            val y = fold(b)
            for (i in 0 until minOf(x.length, y.length)) {
                val diff = rank(x[i]) - rank(y[i])
                if (diff != 0) return@Comparator diff
            }
            x.length - y.length
        }

        private fun rank(c: Char): Int = when {
            c == ' ' -> -1
            ALPHABET.indexOf(c) >= 0 -> ALPHABET.indexOf(c)
            else -> ALPHABET.length + c.code
        }

        /**
         * Folds text for matching: Arabic yeh/kaf become Persian, the zero-width non-joiner and
         * repeated spaces become one space, hamza forms and case are ignored.
         */
        fun fold(text: String): String = buildString {
            var space = false
            for (c in text.trim().lowercase()) {
                val mapped = when (c) {
                    'ي', 'ى', 'ئ' -> 'ی'
                    'ك' -> 'ک'
                    'ۀ', 'ة' -> 'ه'
                    'أ', 'إ', 'آ' -> 'ا'
                    'ؤ' -> 'و'
                    '‌', '-', '_' -> ' '
                    else -> c
                }
                if (mapped == ' ' || mapped.isWhitespace()) {
                    if (!space && isNotEmpty()) append(' ')
                    space = true
                } else {
                    append(mapped)
                    space = false
                }
            }
        }.trimEnd()
    }
}
