package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.entity.DayVariantEntity
import app.fitos.core.data.db.entity.PlannedExerciseEntity
import app.fitos.core.data.db.entity.ProgramChangeLogEntity
import app.fitos.core.data.db.entity.ProgramDayEntity
import app.fitos.core.data.db.entity.ProgramEntity
import app.fitos.core.data.db.entity.ProgressionRuleEntity
import app.fitos.core.domain.format.PersianNumbers
import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.time.JalaliCalendar
import app.fitos.core.domain.training.LogType
import app.fitos.core.domain.training.PlannedTargets
import app.fitos.core.domain.training.Program
import app.fitos.core.domain.training.ProgramChange
import app.fitos.core.domain.training.ProgramRepository
import app.fitos.core.domain.training.ProgramSummary
import app.fitos.core.domain.training.ProgramTemplate
import app.fitos.core.domain.training.ProgramTemplates
import app.fitos.core.domain.training.ProgressionRule
import app.fitos.core.domain.training.ProgressionType
import app.fitos.core.domain.training.ScheduleMode
import app.fitos.core.domain.training.TemplateSlot
import app.fitos.core.domain.training.TrainingRules
import app.fitos.core.domain.training.VariantKind
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Programs as a tree (program → days → variants → planned exercises). Every edit runs in one
 * transaction and leaves a plain-language entry in the program's change log.
 */
