package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.dao.DayTotalsRow
import app.fitos.core.data.db.dao.FoodWithPortions
import app.fitos.core.data.db.entity.FoodEntity
import app.fitos.core.data.db.entity.FoodLogEntity
import app.fitos.core.data.db.entity.FoodPortionEntity
import app.fitos.core.data.db.entity.MealPlanEntity
import app.fitos.core.data.db.entity.MealPlanItemEntity
import app.fitos.core.data.db.entity.RecipeIngredientEntity
import app.fitos.core.data.db.entity.SavedMealEntity
import app.fitos.core.data.db.entity.SavedMealItemEntity
import app.fitos.core.domain.common.DataSource
import app.fitos.core.domain.nutrition.Accuracy
import app.fitos.core.domain.nutrition.Amount
import app.fitos.core.domain.nutrition.Food
import app.fitos.core.domain.nutrition.FoodInput
import app.fitos.core.domain.nutrition.FoodLibrary
import app.fitos.core.domain.nutrition.FoodLogEntry
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.FoodPortion
import app.fitos.core.domain.nutrition.FoodRepository
import app.fitos.core.domain.nutrition.FoodSource
import app.fitos.core.domain.nutrition.FoodState
import app.fitos.core.domain.nutrition.LibraryFood
import app.fitos.core.domain.nutrition.Meal
import app.fitos.core.domain.nutrition.MealPlan
import app.fitos.core.domain.nutrition.MealPlanItem
import app.fitos.core.domain.nutrition.MealPlanRepository
import app.fitos.core.domain.nutrition.NutrientTotals
import app.fitos.core.domain.nutrition.Nutrients
import app.fitos.core.domain.nutrition.RecipeIngredient
import app.fitos.core.domain.nutrition.RecipeInput
import app.fitos.core.domain.nutrition.RecipeMath
import app.fitos.core.domain.nutrition.SavedMeal
import app.fitos.core.domain.nutrition.SavedMealItem
import app.fitos.core.domain.nutrition.SavedMealRepository
import app.fitos.core.domain.training.ExerciseFilter
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun FoodWithPortions.toDomain() = Food(
    id = food.id,
    builtinKey = food.builtinKey,
    nameFa = food.nameFa,
    nameEn = food.nameEn,
    brand = food.brand,
    per100g = Nutrients(food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g, food.fiberPer100g),
    sugarPer100g = food.sugarPer100g,
    sodiumMgPer100g = food.sodiumMgPer100g,
    state = FoodState.fromCode(food.state),
    source = FoodSource.fromCode(food.source),
    sourceRef = food.sourceRef,
    accuracy = Accuracy.fromCode(food.accuracy),
    category = food.builtinKey?.let { FoodLibrary.byKey[it]?.category },
    isRecipe = food.isRecipe,
    recipeServings = food.recipeServings,
    recipeCookedWeightG = food.recipeCookedWeightG,
    isFavorite = food.isFavorite,
    portions = portions.sortedBy { it.position }.map { FoodPortion(it.label, it.grams) },
)

class RoomFoodRepository(private val db: FitDatabase, private val clock: Clock) : FoodRepository {
    private val dao = db.foodDao()
    private val seedLock = Mutex()
    @Volatile private var seeded = false

    // Persian alphabetical order, the same as the exercise library.
    override val all: Flow<List<Food>> = dao.observeAll()
        .onStart { ensureLibrary() }
        .map { list -> list.map { it.toDomain() }.sortedWith(compareBy(ExerciseFilter.nameOrder) { it.nameFa }) }

    override val recent: Flow<List<Food>> = dao.observeRecent(30).map { list -> list.map { it.toDomain() } }

    override fun food(id: String): Flow<Food?> = dao.observe(id).map { it?.toDomain() }

    override fun ingredients(recipeId: String): Flow<List<RecipeIngredient>> =
        dao.observeIngredients(recipeId).map { rows -> rows.map { RecipeIngredient(it.food.toDomain(), it.ingredient.grams) } }

    /**
     * Inserts missing foods of the built-in bank and refreshes changed ones, keyed by
     * [LibraryFood.key]; ids and favourites are kept so logs and recipes stay linked.
     */
    suspend fun ensureLibrary() = seedLock.withLock {
        if (seeded) return@withLock
        val existing = dao.builtins().associateBy { it.builtinKey }
        val now = clock.millis()
        db.withTransaction {
            FoodLibrary.all.forEach { lib ->
                val row = existing[lib.key]
                val id = row?.id ?: newId()
                val entity = lib.toEntity(id, row?.isFavorite ?: false, row?.createdAt ?: now, now)
                if (row == null || row.copy(updatedAt = now) != entity || dao.portions(id).map { it.label to it.grams } != lib.portions.map { it.label to it.grams }) {
                    dao.upsert(entity)
                    dao.deletePortions(id)
                    dao.insertPortions(lib.portions.mapIndexed { i, p -> FoodPortionEntity(newId(), id, p.label, p.grams, "usda", i, now, now) })
                }
            }
        }
        seeded = true
    }

