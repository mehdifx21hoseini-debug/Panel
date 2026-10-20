package app.fitos.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A food or a recipe (isRecipe). Nutrients are per 100 g of the food as described by [state];
 * a null nutrient means "no reliable data", which the UI marks as incomplete instead of counting 0.
 */
@Entity(tableName = "food", indices = [Index(value = ["builtinKey"], unique = true), Index("nameFa")])
data class FoodEntity(
    @PrimaryKey val id: String,
    val builtinKey: String?,
    val nameFa: String,
    val nameEn: String?,
    val brand: String?,
    val kcalPer100g: Double?,
    val proteinPer100g: Double?,
    val carbsPer100g: Double?,
    val fatPer100g: Double?,
    val fiberPer100g: Double?,
    val sugarPer100g: Double?,
    val sodiumMgPer100g: Double?,
    /** raw, cooked or not_applicable. */
    val state: String,
    /** usda, label, user, estimate or recipe. */
    val source: String,
    val sourceRef: String?,
    /** high, medium or low. */
    val accuracy: String,
    val isRecipe: Boolean,
    val recipeServings: Double?,
    /** Weight after cooking, when known, so a recipe can be logged by grams of the finished dish. */
    val recipeCookedWeightG: Double?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** A household unit for a food, e.g. "1 medium = 50 g". */
@Entity(
    tableName = "food_portion",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("foodId")],
)
data class FoodPortionEntity(
    @PrimaryKey val id: String,
    val foodId: String,
    val label: String,
    val grams: Double,
    val source: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"]),
    ],
    indices = [Index("recipeId"), Index("foodId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val foodId: String,
    val grams: Double,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A logged portion with a copy of its nutrients at logging time, so food edits do not rewrite the past. */
@Entity(
    tableName = "food_log",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("day"), Index("foodId")],
)
data class FoodLogEntity(
    @PrimaryKey val id: String,
    val day: Long,
    /** breakfast, lunch, dinner or snack. */
    val meal: String,
    val foodId: String?,
    val foodName: String,
    val grams: Double,
    val portionLabel: String?,
    val portionQuantity: Double?,
    val kcal: Double?,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val loggedAt: Long,
    val zoneId: String,
    val source: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "saved_meal")
data class SavedMealEntity(
    @PrimaryKey val id: String,
    val name: String,
    val meal: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "saved_meal_item",
    foreignKeys = [
        ForeignKey(entity = SavedMealEntity::class, parentColumns = ["id"], childColumns = ["savedMealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"]),
    ],
    indices = [Index("savedMealId"), Index("foodId")],
)
data class SavedMealItemEntity(
    @PrimaryKey val id: String,
    val savedMealId: String,
    val foodId: String,
    val grams: Double,
    val portionLabel: String?,
    val portionQuantity: Double?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "meal_plan")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(
    tableName = "meal_plan_item",
    foreignKeys = [
        ForeignKey(entity = MealPlanEntity::class, parentColumns = ["id"], childColumns = ["mealPlanId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"]),
    ],
    indices = [Index("mealPlanId"), Index("foodId")],
)
data class MealPlanItemEntity(
    @PrimaryKey val id: String,
    val mealPlanId: String,
    /** ISO day of week 1..7, or null for every day. */
    val weekday: Int?,
    val meal: String,
    val foodId: String,
    val grams: Double,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
