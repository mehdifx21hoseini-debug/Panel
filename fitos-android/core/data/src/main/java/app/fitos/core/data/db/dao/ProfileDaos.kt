package app.fitos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import app.fitos.core.data.db.entity.NutritionTargetEntity
import app.fitos.core.data.db.entity.ProfileEntity
import app.fitos.core.data.db.entity.WeightEntryEntity
import app.fitos.core.data.db.entity.WeightGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = '${ProfileEntity.SINGLE_ID}'")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = '${ProfileEntity.SINGLE_ID}'")
    suspend fun get(): ProfileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM profile)")
    fun observeExists(): Flow<Boolean>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface NutritionTargetDao {
    @Query("SELECT * FROM nutrition_target WHERE effectiveFrom <= :day ORDER BY effectiveFrom DESC LIMIT 1")
    fun observeEffectiveOn(day: Long): Flow<NutritionTargetEntity?>

    @Query("SELECT * FROM nutrition_target WHERE effectiveFrom = :day")
    suspend fun getStartingOn(day: Long): NutritionTargetEntity?

    @Query("SELECT * FROM nutrition_target ORDER BY effectiveFrom DESC")
    fun observeHistory(): Flow<List<NutritionTargetEntity>>

    @Upsert
    suspend fun upsert(target: NutritionTargetEntity)
}

@Dao
interface WeightGoalDao {
    @Query("SELECT * FROM weight_goal WHERE status = 'active' ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<WeightGoalEntity?>

    @Query("UPDATE weight_goal SET status = :status, updatedAt = :now WHERE status = 'active'")
    suspend fun closeActive(status: String, now: Long)

    @Insert
    suspend fun insert(goal: WeightGoalEntity)
}

@Dao
interface WeightEntryDao {
    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Insert
    suspend fun insert(entry: WeightEntryEntity)
}
