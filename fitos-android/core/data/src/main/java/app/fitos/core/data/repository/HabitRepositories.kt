package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.entity.ActivityLogEntity
import app.fitos.core.data.db.entity.HabitEntity
import app.fitos.core.data.db.entity.HabitLogEntity
import app.fitos.core.data.db.entity.ReminderEntity
import app.fitos.core.data.db.entity.ReturnPlanEventEntity
import app.fitos.core.data.db.entity.SleepLogEntity
import app.fitos.core.data.db.entity.WaterLogEntity
import app.fitos.core.domain.common.DataSource
import app.fitos.core.domain.habits.ActivityEntry
import app.fitos.core.domain.habits.ActivityInput
import app.fitos.core.domain.habits.ActivityKind
import app.fitos.core.domain.habits.ActivityRepository
import app.fitos.core.domain.habits.Habit
import app.fitos.core.domain.habits.HabitFrequency
import app.fitos.core.domain.habits.HabitInput
import app.fitos.core.domain.habits.HabitKind
import app.fitos.core.domain.habits.HabitLog
import app.fitos.core.domain.habits.HabitRepository
import app.fitos.core.domain.habits.HabitStatus
import app.fitos.core.domain.habits.LifestyleRules
import app.fitos.core.domain.habits.Reminder
import app.fitos.core.domain.habits.ReminderInput
import app.fitos.core.domain.habits.ReminderRepository
import app.fitos.core.domain.habits.ReminderType
import app.fitos.core.domain.habits.SleepEntry
import app.fitos.core.domain.habits.SleepRepository
import app.fitos.core.domain.habits.SystemHabit
import app.fitos.core.domain.habits.WaterEntry
import app.fitos.core.domain.habits.WaterRepository
import app.fitos.core.domain.training.ReturnChoice
import app.fitos.core.domain.training.ReturnEvent
import app.fitos.core.domain.training.ReturnPlanRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** ISO weekdays as a bit mask (bit 0 = Monday); an empty set means every day. */
internal fun Set<DayOfWeek>.toMask(): Int = if (isEmpty()) ALL_DAYS else fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

internal fun Int?.toWeekdays(): Set<DayOfWeek> =
    if (this == null || this == 0 || this == ALL_DAYS) emptySet() else DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

private const val ALL_DAYS = 0b1111111

class RoomHabitRepository(private val db: FitDatabase, private val clock: Clock) : HabitRepository {
    private val dao = db.habitDao()

    override val habits: Flow<List<Habit>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun habit(id: String): Flow<Habit?> = dao.observe(id).map { it?.toDomain() }

    override fun logs(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        dao.observeLogs(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override suspend fun add(input: HabitInput): String = db.withTransaction {
        val now = clock.millis()
        val id = newId()
        dao.insert(input.toEntity(id, dao.maxPosition() + 1, now, now))
        id
    }

    override suspend fun update(id: String, input: HabitInput) = db.withTransaction {
        val old = dao.get(id) ?: return@withTransaction
        dao.update(input.toEntity(id, old.position, old.createdAt, clock.millis()).copy(isArchived = old.isArchived))
    }

    override suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, archived, clock.millis())

    override suspend fun delete(id: String) = dao.softDelete(id, clock.millis())

    override suspend fun restore(id: String) = dao.restore(id, clock.millis())

    override suspend fun move(id: String, by: Int) = db.withTransaction {
        val list = dao.all().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from == -1) return@withTransaction
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (to == from) return@withTransaction
        list.add(to, list.removeAt(from))
        val now = clock.millis()
        list.forEachIndexed { i, h -> if (h.position != i) dao.setPosition(h.id, i, now) }
    }