    /** Checks the bank again after a restore replaced the tables. */
    suspend fun reseed() {
        seedLock.withLock { seeded = false }
        ensureLibrary()
    }

    override suspend fun addCustom(input: FoodInput): Food {
        val id = newId()
        val now = clock.millis()
        db.withTransaction {
            dao.upsert(input.toEntity(id, now, now))
            dao.insertPortions(portionRows(id, input.portions, now))
        }
        return dao.get(id)!!.toDomain()
    }

    override suspend fun updateCustom(id: String, input: FoodInput) = db.withTransaction {
        val old = dao.get(id)?.food ?: return@withTransaction
        require(old.builtinKey == null && !old.isRecipe) { "only own foods can be edited here" }
        val now = clock.millis()
        dao.upsert(input.toEntity(id, old.createdAt, now).copy(isFavorite = old.isFavorite))
        dao.deletePortions(id)
        dao.insertPortions(portionRows(id, input.portions, now))
    }

    override suspend fun deleteCustom(id: String) = db.withTransaction { dao.softDeleteCustom(id, clock.millis()) }

    override suspend fun setFavorite(id: String, favorite: Boolean) = db.withTransaction { dao.setFavorite(id, favorite, clock.millis()) }

    override suspend fun saveRecipe(id: String?, input: RecipeInput): String = db.withTransaction {
        val recipeId = id ?: newId()
        val now = clock.millis()
        val ingredients = input.ingredients.mapNotNull { (foodId, grams) -> dao.get(foodId)?.let { RecipeIngredient(it.toDomain(), grams) } }
        val result = RecipeMath.compute(ingredients, input.cookedWeightG)
        val old = id?.let { dao.get(it)?.food }
        dao.upsert(
            FoodEntity(
                id = recipeId, builtinKey = null, nameFa = input.nameFa.trim(), nameEn = null, brand = null,
                kcalPer100g = result.per100g.kcal, proteinPer100g = result.per100g.proteinG, carbsPer100g = result.per100g.carbsG,
                fatPer100g = result.per100g.fatG, fiberPer100g = result.per100g.fiberG, sugarPer100g = null, sodiumMgPer100g = null,
                state = FoodState.COOKED.code, source = FoodSource.RECIPE.code, sourceRef = null,
                // A recipe is as good as its weakest ingredient.
                accuracy = ingredients.map { it.food.accuracy }.maxByOrNull { it.ordinal }?.code ?: Accuracy.LOW.code,
                isRecipe = true, recipeServings = input.servings, recipeCookedWeightG = input.cookedWeightG,
                isFavorite = old?.isFavorite ?: false, createdAt = old?.createdAt ?: now, updatedAt = now, deletedAt = null,
            ),
        )
        dao.deleteIngredients(recipeId)
        dao.insertIngredients(input.ingredients.mapIndexed { i, (foodId, grams) -> RecipeIngredientEntity(newId(), recipeId, foodId, grams, i, now, now) })
        dao.deletePortions(recipeId)
        dao.insertPortions(listOf(FoodPortionEntity(newId(), recipeId, SERVING_LABEL, result.servingGrams(input.servings), "recipe", 0, now, now)))
        recipeId
    }

    private fun portionRows(foodId: String, portions: List<FoodPortion>, now: Long) =
        portions.mapIndexed { i, p -> FoodPortionEntity(newId(), foodId, p.label.trim(), p.grams, "user", i, now, now) }

    private fun LibraryFood.toEntity(id: String, isFavorite: Boolean, createdAt: Long, updatedAt: Long) = FoodEntity(
        id = id, builtinKey = key, nameFa = nameFa, nameEn = nameEn, brand = null,
        kcalPer100g = kcal, proteinPer100g = proteinG, carbsPer100g = carbsG, fatPer100g = fatG, fiberPer100g = fiberG,
        sugarPer100g = sugarG, sodiumMgPer100g = sodiumMg, state = state.code, source = FoodSource.USDA.code,
        sourceRef = "FDC $fdcId", accuracy = Accuracy.HIGH.code, isRecipe = false, recipeServings = null, recipeCookedWeightG = null,
        isFavorite = isFavorite, createdAt = createdAt, updatedAt = updatedAt, deletedAt = null,
    )

