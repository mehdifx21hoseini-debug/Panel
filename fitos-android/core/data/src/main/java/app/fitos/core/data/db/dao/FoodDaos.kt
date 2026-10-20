package app.fitos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.fitos.core.data.db.entity.FoodEntity
import app.fitos.core.data.db.entity.FoodLogEntity
import app.fitos.core.data.db.entity.FoodPortionEntity
import app.fitos.core.data.db.entity.MealPlanEntity
import app.fitos.core.data.db.entity.MealPlanItemEntity
import app.fitos.core.data.db.entity.RecipeIngredientEntity
import app.fitos.core.data.db.entity.SavedMealEntity
import app.fitos.core.data.db.entity.SavedMealItemEntity
import kotlinx.coroutines.flow.Flow

data class FoodWithPortions(
    @Embedded val food: FoodEntity,
    @Relation(parentColumn = "id", entityColumn = "foodId")
    val portions: List<FoodPortionEntity>,
)

data class IngredientRow(
    @Embedded val ingredient: RecipeIngredientEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id", entity = FoodEntity::class)
    val food: FoodWithPortions,
)

data class SavedMealItemRow(
    @Embedded val item: SavedMealItemEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id", entity = FoodEntity::class)
    val food: FoodWithPortions,
)

data class SavedMealTree(
    @Embedded val meal: SavedMealEntity,
    @Relation(parentColumn = "id", entityColumn = "savedMealId", entity = SavedMealItemEntity::class)
    val items: List<SavedMealItemRow>,
)

data class PlanItemRow(
    @Embedded val item: MealPlanItemEntity,
    @Relation(parentColumn = "foodId", entityColumn = "id", entity = FoodEntity::class)
    val food: FoodWithPortions,
)

data class MealPlanTree(
    @Embedded val plan: MealPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "mealPlanId", entity = MealPlanItemEntity::class)
    val items: List<PlanItemRow>,
)

/** Sums per day straight from SQL; the counts of null values tell which totals are incomplete. */
data class DayTotalsRow(
    val day: Long,
    val entries: Int,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val kcalNulls: Int,
    val proteinNulls: Int,
    val carbsNulls: Int,
    val fatNulls: Int,
    val fiberNulls: Int,
)

@Dao
interface FoodDao {
    @Transaction
    @Query("SELECT * FROM food WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<FoodWithPortions>>

    @Transaction
    @Query("SELECT * FROM food WHERE id = :id")
    fun observe(id: String): Flow<FoodWithPortions?>

    @Transaction
    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: String): FoodWithPortions?

    @Query("SELECT * FROM food WHERE builtinKey IS NOT NULL")
    suspend fun builtins(): List<FoodEntity>

    @Upsert suspend fun upsert(food: FoodEntity)
    @Insert suspend fun insertPortions(portions: List<FoodPortionEntity>)

    @Query("DELETE FROM food_portion WHERE foodId = :foodId")
    suspend fun deletePortions(foodId: String)

    @Query("SELECT * FROM food_portion WHERE foodId = :foodId ORDER BY position")
    suspend fun portions(foodId: String): List<FoodPortionEntity>

    @Query("UPDATE food SET isFavorite = :favorite, updatedAt = :at WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, at: Long)

    @Query("UPDATE food SET deletedAt = :at, updatedAt = :at WHERE id = :id AND builtinKey IS NULL")
    suspend fun softDeleteCustom(id: String, at: Long)

    /** Foods logged lately, newest first, one row each. */
    @Transaction
    @Query(
        "SELECT f.* FROM food f JOIN (SELECT foodId, MAX(loggedAt) AS lastAt FROM food_log WHERE deletedAt IS NULL AND foodId IS NOT NULL GROUP BY foodId) l " +
            "ON l.foodId = f.id WHERE f.deletedAt IS NULL ORDER BY l.lastAt DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<FoodWithPortions>>

    @Transaction
    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY position")
    fun observeIngredients(recipeId: String): Flow<List<IngredientRow>>

    @Transaction
    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY position")
    suspend fun ingredients(recipeId: String): List<IngredientRow>

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: String)

    @Insert suspend fun insertIngredients(rows: List<RecipeIngredientEntity>)
}

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_log WHERE day = :day AND deletedAt IS NULL ORDER BY loggedAt, createdAt, rowid")
    fun observeDay(day: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE day = :day AND deletedAt IS NULL ORDER BY loggedAt, createdAt, rowid")
    suspend fun day(day: Long): List<FoodLogEntity>

    @Query(
        "SELECT day, COUNT(*) AS entries, " +
            "COALESCE(SUM(kcal), 0) AS kcal, COALESCE(SUM(proteinG), 0) AS protein, COALESCE(SUM(carbsG), 0) AS carbs, " +
            "COALESCE(SUM(fatG), 0) AS fat, COALESCE(SUM(fiberG), 0) AS fiber, " +
            "SUM(kcal IS NULL) AS kcalNulls, SUM(proteinG IS NULL) AS proteinNulls, SUM(carbsG IS NULL) AS carbsNulls, " +
            "SUM(fatG IS NULL) AS fatNulls, SUM(fiberG IS NULL) AS fiberNulls " +
            "FROM food_log WHERE deletedAt IS NULL AND day BETWEEN :from AND :to GROUP BY day",
    )
    fun observeTotals(from: Long, to: Long): Flow<List<DayTotalsRow>>

    @Query("SELECT * FROM food_log WHERE id = :id")
    suspend fun get(id: String): FoodLogEntity?

    @Insert suspend fun insert(entry: FoodLogEntity)
    @Insert suspend fun insertAll(entries: List<FoodLogEntity>)
    @Update suspend fun update(entry: FoodLogEntity)

    @Query("UPDATE food_log SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE food_log SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long)
}

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM saved_meal WHERE deletedAt IS NULL ORDER BY position, createdAt")
    fun observeSaved(): Flow<List<SavedMealTree>>

    @Transaction
    @Query("SELECT * FROM saved_meal WHERE id = :id")
    suspend fun saved(id: String): SavedMealTree?

    @Query("SELECT COALESCE(MAX(position), -1) FROM saved_meal")
    suspend fun maxSavedPosition(): Int

    @Insert suspend fun insertSaved(meal: SavedMealEntity)
    @Update suspend fun updateSaved(meal: SavedMealEntity)
    @Insert suspend fun insertSavedItems(items: List<SavedMealItemEntity>)

    @Query("SELECT * FROM saved_meal WHERE id = :id")
    suspend fun savedEntity(id: String): SavedMealEntity?

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    fun observeActivePlan(): Flow<MealPlanTree?>

    @Query("SELECT * FROM meal_plan WHERE isActive = 1 AND deletedAt IS NULL LIMIT 1")
    suspend fun activePlan(): MealPlanEntity?

    @Insert suspend fun insertPlan(plan: MealPlanEntity)
    @Insert suspend fun insertPlanItems(items: List<MealPlanItemEntity>)
    @Update suspend fun updatePlanItem(item: MealPlanItemEntity)

    @Query("SELECT * FROM meal_plan_item WHERE id = :id")
    suspend fun planItem(id: String): MealPlanItemEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM meal_plan_item WHERE mealPlanId = :planId")
    suspend fun maxPlanPosition(planId: String): Int

    @Query("DELETE FROM meal_plan_item WHERE id = :id")
    suspend fun deletePlanItem(id: String)

    @Query("DELETE FROM meal_plan_item WHERE mealPlanId = :planId AND weekday IS NULL")
    suspend fun deleteEveryDayItems(planId: String)
}
