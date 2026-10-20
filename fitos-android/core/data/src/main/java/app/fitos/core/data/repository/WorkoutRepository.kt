package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.dao.DayWeight
import app.fitos.core.data.db.dao.PerformanceRow
import app.fitos.core.data.db.dao.SessionExerciseTree
import app.fitos.core.data.db.dao.SessionTree
import app.fitos.core.data.db.entity.PersonalRecordEntity
import app.fitos.core.data.db.entity.SessionExerciseEntity
import app.fitos.core.data.db.entity.SetLogEntity
import app.fitos.core.data.db.entity.WorkoutSessionEntity
import app.fitos.core.domain.common.DataSource
import app.fitos.core.domain.training.CompletedDay
import app.fitos.core.domain.training.ExercisePerformance
import app.fitos.core.domain.training.PersonalRecord
import app.fitos.core.domain.training.RecordType
import app.fitos.core.domain.training.Records
import app.fitos.core.domain.training.SessionExercise
import app.fitos.core.domain.training.SessionReport
import app.fitos.core.domain.training.SessionStatus
import app.fitos.core.domain.training.SessionSummary
import app.fitos.core.domain.training.SessionTargets
import app.fitos.core.domain.training.SetInput
import app.fitos.core.domain.training.SetLog
import app.fitos.core.domain.training.SetQuality
import app.fitos.core.domain.training.SetType
import app.fitos.core.domain.training.Strength
import app.fitos.core.domain.training.TrainingRules
import app.fitos.core.domain.training.VariantKind
import app.fitos.core.domain.training.WorkoutRepository
import app.fitos.core.domain.training.WorkoutSession
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Thrown by [RoomWorkoutRepository.start] while another session is still open. */
class SessionInProgressException : IllegalStateException("a workout session is already in progress")

class RoomWorkoutRepository(private val db: FitDatabase, private val clock: Clock) : WorkoutRepository {
    private val dao = db.workoutDao()
    private val programDao = db.programDao()
    private val exerciseDao = db.exerciseDao()

    private fun now() = clock.millis()

    override val active: Flow<WorkoutSession?> = dao.observeActive().map { it?.toDomain() }

    override val history: Flow<List<SessionSummary>> = combine(dao.observeFinished(), dao.observeBodyWeights()) { trees, weights ->
        trees.map { it.toDomain().summary(weights) }
    }

    override fun session(id: String): Flow<WorkoutSession?> = dao.observeTree(id).map { it?.toDomain() }

    override fun lastCompleted(programId: String): Flow<CompletedDay?> =
        dao.observeLastFinished(programId).map { s -> s?.let { CompletedDay(it.programDayId, LocalDate.ofEpochDay(it.day)) } }

    override fun exerciseHistory(exerciseId: String): Flow<List<ExercisePerformance>> =
        combine(dao.observePerformances(exerciseId), dao.observeBodyWeights()) { rows, weights -> rows.map { it.toDomain(bodyWeightOn(weights, it.sessionDay)) } }

    override fun records(exerciseId: String): Flow<List<PersonalRecord>> = dao.observeRecords(exerciseId).map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun lastPerformance(exerciseId: String, before: Instant): ExercisePerformance? =
        dao.lastPerformance(exerciseId, before.toEpochMilli())?.let { it.toDomain(dao.bodyWeightOn(it.sessionDay)) }

    // ---- session lifecycle ----

