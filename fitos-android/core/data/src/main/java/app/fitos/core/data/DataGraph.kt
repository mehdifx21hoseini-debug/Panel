package app.fitos.core.data

import android.content.Context
import androidx.room.Room
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.repository.RoomNutritionTargetRepository
import app.fitos.core.data.repository.RoomProfileRepository
import app.fitos.core.data.repository.RoomSetupRepository
import app.fitos.core.data.repository.RoomWeightGoalRepository
import app.fitos.core.data.repository.RoomWeightRepository
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.SetupRepository
import app.fitos.core.domain.profile.WeightGoalRepository
import java.time.Clock

/**
 * The repositories the app uses, behind domain interfaces. The app never sees Room types,
 * which keeps the database an implementation detail of this module.
 */
class DataGraph private constructor(private val db: FitDatabase, clock: Clock) {
    val profiles: ProfileRepository = RoomProfileRepository(db, clock)
    val setup: SetupRepository = RoomSetupRepository(db, clock)
    val targets: NutritionTargetRepository = RoomNutritionTargetRepository(db, clock)
    val goals: WeightGoalRepository = RoomWeightGoalRepository(db, clock)
    val weights: WeightRepository = RoomWeightRepository(db, clock)

    fun close() = db.close()

    companion object {
        fun open(context: Context, clock: Clock): DataGraph = DataGraph(FitDatabase.open(context), clock)

        /** For tests: a fresh database that lives only in memory. */
        fun inMemory(context: Context, clock: Clock): DataGraph = DataGraph(
            Room.inMemoryDatabaseBuilder(context.applicationContext, FitDatabase::class.java).build(),
            clock,
        )
    }
}