    override suspend fun setLog(habitId: String, day: LocalDate, status: HabitStatus, value: Double?) = db.withTransaction {
        val now = clock.millis()
        val old = dao.getLog(habitId, day.toEpochDay())
        dao.upsertLog(
            HabitLogEntity(
                id = old?.id ?: newId(),
                habitId = habitId,
                day = day.toEpochDay(),
                status = status.code,
                value = value,
                note = old?.note,
                loggedAt = now,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun clearLog(habitId: String, day: LocalDate) = dao.deleteLog(habitId, day.toEpochDay())

    private fun HabitEntity.toDomain() = Habit(
        id = id,
        name = name,
        emoji = emoji,
        kind = HabitKind.fromCode(kind),
        system = SystemHabit.fromCode(systemType),
        frequency = HabitFrequency.fromCode(frequency),
        target = target,
        unit = unit,
        weekdays = weekdaysMask.toWeekdays(),
        position = position,
        archived = isArchived,
        createdOn = Instant.ofEpochMilli(createdAt).atZone(clock.zone).toLocalDate(),
    )
}

private fun HabitInput.toEntity(id: String, position: Int, createdAt: Long, updatedAt: Long) = HabitEntity(
    id = id,
    name = name.trim(),
    emoji = emoji,
    kind = kind.code,
    systemType = system?.code,
    frequency = frequency.code,
    target = target,
    unit = unit.clean(),
    weekdaysMask = if (weekdays.isEmpty()) null else weekdays.toMask(),
    position = position,
    isArchived = false,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = null,
)

private fun HabitLogEntity.toDomain() = HabitLog(habitId, LocalDate.ofEpochDay(day), HabitStatus.fromCode(status), value)

class RoomWaterRepository(private val db: FitDatabase, private val clock: Clock) : WaterRepository {
    private val dao = db.lifestyleDao()

    override fun entries(day: LocalDate): Flow<List<WaterEntry>> = dao.observeWater(day.toEpochDay()).map { list ->
        list.map { WaterEntry(it.id, LocalDate.ofEpochDay(it.day), Instant.ofEpochMilli(it.loggedAt), it.amountMl) }
    }

    override fun totals(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        dao.observeWaterTotals(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.associate { LocalDate.ofEpochDay(it.day) to it.total } }

    override suspend fun add(day: LocalDate, amountMl: Int): String {
        require(amountMl in LifestyleRules.WATER_ML)
        val now = clock.instant()
        val id = newId()
        db.withTransaction {
            dao.insertWater(
                WaterLogEntity(
                    id = id,
                    day = day.toEpochDay(),
                    loggedAt = loggedAt(day, now),
                    zoneId = clock.zone.id,
                    amountMl = amountMl,
                    source = DataSource.MANUAL.code,
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                    deletedAt = null,
                ),
            )
        }
        return id
    }

    override suspend fun delete(id: String) = dao.deleteWater(id, clock.millis())

    override suspend fun restore(id: String) = dao.restoreWater(id, clock.millis())

    /** Now for today; noon for an earlier day logged later. */
    private fun loggedAt(day: LocalDate, now: Instant): Long =
        if (day == now.atZone(clock.zone).toLocalDate()) now.toEpochMilli() else day.atTime(12, 0).atZone(clock.zone).toInstant().toEpochMilli()
}

class RoomSleepRepository(private val db: FitDatabase, private val clock: Clock) : SleepRepository {
    private val dao = db.lifestyleDao()

    override fun entries(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>> =
        dao.observeSleep(from.toEpochDay(), to.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override suspend fun save(day: LocalDate, bedtime: LocalTime, wake: LocalTime, quality: Int?, note: String?): String = db.withTransaction {
        val (bedAt, wakeAt, minutes) = LifestyleRules.night(day, bedtime, wake)
        require(minutes in LifestyleRules.SLEEP_MINUTES)
        val now = clock.millis()
        val old = dao.sleepOn(day.toEpochDay())
        val entity = SleepLogEntity(
            id = old?.id ?: newId(),
            day = day.toEpochDay(),
            bedtimeAt = bedAt.atZone(clock.zone).toInstant().toEpochMilli(),
            wakeAt = wakeAt.atZone(clock.zone).toInstant().toEpochMilli(),
            durationMinutes = minutes,
            quality = quality,
            note = note.clean(),
            source = DataSource.MANUAL.code,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
        )
        if (old == null) dao.insertSleep(entity) else dao.updateSleep(entity)
        entity.id
    }

    override suspend fun delete(id: String) = dao.deleteSleep(id, clock.millis())

    override suspend fun restore(id: String) = dao.restoreSleep(id, clock.millis())

    private fun SleepLogEntity.toDomain() = SleepEntry(
        id = id,
        day = LocalDate.ofEpochDay(day),
        bedtime = bedtimeAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), clock.zone) },
        wake = wakeAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), clock.zone) },
        durationMinutes = durationMinutes,
        quality = quality,
        note = note,
    )
}