    override suspend fun start(programId: String, dayId: String, kind: VariantKind): String = db.withTransaction {
        if (dao.active() != null) throw SessionInProgressException()
        val day = programDao.day(dayId)?.takeIf { it.programId == programId } ?: error("program day not found")
        val variant = programDao.variant(day.id, kind.code)
        val planned = variant?.let { programDao.plannedIn(it.id) }.orEmpty()
        val id = newId()
        val at = now()
        dao.insertSession(
            WorkoutSessionEntity(
                id = id, programId = programId, programDayId = day.id, variantKind = kind.code,
                day = LocalDate.now(clock).toEpochDay(), startedAt = at, endedAt = null, pausedAt = null, pausedMillis = 0,
                status = SessionStatus.IN_PROGRESS.code, zoneId = clock.zone.id, perceivedEffort = null, note = null,
                source = DataSource.MANUAL.code, createdAt = at, updatedAt = at, deletedAt = null,
            ),
        )
        dao.insertItems(
            planned.mapIndexed { index, p ->
                SessionExerciseEntity(
                    id = newId(), sessionId = id, exerciseId = p.exerciseId, position = index, plannedExerciseId = p.id,
                    replacedExerciseId = null, skipped = false, targetSets = p.sets, targetRepMin = p.repMin, targetRepMax = p.repMax,
                    targetRir = p.targetRir, targetWeightKg = p.startWeightKg, targetDurationSec = p.durationSec, restSec = p.restSec,
                    note = p.note, createdAt = at, updatedAt = at,
                )
            },
        )
        id
    }

    override suspend fun addExercise(sessionId: String, exerciseId: String): String = db.withTransaction {
        val exercise = exerciseDao.get(exerciseId)?.toDomain() ?: error("exercise not found")
        val t = TrainingRules.defaults(exercise)
        val position = (dao.items(sessionId).maxOfOrNull { it.position } ?: -1) + 1
        val id = newId()
        val at = now()
        dao.insertItems(
            listOf(
                SessionExerciseEntity(
                    id, sessionId, exerciseId, position, null, null, false, t.sets, t.repMin, t.repMax, t.targetRir,
                    t.startWeightKg, t.durationSec, t.restSec, null, at, at,
                ),
            ),
        )
        touch(sessionId)
        id
    }

    override suspend fun replaceExercise(sessionExerciseId: String, exerciseId: String) = db.withTransaction {
        val item = dao.item(sessionExerciseId) ?: return@withTransaction
        // Logged sets belong to the exercise they were done with.
        require(dao.setCount(item.id) == 0) { "exercise already has sets" }
        dao.updateItem(item.copy(exerciseId = exerciseId, replacedExerciseId = item.replacedExerciseId ?: item.exerciseId, updatedAt = now()))
        touch(item.sessionId)
    }

    override suspend fun setSkipped(sessionExerciseId: String, skipped: Boolean) = db.withTransaction {
        val item = dao.item(sessionExerciseId) ?: return@withTransaction
        dao.updateItem(item.copy(skipped = skipped, updatedAt = now()))
        touch(item.sessionId)
    }

    override suspend fun logSet(sessionExerciseId: String, input: SetInput): String = db.withTransaction {
        val item = dao.item(sessionExerciseId) ?: error("session exercise not found")
        val session = dao.session(item.sessionId) ?: error("session not found")
        val at = now()
        val id = newId()
        dao.insertSet(input.toEntity(id, item.id, dao.maxSetPosition(item.id) + 1, completedAt = at, createdAt = at))
        if (item.skipped) dao.updateItem(item.copy(skipped = false, updatedAt = at))
        // Logging a set while paused means training goes on.
        dao.updateSession(session.resumed(at).copy(updatedAt = at))
        id
    }

    override suspend fun updateSet(setId: String, input: SetInput) {
        val exerciseId = db.withTransaction {
            val old = dao.set(setId) ?: return@withTransaction null
            dao.updateSet(input.toEntity(old.id, old.sessionExerciseId, old.position, old.completedAt, old.createdAt).copy(updatedAt = now()))
            finishedExerciseOf(old.sessionExerciseId)
        }
        exerciseId?.let { rebuildRecords(it) }
    }

    override suspend fun deleteSet(setId: String) {
        val exerciseId = db.withTransaction {
            val old = dao.set(setId) ?: return@withTransaction null
            dao.deleteSet(setId)
            finishedExerciseOf(old.sessionExerciseId)
        }
        exerciseId?.let { rebuildRecords(it) }
    }

    override suspend fun pause(sessionId: String) = db.withTransaction {
        val s = dao.session(sessionId) ?: return@withTransaction
        if (s.status == SessionStatus.IN_PROGRESS.code && s.pausedAt == null) dao.updateSession(s.copy(pausedAt = now(), updatedAt = now()))
    }

