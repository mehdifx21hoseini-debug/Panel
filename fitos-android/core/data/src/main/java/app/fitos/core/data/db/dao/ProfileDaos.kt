package app.fitos.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import app.fitos.core.data.db.entity.BodyMeasurementEntity
import app.fitos.core.data.db.entity.MeasurementSiteEntity
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
    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL ORDER BY measuredAt DESC, createdAt DESC, rowid DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL ORDER BY measuredAt ASC, createdAt ASC, rowid ASC")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entry WHERE id = :id")
    suspend fun get(id: String): WeightEntryEntity?

    @Insert
    suspend fun insert(entry: WeightEntryEntity)

    @Update
    suspend fun update(entry: WeightEntryEntity)

    @Query("UPDATE weight_entry SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE weight_entry SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement_site ORDER BY position, createdAt")
    fun observeSites(): Flow<List<MeasurementSiteEntity>>

    @Query("SELECT builtinKey FROM measurement_site WHERE builtinKey IS NOT NULL")
    suspend fun builtinKeys(): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM measurement_site")
    suspend fun maxPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSite(site: MeasurementSiteEntity)

    @Query("UPDATE measurement_site SET isHidden = :hidden, updatedAt = :at WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, at: Long)

    @Query("SELECT * FROM body_measurement WHERE deletedAt IS NULL ORDER BY day ASC, measuredAt ASC")
    fun observeAll(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurement WHERE id = :id")
    suspend fun get(id: String): BodyMeasurementEntity?

    @Insert
    suspend fun insert(values: List<BodyMeasurementEntity>)

    @Update
    suspend fun update(value: BodyMeasurementEntity)

    @Query("UPDATE body_measurement SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE body_measurement SET deletedAt = NULL, updatedAt = :at WHERE id = :id")
    suspend fun restore(id: String, at: Long)
}
