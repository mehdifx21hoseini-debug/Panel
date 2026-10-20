package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.entity.ExerciseEntity
import app.fitos.core.domain.training.Exercise
import app.fitos.core.domain.training.ExerciseFilter
import app.fitos.core.domain.training.ExerciseInput
import app.fitos.core.domain.training.ExerciseLibrary
import app.fitos.core.domain.training.ExerciseRepository
import app.fitos.core.domain.training.LibraryExercise
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomExerciseRepository(private val db: FitDatabase, private val clock: Clock) : ExerciseRepository {
    private val dao = db.exerciseDao()
    private val seedLock = Mutex()
    @Volatile private var seeded = false

    // SQLite sorts by code point, which puts پ چ ژ گ after ی; the list is re-sorted in Persian order.
    override val all: Flow<List<Exercise>> = dao.observeAll()
        .onStart { ensureLibrary() }
        .map { list -> list.map { it.toDomain() }.sortedWith(compareBy(ExerciseFilter.nameOrder) { it.nameFa }) }

    override fun exercise(id: String): Flow<Exercise?> = dao.observe(id).map { it?.toDomain() }

    /**
     * Inserts missing built-in exercises and refreshes the texts of existing ones, keyed by
     * [LibraryExercise.key]. Favourites and ids are kept, so programs never lose their exercises.
     */
    suspend fun ensureLibrary() = seedLock.withLock {
        if (seeded) return@withLock
        val existing = dao.builtins().associateBy { it.exercise.builtinKey }
        val now = clock.millis()
        db.withTransaction {
            ExerciseLibrary.all.forEach { lib ->
                val row = existing[lib.key]
                val id = row?.exercise?.id ?: newId()
                val entity = lib.toEntity(id, isFavorite = row?.exercise?.isFavorite ?: false, createdAt = row?.exercise?.createdAt ?: now, updatedAt = now)
                val same = row != null && row.exercise.copy(updatedAt = now) == entity &&
                    row.muscles.toSet() == muscleRows(id, lib.primary, lib.secondary).toSet()
                if (!same) {
                    dao.upsert(entity)
                    dao.deleteMuscles(id)
                    dao.insertMuscles(muscleRows(id, lib.primary, lib.secondary))
                }
            }
        }
        seeded = true
    }

    /** Checks the library again, after a restore replaced the tables (an older backup may lack built-ins). */
    suspend fun reseed() {
        seedLock.withLock { seeded = false }
        ensureLibrary()
    }

    /** The id of a built-in exercise by key (after the library has been ensured). */
    suspend fun idsByKey(): Map<String, String> {
        ensureLibrary()
        return dao.builtins().associate { it.exercise.builtinKey!! to it.exercise.id }
    }

    override suspend fun addCustom(input: ExerciseInput): Exercise {
        val now = clock.millis()
        val id = newId()
        db.withTransaction {
            dao.upsert(input.toEntity(id, now, now))
            dao.insertMuscles(muscleRows(id, input.primary, input.secondary))
        }
        return dao.get(id)!!.toDomain()
    }

    override suspend fun updateCustom(id: String, input: ExerciseInput) {
        val old = dao.get(id)?.exercise ?: return
        require(old.isCustom) { "built-in exercises are read-only" }
        db.withTransaction {
            dao.upsert(input.toEntity(id, old.createdAt, clock.millis()).copy(isFavorite = old.isFavorite))
            dao.deleteMuscles(id)
            dao.insertMuscles(muscleRows(id, input.primary, input.secondary))
        }
    }

    override suspend fun deleteCustom(id: String) = dao.softDeleteCustom(id, clock.millis())

    override suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite, clock.millis())

    override suspend fun usageCount(id: String): Int = dao.usageCount(id)

    private fun LibraryExercise.toEntity(id: String, isFavorite: Boolean, createdAt: Long, updatedAt: Long) = ExerciseEntity(
        id = id,
        builtinKey = key,
        nameFa = nameFa,
        nameEn = nameEn,
        equipment = equipment.code,
        logType = logType.code,
        weightIncrementKg = incrementKg,
        bodyweightPercent = bodyweightPercent,
        instructions = instructions,
        isCustom = false,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = null,
    )

    private fun ExerciseInput.toEntity(id: String, createdAt: Long, updatedAt: Long) = ExerciseEntity(
        id = id,
        builtinKey = null,
        nameFa = nameFa.trim(),
        nameEn = nameEn?.trim()?.takeIf { it.isNotEmpty() },
        equipment = equipment.code,
        logType = logType.code,
        weightIncrementKg = weightIncrementKg,
        bodyweightPercent = null,
        instructions = instructions.clean(),
        isCustom = true,
        isFavorite = false,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = null,
    )
}