class RoomActivityRepository(private val db: FitDatabase, private val clock: Clock) : ActivityRepository {
    private val dao = db.lifestyleDao()

    override fun entries(from: LocalDate, to: LocalDate): Flow<List<ActivityEntry>> =
        dao.observeActivity(from.toEpochDay(), to.toEpochDay()).map { list ->
            list.map {
                ActivityEntry(it.id, LocalDate.ofEpochDay(it.day), ActivityKind.fromCode(it.kind), it.steps, it.durationMinutes, it.note, Instant.ofEpochMilli(it.loggedAt))
            }
        }

    override suspend fun add(day: LocalDate, input: ActivityInput): String {
        require(LifestyleRules.check(input).isEmpty())
        val now = clock.instant()
        val id = newId()
        db.withTransaction {
            dao.insertActivity(
                ActivityLogEntity(
                    id = id,
                    day = day.toEpochDay(),
                    kind = input.kind.code,
                    steps = input.steps,
                    durationMinutes = input.durationMinutes,
                    distanceM = null,
                    note = input.note.clean(),
                    loggedAt = if (day == now.atZone(clock.zone).toLocalDate()) now.toEpochMilli() else day.atTime(12, 0).atZone(clock.zone).toInstant().toEpochMilli(),
                    zoneId = clock.zone.id,
                    source = DataSource.MANUAL.code,
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                    deletedAt = null,
                ),
            )
        }
        return id
    }

    override suspend fun update(id: String, input: ActivityInput) = db.withTransaction {
        require(LifestyleRules.check(input).isEmpty())
        val old = dao.getActivity(id) ?: return@withTransaction
        dao.updateActivity(
            old.copy(kind = input.kind.code, steps = input.steps, durationMinutes = input.durationMinutes, note = input.note.clean(), updatedAt = clock.millis()),
        )
    }

    override suspend fun delete(id: String) = dao.deleteActivity(id, clock.millis())

    override suspend fun restore(id: String) = dao.restoreActivity(id, clock.millis())
}

class RoomReminderRepository(private val db: FitDatabase, private val clock: Clock) : ReminderRepository {
    private val dao = db.reminderDao()

    override val all: Flow<List<Reminder>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun list(): List<Reminder> = dao.all().map { it.toDomain() }

    override suspend fun get(id: String): Reminder? = dao.get(id)?.toDomain()

    override suspend fun save(id: String?, input: ReminderInput): String = db.withTransaction {
        val now = clock.millis()
        val old = id?.let { dao.get(it) }
        val entity = ReminderEntity(
            id = old?.id ?: newId(),
            type = input.type.code,
            habitId = input.habitId,
            minuteOfDay = input.time.hour * 60 + input.time.minute,
            weekdaysMask = input.weekdays.toMask(),
            isEnabled = input.enabled,
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(entity)
        entity.id
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled, clock.millis())

    override suspend fun delete(id: String) = dao.delete(id)

    private fun ReminderEntity.toDomain() = Reminder(
        id = id,
        type = ReminderType.fromCode(type),
        habitId = habitId,
        time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
        weekdays = weekdaysMask.toWeekdays(),
        enabled = isEnabled,
    )
}

class RoomReturnPlanRepository(private val db: FitDatabase, private val clock: Clock) : ReturnPlanRepository {
    private val dao = db.returnPlanDao()

    override val latest: Flow<ReturnEvent?> = dao.observeLatest().map { e ->
        e?.let {
            ReturnEvent(it.id, Instant.ofEpochMilli(it.detectedAt), it.gapDays, ReturnChoice.fromCode(it.choice), it.chosenAt?.let(Instant::ofEpochMilli))
        }
    }

    override suspend fun open(gapDays: Int): ReturnEvent {
        val now = clock.millis()
        val entity = ReturnPlanEventEntity(newId(), now, gapDays, null, null, now, now)
        db.withTransaction { dao.insert(entity) }
        return ReturnEvent(entity.id, Instant.ofEpochMilli(now), gapDays, null, null)
    }

    override suspend fun choose(id: String, choice: ReturnChoice) = dao.choose(id, choice.code, clock.millis())
}