    override suspend fun resume(sessionId: String) = db.withTransaction {
        val s = dao.session(sessionId) ?: return@withTransaction
        if (s.pausedAt != null) dao.updateSession(s.resumed(now()).copy(updatedAt = now()))
    }

    override suspend fun finish(sessionId: String, perceivedEffort: Int?, note: String?) {
        db.withTransaction {
            val s = dao.session(sessionId) ?: return@withTransaction
            if (s.status != SessionStatus.IN_PROGRESS.code) return@withTransaction
            val at = now()
            dao.updateSession(
                s.resumed(at).copy(
                    status = SessionStatus.FINISHED.code, endedAt = at, perceivedEffort = perceivedEffort?.coerceIn(1, 10),
                    note = note.clean(), updatedAt = at,
                ),
            )
        }
        dao.items(sessionId).map { it.exerciseId }.distinct().forEach { rebuildRecords(it) }
    }

    override suspend fun discard(sessionId: String) = db.withTransaction {
        val s = dao.session(sessionId) ?: return@withTransaction
        if (s.status == SessionStatus.IN_PROGRESS.code) {
            dao.updateSession(s.copy(status = SessionStatus.ABANDONED.code, endedAt = now(), deletedAt = now(), updatedAt = now()))
        }
    }

    override suspend fun delete(sessionId: String) {
        db.withTransaction {
            val s = dao.session(sessionId) ?: return@withTransaction
            dao.updateSession(s.copy(deletedAt = now(), updatedAt = now()))
        }
        dao.items(sessionId).map { it.exerciseId }.distinct().forEach { rebuildRecords(it) }
    }

    override suspend fun report(sessionId: String): SessionReport? {
        val tree = dao.tree(sessionId) ?: return null
        val session = tree.toDomain()
        val bodyWeight = dao.bodyWeightOn(tree.session.day)
        val tonnage = session.exercises.sumOf { Strength.tonnage(it.exercise, it.sets, bodyWeight) }
        val previous = session.programDayId?.let { dayId ->
            dao.previousOfDay(dayId, tree.session.startedAt)?.let { prev -> prev.toDomain().summary(listOfNotNull(dao.bodyWeightOn(prev.session.day)?.let { DayWeight(prev.session.day, it) })) }
        }
        val records = mutableMapOf<String, List<PersonalRecord>>()
        val firstTime = mutableSetOf<String>()
        session.exercises.filter { it.working.isNotEmpty() }.map { it.exercise.id }.distinct().forEach { exerciseId ->
            val history = performances(exerciseId)
            val earlier = history.any { it.sessionId != sessionId && it.day <= session.day }
            if (!earlier) firstTime += exerciseId
            val set = Records.setIn(sessionId, dao.records(exerciseId).mapNotNull { it.toDomain() }, history)
            if (set.isNotEmpty()) records[exerciseId] = set
        }
        return SessionReport(session, tonnage, previous, records, firstTime)
    }

    // ---- records ----

    /** Rebuilds the record cache of one exercise from every finished session. */
    suspend fun rebuildRecords(exerciseId: String) {
        val exercise = exerciseDao.get(exerciseId)?.toDomain() ?: return
        val computed = Records.compute(exercise, performances(exerciseId))
        db.withTransaction {
            dao.deleteRecords(exerciseId)
            dao.insertRecords(
                computed.map {
                    PersonalRecordEntity(
                        newId(), exerciseId, it.type.code, it.reps, it.value, it.setId, it.sessionId,
                        achievedAt = it.day.atStartOfDay(clock.zone).toInstant().toEpochMilli(), day = it.day.toEpochDay(),
                    )
                },
            )
        }
    }

    private suspend fun performances(exerciseId: String): List<ExercisePerformance> =
        dao.performances(exerciseId).map { it.toDomain(dao.bodyWeightOn(it.sessionDay)) }

    /** The exercise id when the set's session is finished (its records then need rebuilding). */
    private suspend fun finishedExerciseOf(sessionExerciseId: String): String? {
        val item = dao.item(sessionExerciseId) ?: return null
        val session = dao.session(item.sessionId) ?: return null
        dao.updateSession(session.copy(updatedAt = now()))
        return item.exerciseId.takeIf { session.status == SessionStatus.FINISHED.code }
    }

