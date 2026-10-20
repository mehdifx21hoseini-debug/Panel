package app.fitos.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.fitos.core.data.db.dao.ExerciseDao
import app.fitos.core.data.db.dao.FoodDao
import app.fitos.core.data.db.dao.FoodLogDao
import app.fitos.core.data.db.dao.MealDao
import app.fitos.core.data.db.dao.MeasurementDao
import app.fitos.core.data.db.dao.ProgramDao
import app.fitos.core.data.db.dao.WorkoutDao
import app.fitos.core.data.db.dao.NutritionTargetDao
import app.fitos.core.data.db.dao.ProfileDao
import app.fitos.core.data.db.dao.WeightEntryDao
import app.fitos.core.data.db.dao.WeightGoalDao
import app.fitos.core.data.db.entity.ActivityLogEntity
import app.fitos.core.data.db.entity.BodyMeasurementEntity
import app.fitos.core.data.db.entity.ConsentEntity
import app.fitos.core.data.db.entity.DayVariantEntity
import app.fitos.core.data.db.entity.ExerciseEntity
import app.fitos.core.data.db.entity.ExerciseMuscleEntity
import app.fitos.core.data.db.entity.FoodEntity
import app.fitos.core.data.db.entity.FoodLogEntity
import app.fitos.core.data.db.entity.FoodPortionEntity
import app.fitos.core.data.db.entity.HabitEntity
import app.fitos.core.data.db.entity.HabitLogEntity
import app.fitos.core.data.db.entity.MealPlanEntity
import app.fitos.core.data.db.entity.MealPlanItemEntity
import app.fitos.core.data.db.entity.MeasurementSiteEntity
import app.fitos.core.data.db.entity.NutritionTargetEntity
import app.fitos.core.data.db.entity.PersonalRecordEntity
import app.fitos.core.data.db.entity.PlannedExerciseEntity
import app.fitos.core.data.db.entity.ProfileEntity
import app.fitos.core.data.db.entity.ProgramChangeLogEntity
import app.fitos.core.data.db.entity.ProgramDayEntity
import app.fitos.core.data.db.entity.ProgramEntity
import app.fitos.core.data.db.entity.ProgressPhotoEntity
import app.fitos.core.data.db.entity.ProgressionRuleEntity
import app.fitos.core.data.db.entity.RecipeIngredientEntity
import app.fitos.core.data.db.entity.ReminderEntity
import app.fitos.core.data.db.entity.ReturnPlanEventEntity
import app.fitos.core.data.db.entity.SavedMealEntity
import app.fitos.core.data.db.entity.SavedMealItemEntity
import app.fitos.core.data.db.entity.SessionExerciseEntity
import app.fitos.core.data.db.entity.SetLogEntity
import app.fitos.core.data.db.entity.SleepLogEntity
import app.fitos.core.data.db.entity.WaterLogEntity
import app.fitos.core.data.db.entity.WeeklyReportEntity
import app.fitos.core.data.db.entity.WeightEntryEntity
import app.fitos.core.data.db.entity.WeightGoalEntity
import app.fitos.core.data.db.entity.WorkoutSessionEntity

/**
 * The whole v1 schema is created up front (see docs/database.md); features fill their tables stage by stage.
 * Schema changes only ever go through [Migrations]; destructive fallback is never enabled.
 */
@Database(
    entities = [
        WeightEntryEntity::class,
        MeasurementSiteEntity::class,
        BodyMeasurementEntity::class,
        ProgressPhotoEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        WaterLogEntity::class,
        SleepLogEntity::class,
        ActivityLogEntity::class,
        ReminderEntity::class,
        FoodEntity::class,
        FoodPortionEntity::class,
        RecipeIngredientEntity::class,
        FoodLogEntity::class,
        SavedMealEntity::class,
        SavedMealItemEntity::class,
        MealPlanEntity::class,
        MealPlanItemEntity::class,
        ProfileEntity::class,
        NutritionTargetEntity::class,
        WeightGoalEntity::class,
        WeeklyReportEntity::class,
        ReturnPlanEventEntity::class,
        ConsentEntity::class,
        ExerciseEntity::class,
        ExerciseMuscleEntity::class,
        ProgramEntity::class,
        ProgramDayEntity::class,
        DayVariantEntity::class,
        PlannedExerciseEntity::class,
        ProgressionRuleEntity::class,
        ProgramChangeLogEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        PersonalRecordEntity::class,
    ],
    version = FitDatabase.VERSION,
    exportSchema = true,
)
abstract class FitDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun nutritionTargetDao(): NutritionTargetDao
    abstract fun weightGoalDao(): WeightGoalDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun mealDao(): MealDao

    companion object {
        const val NAME = "fitos.db"
        const val VERSION = 1

        /** Opens the app database, first copying the file aside if this start is about to upgrade it. */
        fun open(context: Context): FitDatabase {
            DatabaseUpgradeGuard.backupBeforeUpgrade(context.getDatabasePath(NAME), VERSION, System.currentTimeMillis())
            return Room.databaseBuilder(context.applicationContext, FitDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
        }
    }
}

/** Every schema migration, in order. Each one gets a test in MigrationTest. */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
