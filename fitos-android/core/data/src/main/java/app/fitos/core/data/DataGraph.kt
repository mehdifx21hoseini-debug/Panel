package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import app.fitos.core.data.backup.AppInfo
import app.fitos.core.data.backup.BackupCrypto
import app.fitos.core.data.backup.RoomBackupService
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomExerciseRepository
import app.fitos.core.data.repository.RoomFoodLogRepository
import app.fitos.core.data.repository.RoomFoodRepository
import app.fitos.core.data.repository.RoomMealPlanRepository
import app.fitos.core.data.repository.RoomSavedMealRepository
import app.fitos.core.data.repository.RoomMeasurementRepository
import app.fitos.core.data.repository.RoomProgramRepository
import app.fitos.core.data.repository.RoomNutritionTargetRepository
import app.fitos.core.data.repository.RoomProfileRepository
import app.fitos.core.data.repository.RoomSetupRepository
import app.fitos.core.data.repository.RoomWeightGoalRepository
import app.fitos.core.data.repository.RoomWeightRepository
import app.fitos.core.data.repository.RoomWorkoutRepository
import app.fitos.core.domain.backup.BackupService
import app.fitos.core.domain.body.MeasurementRepository
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.nutrition.FoodLogRepository
import app.fitos.core.domain.nutrition.FoodRepository
import app.fitos.core.domain.nutrition.MealPlanRepository
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.nutrition.SavedMealRepository
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.SetupRepository
import app.fitos.core.domain.profile.WeightGoalRepository
import app.fitos.core.domain.settings.SettingsRepository
import app.fitos.core.domain.training.ExerciseRepository
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.WorkoutRepository
import java.time.Clock

/**
 * The repositories the app uses, behind domain interfaces. The app never sees Room types,
 * which keeps the database an implementation detail of this module.
 */
class DataGraph private constructor(
    context: Context,
    private val db: FitDatabase,
    clock: Clock,
    settings: SettingsRepository,
    app: AppInfo,
    backupIterations: Int,
) {
    val profiles: ProfileRepository = RoomProfileRepository(db, clock)
    val setup: SetupRepository = RoomSetupRepository(db, clock)
    val targets: NutritionTargetRepository = RoomNutritionTargetRepository(db, clock)
    val goals: WeightGoalRepository = RoomWeightGoalRepository(db, clock)
    val weights: WeightRepository = RoomWeightRepository(db, clock)
    val measurements: MeasurementRepository = RoomMeasurementRepository(db, clock)
    private val exerciseRepo = RoomExerciseRepository(db, clock)
    val exercises: ExerciseRepository = exerciseRepo
    val programs: ProgramRepository = RoomProgramRepository(db, exerciseRepo, clock)
    val workouts: WorkoutRepository = RoomWorkoutRepository(db, clock)
    private val foodRepo = RoomFoodRepository(db, clock)
    val foods: FoodRepository = foodRepo
    val foodLog: FoodLogRepository = RoomFoodLogRepository(db, clock)
    val savedMeals: SavedMealRepository = RoomSavedMealRepository(db, clock)
    val mealPlans: MealPlanRepository = RoomMealPlanRepository(db, clock)
    val backup: BackupService = RoomBackupService(
        context.applicationContext, db, settings, clock, app, backupIterations,
        // An older backup may lack built-in exercises or foods.
        afterRestore = {
            exerciseRepo.reseed()
            foodRepo.reseed()
        },
    )

    fun close() = db.close()

    companion object {
        fun open(context: Context, clock: Clock, settings: SettingsRepository, app: AppInfo): DataGraph =
            DataGraph(context, FitDatabase.open(context), clock, settings, app, BackupCrypto.DEFAULT_ITERATIONS)

        /** For tests: a fresh in-memory database, and a fast key derivation for encrypted backups. */
        fun inMemory(context: Context, clock: Clock, settings: SettingsRepository, app: AppInfo = AppInfo("test", 0)): DataGraph = DataGraph(
            context,
            Room.inMemoryDatabaseBuilder(context.applicationContext, FitDatabase::class.java).build(),
            clock,
            settings,
            app,
            backupIterations = 10_000,
        )
    }
}