class RoomProgramRepository(
    private val db: FitDatabase,
    private val exercises: RoomExerciseRepository,
    private val clock: Clock,
) : ProgramRepository {
    private val dao = db.programDao()
    private val exerciseDao = db.exerciseDao()

    override val programs: Flow<List<ProgramSummary>> = dao.observeSummaries()
        .map { rows -> rows.map { ProgramSummary(it.id, it.name, it.isActive, it.trainingDays, it.archived) } }

    override val active: Flow<Program?> = dao.observeActiveTree().map { it?.toDomain() }

    override fun program(id: String): Flow<Program?> = dao.observeTree(id).map { it?.toDomain() }

    override fun changes(programId: String): Flow<List<ProgramChange>> =
        dao.observeChanges(programId).map { list -> list.map { ProgramChange(Instant.ofEpochMilli(it.at), it.summary) } }

    // ---- programs ----

    override suspend fun createEmpty(name: String): String = db.withTransaction {
        val id = insertProgram(name, description = null)
        log(id, "created", "برنامه‌ی «${name.trim()}» ساخته شد.")
        id
    }

    override suspend fun createFromTemplate(template: ProgramTemplate, homeEquipment: Set<HomeEquipment>): String {
        val ids = exercises.idsByKey()
        return db.withTransaction {
            val programId = insertProgram(template.name, template.description)
            template.days.forEachIndexed { index, t ->
                val dayId = newId()
                dao.insertDay(ProgramDayEntity(dayId, programId, index, t.name, t.rest, t.weekday.value, null, now(), now()))
                if (!t.rest) {
                    insertSlots(dayId, VariantKind.FULL, ProgramTemplates.resolve(t.full, HomeEquipment.entries.toSet()).map { it.key to it.slot }, ids)
                    insertSlots(dayId, VariantKind.HOME, ProgramTemplates.resolve(t.home, homeEquipment).map { it.key to it.slot }, ids)
                    insertSlots(dayId, VariantKind.MINIMAL, ProgramTemplates.resolve(t.minimal, homeEquipment).map { it.key to it.slot }, ids)
                }
            }
            log(programId, "created", "از قالب «${template.name}» ساخته شد.")
            programId
        }
    }

    override suspend fun duplicate(programId: String, name: String): String = db.withTransaction {
        val source = dao.tree(programId) ?: error("no program $programId")
        val copyId = insertProgram(name, source.program.description, active = false, mode = source.program.scheduleMode)
        source.days.forEach { d ->
            val dayId = newId()
            dao.insertDay(d.day.copy(id = dayId, programId = copyId, createdAt = now(), updatedAt = now()))
            d.variants.forEach { v ->
                val variantId = newId()
                dao.insertVariant(v.variant.copy(id = variantId, programDayId = dayId, createdAt = now(), updatedAt = now()))
                v.planned.forEach { p ->
                    val plannedId = newId()
                    dao.insertPlanned(p.planned.copy(id = plannedId, variantId = variantId, createdAt = now(), updatedAt = now()))
                    p.rule?.let { dao.upsertRule(it.copy(id = newId(), plannedExerciseId = plannedId, createdAt = now(), updatedAt = now())) }
                }
            }
        }
        log(copyId, "created", "کپی از برنامه‌ی «${source.program.name}» ساخته شد.")
        copyId
    }

    override suspend fun rename(programId: String, name: String) = db.withTransaction {
        val p = dao.program(programId) ?: return@withTransaction
        if (p.name == name.trim()) return@withTransaction
        dao.updateProgram(p.copy(name = name.trim(), updatedAt = now()))
        log(programId, "renamed", "نام برنامه از «${p.name}» به «${name.trim()}» تغییر کرد.")
    }

    override suspend fun setScheduleMode(programId: String, mode: ScheduleMode) = db.withTransaction {
        val p = dao.program(programId) ?: return@withTransaction
        if (p.scheduleMode == mode.code) return@withTransaction
        dao.updateProgram(p.copy(scheduleMode = mode.code, updatedAt = now()))
        log(programId, "schedule", if (mode == ScheduleMode.SEQUENCE) "زمان‌بندی: ترتیبی (روز جامانده حذف نمی‌شود)." else "زمان‌بندی: روزهای ثابت هفته.")
    }

    override suspend fun setActive(programId: String) = db.withTransaction {
        val p = dao.program(programId) ?: return@withTransaction
        dao.deactivateAll(now())
        dao.updateProgram(p.copy(isActive = true, archivedAt = null, updatedAt = now()))
        log(programId, "activated", "این برنامه فعال شد.")
    }

    override suspend fun archive(programId: String, archived: Boolean) = db.withTransaction {
        val p = dao.program(programId) ?: return@withTransaction
        dao.updateProgram(p.copy(archivedAt = if (archived) now() else null, isActive = if (archived) false else p.isActive, updatedAt = now()))
        log(programId, "archived", if (archived) "برنامه بایگانی شد." else "برنامه از بایگانی خارج شد.")
    }

    // ---- days ----

    override suspend fun addDay(programId: String, name: String, isRest: Boolean): String = db.withTransaction {
        val days = dao.days(programId)
        require(days.size < TrainingRules.MAX_DAYS) { "too many days" }
        val id = newId()
        dao.insertDay(ProgramDayEntity(id, programId, days.size, name.trim(), isRest, null, null, now(), now()))
        if (!isRest) ensureVariant(id, VariantKind.FULL)
        log(programId, "day_added", if (isRest) "روز استراحت اضافه شد." else "روز «${name.trim()}» اضافه شد.")
        id
    }

    override suspend fun updateDay(dayId: String, name: String, isRest: Boolean, weekday: DayOfWeek?, note: String?) = db.withTransaction {
        val d = dao.day(dayId) ?: return@withTransaction
        val updated = d.copy(name = name.trim(), isRest = isRest, preferredWeekday = weekday?.value, note = note.clean(), updatedAt = now())
        if (updated.copy(updatedAt = d.updatedAt) == d) return@withTransaction
        dao.updateDay(updated)
        if (!isRest) ensureVariant(dayId, VariantKind.FULL)
        val parts = buildList {
            if (d.name != updated.name) add("نام «${d.name}» → «${updated.name}»")
            if (d.isRest != isRest) add(if (isRest) "روز استراحت شد" else "روز تمرین شد")
            if (d.preferredWeekday != updated.preferredWeekday) add("روز هفته: ${weekday?.let(JalaliCalendar::weekdayName) ?: "بدون روز ثابت"}")
            if (d.note != updated.note) add("یادداشت تغییر کرد")
        }
        log(d.programId, "day_changed", "روز «${updated.name}»: ${parts.joinToString("، ")}.")
    }

    override suspend fun moveDay(dayId: String, by: Int) = db.withTransaction {
        val d = dao.day(dayId) ?: return@withTransaction
        val days = dao.days(d.programId).toMutableList()
        val from = days.indexOfFirst { it.id == dayId }
        val to = (from + by).coerceIn(0, days.lastIndex)
        if (from == to) return@withTransaction
        days.add(to, days.removeAt(from))
        dao.updateDays(days.mapIndexed { i, day -> day.copy(position = i, updatedAt = now()) })
        log(d.programId, "day_moved", "روز «${d.name}» به جای ${PersianNumbers.format(to + 1)} رفت.")
    }

    override suspend fun deleteDay(dayId: String) = db.withTransaction {
        val d = dao.day(dayId) ?: return@withTransaction
        dao.deleteDay(dayId)
        dao.updateDays(dao.days(d.programId).mapIndexed { i, day -> day.copy(position = i) })
        log(d.programId, "day_removed", if (d.isRest) "یک روز استراحت حذف شد." else "روز «${d.name}» حذف شد.")
    }

    // ---- planned exercises ----

    override suspend fun addExercise(dayId: String, kind: VariantKind, exerciseId: String): String = db.withTransaction {
        val d = dao.day(dayId) ?: error("no day $dayId")
        val exercise = exerciseDao.get(exerciseId)?.toDomain() ?: error("no exercise $exerciseId")
        val variant = ensureVariant(dayId, kind)
        val list = dao.plannedIn(variant.id)
        require(list.size < TrainingRules.MAX_EXERCISES) { "too many exercises" }
        val id = insertPlanned(variant.id, exerciseId, list.size, TrainingRules.defaults(exercise))
        log(d.programId, "exercise_added", "«${exercise.nameFa}» به نسخه‌ی ${kind.labelFa}ِ «${d.name}» اضافه شد.")
        id
    }

    override suspend fun updatePlanned(plannedId: String, targets: PlannedTargets) = db.withTransaction {
        val (p, d, kind, name) = context(plannedId) ?: return@withTransaction
        val updated = p.copy(
            sets = targets.sets, repMin = targets.repMin, repMax = targets.repMax, targetRir = targets.targetRir,
            durationSec = targets.durationSec, distanceM = targets.distanceM, startWeightKg = targets.startWeightKg,
            restSec = targets.restSec, supersetGroup = targets.supersetGroup, note = targets.note.clean(), updatedAt = now(),
        )
        dao.updatePlanned(updated)
        val oldRule = dao.rule(plannedId)?.toDomain()
        setRule(plannedId, targets.progression)
        val parts = buildList {
            if (p.sets != updated.sets) add("ست ${fa(p.sets)} → ${fa(updated.sets)}")
            if (p.repMin != updated.repMin || p.repMax != updated.repMax) add("تکرار ${range(p.repMin, p.repMax)} → ${range(updated.repMin, updated.repMax)}")
            if (p.durationSec != updated.durationSec) add("زمان ${fa(p.durationSec)} → ${fa(updated.durationSec)} ثانیه")
            if (p.restSec != updated.restSec) add("استراحت ${fa(p.restSec)} → ${fa(updated.restSec)} ثانیه")
            if (p.startWeightKg != updated.startWeightKg) add("وزنه‌ی شروع ${kg(p.startWeightKg)} → ${kg(updated.startWeightKg)}")
            if (p.targetRir != updated.targetRir) add("RIR ${fa(p.targetRir)} → ${fa(updated.targetRir)}")
            if (p.supersetGroup != updated.supersetGroup) add(if (updated.supersetGroup != null) "سوپرست شد" else "از سوپرست جدا شد")
            if (oldRule != targets.progression) add("قاعده‌ی پیشرفت تغییر کرد")
            if (p.note != updated.note) add("یادداشت تغییر کرد")
        }
        if (parts.isNotEmpty()) log(d.programId, "exercise_changed", "«$name» در ${kind.labelFa}ِ «${d.name}»: ${parts.joinToString("، ")}.")
    }

    override suspend fun movePlanned(plannedId: String, by: Int) = db.withTransaction {
        val (p, d, kind, name) = context(plannedId) ?: return@withTransaction
        val list = dao.plannedIn(p.variantId).toMutableList()
        val from = list.indexOfFirst { it.id == plannedId }
        val to = (from + by).coerceIn(0, list.lastIndex)
        if (from == to) return@withTransaction
        list.add(to, list.removeAt(from))
        dao.updatePlannedList(list.mapIndexed { i, item -> item.copy(position = i, updatedAt = now()) })
        log(d.programId, "exercise_moved", "ترتیب «$name» در ${kind.labelFa}ِ «${d.name}» عوض شد.")
    }

    override suspend fun replaceExercise(plannedId: String, exerciseId: String) = db.withTransaction {
        val (p, d, kind, oldName) = context(plannedId) ?: return@withTransaction
        val replacement = exerciseDao.get(exerciseId)?.toDomain() ?: return@withTransaction
        val old = exerciseDao.get(p.exerciseId)?.toDomain()
        // Same kind of logging keeps the targets; otherwise start from the new exercise's defaults.
        val updated = if (old?.logType == replacement.logType) {
            p.copy(exerciseId = exerciseId, startWeightKg = null, updatedAt = now())
        } else {
            val t = TrainingRules.defaults(replacement)
            p.copy(
                exerciseId = exerciseId, sets = p.sets, repMin = t.repMin, repMax = t.repMax, targetRir = t.targetRir,
                durationSec = t.durationSec, distanceM = t.distanceM, startWeightKg = null, updatedAt = now(),
            ).also { setRule(plannedId, t.progression) }
        }
        dao.updatePlanned(updated)
        if (old?.logType == replacement.logType && replacement.logType == LogType.WEIGHT_REPS) {
            dao.rule(plannedId)?.let { dao.upsertRule(it.copy(incrementKg = replacement.weightIncrementKg ?: it.incrementKg, updatedAt = now())) }
        }
        log(d.programId, "exercise_replaced", "«$oldName» در ${kind.labelFa}ِ «${d.name}» با «${replacement.nameFa}» جایگزین شد.")
    }

    override suspend fun removePlanned(plannedId: String) = db.withTransaction {
        val (p, d, kind, name) = context(plannedId) ?: return@withTransaction
        dao.deletePlanned(plannedId)
        dao.updatePlannedList(dao.plannedIn(p.variantId).mapIndexed { i, item -> item.copy(position = i) })
        log(d.programId, "exercise_removed", "«$name» از ${kind.labelFa}ِ «${d.name}» حذف شد.")
    }

    override suspend fun copyVariant(dayId: String, from: VariantKind, to: VariantKind) = db.withTransaction {
        if (from == to) return@withTransaction
        val d = dao.day(dayId) ?: return@withTransaction
        val source = dao.variant(dayId, from.code) ?: return@withTransaction
        val target = ensureVariant(dayId, to)
        dao.deletePlannedIn(target.id)
        dao.plannedIn(source.id).forEach { p ->
            val id = newId()
            dao.insertPlanned(p.copy(id = id, variantId = target.id, createdAt = now(), updatedAt = now()))
            dao.rule(p.id)?.let { dao.upsertRule(it.copy(id = newId(), plannedExerciseId = id, createdAt = now(), updatedAt = now())) }
        }
        log(d.programId, "variant_copied", "نسخه‌ی ${to.labelFa}ِ «${d.name}» از روی نسخه‌ی ${from.labelFa} ساخته شد.")
    }

    // ---- helpers ----

    private fun now() = clock.millis()

    private suspend fun insertProgram(name: String, description: String?, active: Boolean? = null, mode: String = ScheduleMode.SEQUENCE.code): String {
        val id = newId()
        val makeActive = active ?: (dao.activeCount() == 0)
        dao.insertProgram(ProgramEntity(id, name.trim(), description, makeActive, mode, now(), now(), null, null))
        return id
    }

    private suspend fun ensureVariant(dayId: String, kind: VariantKind): DayVariantEntity =
        dao.variant(dayId, kind.code) ?: DayVariantEntity(newId(), dayId, kind.code, null, null, now(), now()).also { dao.insertVariant(it) }

    private suspend fun insertPlanned(variantId: String, exerciseId: String, position: Int, t: PlannedTargets): String {
        val id = newId()
        dao.insertPlanned(
            PlannedExerciseEntity(
                id, variantId, exerciseId, position, t.sets, t.repMin, t.repMax, t.targetRir, t.durationSec, t.distanceM,
                t.startWeightKg, t.restSec, t.supersetGroup, t.note, now(), now(),
            ),
        )
        setRule(id, t.progression)
        return id
    }

    private suspend fun insertSlots(dayId: String, kind: VariantKind, slots: List<Pair<String, TemplateSlot>>, ids: Map<String, String>) {
        if (slots.isEmpty()) return
        val variant = ensureVariant(dayId, kind)
        slots.forEachIndexed { index, (key, slot) ->
            val exerciseId = ids.getValue(key)
            val exercise = exerciseDao.get(exerciseId)!!.toDomain()
            val defaults = TrainingRules.defaults(exercise)
            insertPlanned(
                variant.id, exerciseId, index,
                defaults.copy(sets = slot.sets, repMin = slot.repMin ?: defaults.repMin, repMax = slot.repMax ?: defaults.repMax, targetRir = slot.rir, durationSec = slot.durationSec ?: defaults.durationSec, restSec = slot.restSec),
            )
        }
    }

    private suspend fun setRule(plannedId: String, rule: ProgressionRule?) {
        if (rule == null || rule.type == ProgressionType.NONE) {
            dao.deleteRule(plannedId)
        } else {
            val existing = dao.rule(plannedId)
            dao.upsertRule(
                ProgressionRuleEntity(
                    existing?.id ?: newId(), plannedId, rule.type.code, rule.incrementKg, rule.deloadPercent,
                    rule.requireGoodQuality, existing?.createdAt ?: now(), now(),
                ),
            )
        }
    }

    private data class Context(val planned: PlannedExerciseEntity, val day: ProgramDayEntity, val kind: VariantKind, val name: String)

    private suspend fun context(plannedId: String): Context? {
        val p = dao.planned(plannedId) ?: return null
        val v = dao.variantById(p.variantId) ?: return null
        val d = dao.day(v.programDayId) ?: return null
        val name = exerciseDao.get(p.exerciseId)?.exercise?.nameFa ?: "حرکت"
        return Context(p, d, VariantKind.fromCode(v.kind), name)
    }

    private suspend fun log(programId: String, type: String, summary: String) {
        dao.insertChange(ProgramChangeLogEntity(newId(), programId, now(), type, summary, null))
        dao.touch(programId, now())
    }

    private fun fa(n: Int?) = n?.let { PersianNumbers.format(it) } ?: "—"
    private fun kg(v: Double?) = v?.let { PersianNumbers.format(it) + " کیلوگرم" } ?: "خالی"
    private fun range(min: Int?, max: Int?) = if (min == null || max == null) "—" else if (min == max) fa(min) else "${fa(min)} تا ${fa(max)}"
}
