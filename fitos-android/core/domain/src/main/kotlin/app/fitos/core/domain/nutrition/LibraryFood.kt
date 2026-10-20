package app.fitos.core.domain.nutrition

/** One food of the built-in bank, per 100 g, as published by USDA (see [FoodLibrary]). */
data class LibraryFood(
    /** Stable id; the database finds built-in foods by it when the bank is updated. */
    val key: String,
    val fdcId: Int,
    val nameFa: String,
    val nameEn: String,
    val category: FoodCategory,
    val state: FoodState,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double?,
    val sugarG: Double?,
    val sodiumMg: Double?,
    val portions: List<FoodPortion>,
)