    private suspend fun touch(sessionId: String) {
        dao.session(sessionId)?.let { dao.updateSession(it.copy(updatedAt = now())) }
    }

    // ---- mapping ----

    private fun WorkoutSessionEntity.resumed(at: Long): WorkoutSessionEntity =
        if (pausedAt == null) this else copy(pausedMillis = pausedMillis + (at - pausedAt).coerceAtLeast(0), pausedAt = null)

    private fun SetInput.toEntity(id: String, itemId: String, position: Int, completedAt: Long, createdAt: Long) = SetLogEntity(
        id = id, sessionExerciseId = itemId, position = position, setType = type.code, weightKg = weightKg, reps = reps,
        durationSec = durationSec, distanceM = distanceM, rir = rir, quality = quality?.code, pain = pain,
        completedAt = completedAt, createdAt = createdAt, updatedAt = createdAt,
    )

    private fun SetLogEntity.toDomain() = SetLog(
        id, position, SetType.fromCode(setType), weightKg, reps, durationSec, distanceM, rir, SetQuality.fromCode(quality), pain,
        Instant.ofEpochMilli(completedAt),
    )

    private fun SessionExerciseTree.toDomain() = SessionExercise(
        id = item.id,
        exercise = exercise.toDomain(),
        position = item.position,
        plannedExerciseId = item.plannedExerciseId,
        replacedExerciseId = item.replacedExerciseId,
        skipped = item.skipped,
        targets = SessionTargets(
            sets = item.targetSets ?: 1, repMin = item.targetRepMin, repMax = item.targetRepMax, rir = item.targetRir,
            weightKg = item.targetWeightKg, durationSec = item.targetDurationSec, restSec = item.restSec,
        ),
        supersetGroup = planned?.supersetGroup,
        progression = rule?.toDomain(),
        note = item.note,
        sets = sets.sortedBy { it.position }.map { it.toDomain() },
    )

    private fun SessionTree.toDomain() = WorkoutSession(
        id = session.id,
        programId = session.programId,
        programDayId = session.programDayId,
        dayName = day?.name,
        variantKind = session.variantKind?.let { VariantKind.fromCode(it) },
        day = LocalDate.ofEpochDay(session.day),
        zone = ZoneId.of(session.zoneId),
        startedAt = Instant.ofEpochMilli(session.startedAt),
        endedAt = session.endedAt?.let(Instant::ofEpochMilli),
        pausedAt = session.pausedAt?.let(Instant::ofEpochMilli),
        pausedMillis = session.pausedMillis,
        status = SessionStatus.fromCode(session.status),
        perceivedEffort = session.perceivedEffort,
        note = session.note,
        exercises = exercises.sortedBy { it.item.position }.map { it.toDomain() },
    )

    private fun WorkoutSession.summary(weights: List<DayWeight>): SessionSummary {
        val body = bodyWeightOn(weights, day.toEpochDay())
        return SessionSummary(
            id = id, programDayId = programDayId, dayName = dayName, variantKind = variantKind, day = day, startedAt = startedAt,
            activeMinutes = ((activeMillis(endedAt ?: startedAt) + 30_000) / 60_000).toInt(),
            exerciseCount = exercises.count { it.working.isNotEmpty() },
            workingSets = workingSets,
            tonnageKg = exercises.sumOf { Strength.tonnage(it.exercise, it.sets, body) },
        )
    }

    private fun PerformanceRow.toDomain(bodyWeightKg: Double?) =
        ExercisePerformance(item.sessionId, LocalDate.ofEpochDay(sessionDay), sets.sortedBy { it.position }.map { it.toDomain() }, bodyWeightKg)

    private fun PersonalRecordEntity.toDomain(): PersonalRecord? {
        val t = RecordType.fromCode(type) ?: return null
        return PersonalRecord(t, reps, value, sessionId ?: return null, setLogId, LocalDate.ofEpochDay(day))
    }

    /** Latest weigh-in on or before [day] from a list sorted by day. */
    private fun bodyWeightOn(weights: List<DayWeight>, day: Long): Double? = weights.lastOrNull { it.day <= day }?.weightKg
}