    private fun FoodInput.toEntity(id: String, createdAt: Long, updatedAt: Long): FoodEntity {
        val p = per100g
        return FoodEntity(
            id = id, builtinKey = null, nameFa = nameFa.trim(), nameEn = nameEn.clean(), brand = brand.clean(),
            kcalPer100g = p.kcal, proteinPer100g = p.proteinG, carbsPer100g = p.carbsG, fatPer100g = p.fatG, fiberPer100g = p.fiberG,
            sugarPer100g = null, sodiumMgPer100g = null, state = state.code, source = source.code, sourceRef = null,
            accuracy = accuracy.code, isRecipe = false, recipeServings = null, recipeCookedWeightG = null,
            isFavorite = false, createdAt = createdAt, updatedAt = updatedAt, deletedAt = null,
        )
    }

    companion object {
        /** Label of the one-serving portion every recipe gets. */
        const val SERVING_LABEL = "یک سهم"
    }
}

class RoomFoodLogRepository(private val db: FitDatabase, private val clock: Clock) : FoodLogRepository {
    private val dao = db.foodLogDao()
    private val foods = db.foodDao()
    private val meals = db.mealDao()

    override fun day(date: LocalDate): Flow<List<FoodLogEntry>> = dao.observeDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override fun totals(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, NutrientTotals>> =
        dao.observeTotals(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.associate { LocalDate.ofEpochDay(it.day) to it.toTotals() } }

    // Writes go through withTransaction like the other repositories: Room then runs them on its
    // transaction thread instead of the caller's (the main thread for view models).
    override suspend fun add(date: LocalDate, meal: Meal, foodId: String, amount: Amount, note: String?): String = db.withTransaction {
        val food = foods.get(foodId)?.toDomain() ?: error("food not found")
        val id = newId()
        dao.insert(entry(id, date, meal, food, amount, note))
        id
    }

    override suspend fun update(id: String, meal: Meal, amount: Amount) = db.withTransaction {
        val old = dao.get(id) ?: return@withTransaction
        // Scale the numbers copied at logging time, so an edited food never rewrites a past day.
        val factor = if (old.grams > 0) amount.grams / old.grams else 0.0
        dao.update(
            old.copy(
                meal = meal.code, grams = amount.grams, portionLabel = amount.portionLabel, portionQuantity = amount.portionQuantity,
                kcal = old.kcal?.times(factor), proteinG = old.proteinG?.times(factor), carbsG = old.carbsG?.times(factor),
                fatG = old.fatG?.times(factor), fiberG = old.fiberG?.times(factor), updatedAt = clock.millis(),
            ),
        )
    }

    override suspend fun delete(id: String) = db.withTransaction { dao.softDelete(id, clock.millis()) }
    override suspend fun restore(id: String) = db.withTransaction { dao.restore(id, clock.millis()) }

    override suspend fun copy(from: LocalDate, to: LocalDate, meal: Meal?, toMeal: Meal?): Int = db.withTransaction {
        val now = clock.millis()
        val rows = dao.day(from.toEpochDay()).filter { meal == null || it.meal == meal.code }
        dao.insertAll(
            rows.mapIndexed { i, e ->
                e.copy(
                    id = newId(), day = to.toEpochDay(), meal = toMeal?.code ?: e.meal, loggedAt = now + i, zoneId = clock.zone.id,
                    source = DataSource.MANUAL.code, createdAt = now, updatedAt = now, deletedAt = null,
                )
            },
        )
        rows.size
    }

    override suspend fun addSavedMeal(savedMealId: String, date: LocalDate, meal: Meal): Int = db.withTransaction {
        val saved = meals.saved(savedMealId) ?: return@withTransaction 0
        val items = saved.items.sortedBy { it.item.position }
        items.forEachIndexed { i, row ->
            val amount = Amount(row.item.grams, row.item.portionLabel, row.item.portionQuantity)
            dao.insert(entry(newId(), date, meal, row.food.toDomain(), amount, null, offset = i))
        }
        items.size
    }

    private fun entry(id: String, date: LocalDate, meal: Meal, food: Food, amount: Amount, note: String?, offset: Int = 0): FoodLogEntity {
        val n = food.nutrientsFor(amount.grams)
        val now = clock.millis()
        return FoodLogEntity(
            id = id, day = date.toEpochDay(), meal = meal.code, foodId = food.id, foodName = food.nameFa, grams = amount.grams,
            portionLabel = amount.portionLabel, portionQuantity = amount.portionQuantity,
            kcal = n.kcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG, fiberG = n.fiberG,
            loggedAt = now + offset, zoneId = clock.zone.id, source = DataSource.MANUAL.code, note = note.clean(),
            createdAt = now, updatedAt = now, deletedAt = null,
        )
    }

    private fun FoodLogEntity.toDomain() = FoodLogEntry(
        id = id, day = LocalDate.ofEpochDay(day), meal = Meal.fromCode(meal), foodId = foodId, foodName = foodName, grams = grams,
        portionLabel = portionLabel, portionQuantity = portionQuantity, nutrients = Nutrients(kcal, proteinG, carbsG, fatG, fiberG),
        loggedAt = Instant.ofEpochMilli(loggedAt), note = note,
    )

    private fun DayTotalsRow.toTotals() = NutrientTotals(
        kcal, protein, carbs, fat, fiber, kcalNulls > 0, proteinNulls > 0, carbsNulls > 0, fatNulls > 0, fiberNulls > 0, entries,
    )
}

class RoomSavedMealRepository(private val db: FitDatabase, private val clock: Clock) : SavedMealRepository {
    private val dao = db.mealDao()
    private val log = db.foodLogDao()

