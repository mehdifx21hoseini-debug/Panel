package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.entity.WeightEntryEntity
import app.fitos.core.data.db.entity.WeightGoalEntity
import app.fitos.core.domain.body.WeightCondition
import app.fitos.core.domain.body.WeightEntry
import app.fitos.core.domain.body.WeightRepository
import app.fitos.core.domain.common.DataSource
import app.fitos.core.domain.nutrition.NutritionTarget
import app.fitos.core.domain.nutrition.NutritionTargetRepository
import app.fitos.core.domain.profile.GoalStatus
import app.fitos.core.domain.profile.InitialSetup
import app.fitos.core.domain.profile.Profile
import app.fitos.core.domain.profile.ProfileRepository
import app.fitos.core.domain.profile.SetupRepository
import app.fitos.core.domain.profile.WeightGoal
import app.fitos.core.domain.profile.WeightGoalRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun newId(): String = UUID.randomUUID().toString()

class RoomProfileRepository(private val db: FitDatabase, private val clock: Clock) : ProfileRepository {
    private val dao = db.profileDao()

    override val profile: Flow<Profile?> = dao.observe().map { it?.toDomain() }

    override suspend fun save(profile: Profile) {
        val now = clock.millis()
        val createdAt = dao.get()?.createdAt ?: now
        dao.upsert(profile.toEntity(createdAt = createdAt, updatedAt = now))
    }
}

class RoomNutritionTargetRepository(private val db: FitDatabase, private val clock: Clock) : NutritionTargetRepository {
    private val dao = db.nutritionTargetDao()

    override fun effectiveOn(date: LocalDate): Flow<NutritionTarget?> =
        dao.observeEffectiveOn(date.toEpochDay()).map { it?.toDomain() }

    override suspend fun save(target: NutritionTarget) = upsertTarget(db, target, clock.millis())
}

/** Same-day saves replace that day's target (keeping its id); other days stay as history. */
internal suspend fun upsertTarget(db: FitDatabase, target: NutritionTarget, now: Long) {
    val dao = db.nutritionTargetDao()
    val existing = dao.getStartingOn(target.effectiveFrom.toEpochDay())
    dao.upsert(target.toEntity(id = existing?.id ?: newId(), createdAt = existing?.createdAt ?: now, updatedAt = now))
}

class RoomWeightGoalRepository(private val db: FitDatabase, private val clock: Clock) : WeightGoalRepository {
    private val dao = db.weightGoalDao()

    override val activeGoal: Flow<WeightGoal?> = dao.observeActive().map { it?.toDomain() }

    override suspend fun setActiveGoal(targetWeightKg: Double, reviewDate: LocalDate?, startWeightKg: Double?, startDate: LocalDate) {
        db.withTransaction { insertActiveGoal(db, targetWeightKg, reviewDate, startWeightKg, startDate, clock.millis()) }
    }

    override suspend fun clearActiveGoal() {
        dao.closeActive(GoalStatus.DROPPED.code, clock.millis())
    }
}

internal suspend fun insertActiveGoal(
    db: FitDatabase,
    targetWeightKg: Double,
    reviewDate: LocalDate?,
    startWeightKg: Double?,
    startDate: LocalDate,
    now: Long,
) {
    val dao = db.weightGoalDao()
    dao.closeActive(GoalStatus.REPLACED.code, now)
    dao.insert(
        WeightGoalEntity(
            id = newId(),
            targetWeightKg = targetWeightKg,
            startWeightKg = startWeightKg,
            startDate = startDate.toEpochDay(),
            reviewDate = reviewDate?.toEpochDay(),
            status = GoalStatus.ACTIVE.code,
            note = null,
            createdAt = now,
            updatedAt = now,
        ),
    )
}

class RoomWeightRepository(private val db: FitDatabase, private val clock: Clock) : WeightRepository {
    private val dao = db.weightEntryDao()

    override val latest: Flow<WeightEntry?> = dao.observeLatest().map { it?.toDomain() }

    override val all: Flow<List<WeightEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(weightKg: Double, condition: WeightCondition?, note: String?): WeightEntry {
        val entity = newWeightEntry(clock, weightKg, condition, note)
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun add(weightKg: Double, day: LocalDate, time: LocalTime, condition: WeightCondition?, note: String?): WeightEntry {
        val now = clock.millis()
        val entity = WeightEntryEntity(
            id = newId(),
            day = day.toEpochDay(),
            measuredAt = day.atTime(time).atZone(clock.zone).toInstant().toEpochMilli(),
            zoneId = clock.zone.id,
            weightKg = weightKg,
            condition = condition?.code,
            note = note.clean(),
            source = DataSource.MANUAL.code,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
        )
        dao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun update(id: String, weightKg: Double, day: LocalDate, time: LocalTime, condition: WeightCondition?, note: String?) {
        val old = dao.get(id) ?: return
        // An edit keeps the zone the entry was logged in, so its local day and time stay as typed.
        val zone = java.time.ZoneId.of(old.zoneId)
        dao.update(
            old.copy(
                day = day.toEpochDay(),
                measuredAt = day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                weightKg = weightKg,
                condition = condition?.code,
                note = note.clean(),
                updatedAt = clock.millis(),
            ),
        )
    }

    override suspend fun delete(id: String) = dao.softDelete(id, clock.millis())

    override suspend fun restore(id: String) = dao.restore(id, clock.millis())
}

internal fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** The local day is fixed at logging time from the clock's zone (see WeightEntry). */
internal fun newWeightEntry(clock: Clock, weightKg: Double, condition: WeightCondition?, note: String?): WeightEntryEntity {
    val now = clock.instant()
    return WeightEntryEntity(
        id = newId(),
        day = now.atZone(clock.zone).toLocalDate().toEpochDay(),
        measuredAt = now.toEpochMilli(),
        zoneId = clock.zone.id,
        weightKg = weightKg,
        condition = condition?.code,
        note = note.clean(),
        source = DataSource.MANUAL.code,
        createdAt = now.toEpochMilli(),
        updatedAt = now.toEpochMilli(),
        deletedAt = null,
    )
}

/** First-run setup: profile, first weigh-in, optional goal and first targets, all or nothing. */
class RoomSetupRepository(private val db: FitDatabase, private val clock: Clock) : SetupRepository {

    override val isSetupComplete: Flow<Boolean> = db.profileDao().observeExists()

    override suspend fun completeSetup(setup: InitialSetup) {
        val now = clock.millis()
        val today = LocalDate.now(clock)
        db.withTransaction {
            db.profileDao().upsert(setup.profile.toEntity(createdAt = now, updatedAt = now))
            db.weightEntryDao().insert(newWeightEntry(clock, setup.currentWeightKg, condition = null, note = null))
            setup.targetWeightKg?.let { target ->
                insertActiveGoal(db, target, setup.goalReviewDate, setup.currentWeightKg, today, now)
            }
            upsertTarget(db, setup.target, now)
        }
    }
}