    override val all: Flow<List<SavedMeal>> = dao.observeSaved().map { list ->
        list.map { t ->
            SavedMeal(
                t.meal.id, t.meal.name, t.meal.meal?.let(Meal::fromCode),
                t.items.sortedBy { it.item.position }.map { SavedMealItem(it.food.toDomain(), Amount(it.item.grams, it.item.portionLabel, it.item.portionQuantity)) },
            )
        }
    }

    override suspend fun saveFrom(date: LocalDate, meal: Meal, name: String): String = db.withTransaction {
        val now = clock.millis()
        val id = newId()
        dao.insertSaved(SavedMealEntity(id, name.trim(), meal.code, dao.maxSavedPosition() + 1, now, now, null))
        val entries = log.day(date.toEpochDay()).filter { it.meal == meal.code && it.foodId != null }
        dao.insertSavedItems(entries.mapIndexed { i, e -> SavedMealItemEntity(newId(), id, e.foodId!!, e.grams, e.portionLabel, e.portionQuantity, i, now, now) })
        id
    }

    override suspend fun rename(id: String, name: String) = db.withTransaction {
        dao.savedEntity(id)?.let { dao.updateSaved(it.copy(name = name.trim(), updatedAt = clock.millis())) }
        Unit
    }

    override suspend fun delete(id: String) = db.withTransaction {
        dao.savedEntity(id)?.let { dao.updateSaved(it.copy(deletedAt = clock.millis(), updatedAt = clock.millis())) }
        Unit
    }
}

class RoomMealPlanRepository(private val db: FitDatabase, private val clock: Clock) : MealPlanRepository {
    private val dao = db.mealDao()
    private val log = db.foodLogDao()

    override val active: Flow<MealPlan?> = dao.observeActivePlan().map { t ->
        t?.let {
            MealPlan(
                it.plan.id, it.plan.name, it.plan.isActive,
                it.items.sortedBy { r -> r.item.position }.map { r ->
                    MealPlanItem(r.item.id, r.item.weekday?.let(DayOfWeek::of), Meal.fromCode(r.item.meal), r.food.toDomain(), r.item.grams)
                },
            )
        }
    }

    override suspend fun ensureActive(name: String): String = db.withTransaction {
        dao.activePlan()?.id ?: newId().also { id ->
            val now = clock.millis()
            dao.insertPlan(MealPlanEntity(id, name.trim(), true, now, now, null))
        }
    }

    override suspend fun addItem(planId: String, meal: Meal, foodId: String, grams: Double, weekday: DayOfWeek?): String = db.withTransaction {
        val now = clock.millis()
        val id = newId()
        dao.insertPlanItems(listOf(MealPlanItemEntity(id, planId, weekday?.value, meal.code, foodId, grams, dao.maxPlanPosition(planId) + 1, now, now)))
        id
    }

    override suspend fun updateItem(itemId: String, meal: Meal, grams: Double) = db.withTransaction {
        dao.planItem(itemId)?.let { dao.updatePlanItem(it.copy(meal = meal.code, grams = grams, updatedAt = clock.millis())) }
        Unit
    }

    override suspend fun removeItem(itemId: String) = db.withTransaction { dao.deletePlanItem(itemId) }

    override suspend fun replaceFromDay(planId: String, date: LocalDate) = db.withTransaction {
        val now = clock.millis()
        dao.deleteEveryDayItems(planId)
        val entries = log.day(date.toEpochDay()).filter { it.foodId != null }
        val start = dao.maxPlanPosition(planId) + 1
        dao.insertPlanItems(entries.mapIndexed { i, e -> MealPlanItemEntity(newId(), planId, null, e.meal, e.foodId!!, e.grams, start + i, now, now) })
    }
}
