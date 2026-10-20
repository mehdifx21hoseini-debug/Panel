package app.fitos.core.domain.training

import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The 14 regions of the muscle map (design system), front and back. */
enum class Muscle(val code: String, val nameFa: String, val front: Boolean, val back: Boolean) {
    CHEST("chest", "سینه", front = true, back = false),
    DELTS("delts", "سرشانه", front = true, back = true),
    TRAPS("traps", "کول (ذوزنقه)", front = false, back = true),
    LATS("lats", "زیربغل و پشت", front = false, back = true),
    LOWER_BACK("lower_back", "فیله‌ی کمر", front = false, back = true),
    BICEPS("biceps", "جلو بازو", front = true, back = false),
    TRICEPS("triceps", "پشت بازو", front = false, back = true),
    FOREARMS("forearms", "ساعد", front = true, back = true),
    ABS("abs", "شکم", front = true, back = false),
    OBLIQUES("obliques", "پهلو", front = true, back = false),
    QUADS("quads", "جلو ران", front = true, back = false),
    HAMSTRINGS("hamstrings", "پشت ران", front = false, back = true),
    GLUTES("glutes", "باسن", front = false, back = true),
    CALVES("calves", "ساق پا", front = true, back = true);

    companion object {
        fun fromCode(code: String): Muscle? = entries.firstOrNull { it.code == code }
    }
}

enum class Equipment(val code: String, val nameFa: String) {
    BARBELL("barbell", "هالتر"),
    DUMBBELL("dumbbell", "دمبل"),
    MACHINE("machine", "دستگاه"),
    CABLE("cable", "سیم‌کش"),
    BODYWEIGHT("bodyweight", "وزن بدن"),
    BAND("band", "کش"),
    KETTLEBELL("kettlebell", "کتل‌بل"),
    PULL_UP_BAR("pull_up_bar", "میله‌ی بارفیکس"),
    CARDIO("cardio", "هوازی"),
    OTHER("other", "سایر");

    companion object {
        fun fromCode(code: String): Equipment = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

/** What one set records. */
enum class LogType(val code: String) {
    /** Weight × reps. */
    WEIGHT_REPS("weight_reps"),
    /** Reps only (body weight or band); extra weight is optional. */
    REPS("bodyweight_reps"),
    /** Seconds held or worked. */
    TIME("time"),
    /** Metres covered. */
    DISTANCE("distance");

    companion object {
        fun fromCode(code: String): LogType = entries.firstOrNull { it.code == code } ?: WEIGHT_REPS
    }
}

data class Exercise(
    val id: String,
    val builtinKey: String?,
    val nameFa: String,
    val nameEn: String?,
    val equipment: Equipment,
    val logType: LogType,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    /** Smallest sensible weight step, used by progression suggestions. */
    val weightIncrementKg: Double?,
    /** Share of body weight counted as load, so body-weight sets can count towards tonnage. */
    val bodyweightPercent: Int?,
    val instructions: String?,
    val isCustom: Boolean,
    val isFavorite: Boolean,
)

/** What the user fills in to create or edit an own exercise. */
data class ExerciseInput(
    val nameFa: String,
    val nameEn: String?,
    val equipment: Equipment,
    val logType: LogType,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    val weightIncrementKg: Double?,
    val instructions: String?,
)

/** Full gym session, short home session and a minimal one for very busy days. */
enum class VariantKind(val code: String) {
    FULL("full"),
    HOME("home"),
    MINIMAL("minimal");

    companion object {
        fun fromCode(code: String): VariantKind = entries.firstOrNull { it.code == code } ?: FULL
    }
}

enum class ScheduleMode(val code: String) {
    /** Next session = the day after the last one done; a missed day is never skipped. */
    SEQUENCE("sequence"),
    /** Each day belongs to a fixed weekday. */
    WEEKDAYS("weekdays");

    companion object {
        fun fromCode(code: String): ScheduleMode = entries.firstOrNull { it.code == code } ?: SEQUENCE
    }
}

enum class ProgressionType(val code: String) {
    /** Raise weight once every working set reaches the top of the rep range (rules in stage 4). */
    DOUBLE_PROGRESSION("double_progression"),
    NONE("none");

    companion object {
        fun fromCode(code: String): ProgressionType = entries.firstOrNull { it.code == code } ?: NONE
    }
}

data class ProgressionRule(
    val type: ProgressionType,
    val incrementKg: Double?,
    val deloadPercent: Int?,
    val requireGoodQuality: Boolean,
)

data class PlannedExercise(
    val id: String,
    val exercise: Exercise,
    val position: Int,
    val sets: Int,
    val repMin: Int?,
    val repMax: Int?,
    val targetRir: Int?,
    val durationSec: Int?,
    val distanceM: Int?,
    val startWeightKg: Double?,
    val restSec: Int?,
    /** Exercises with the same group number are done back to back as a superset. */
    val supersetGroup: Int?,
    val note: String?,
    val progression: ProgressionRule?,
)

/** The editable targets of a planned exercise (the exercise itself is chosen separately). */
data class PlannedTargets(
    val sets: Int,
    val repMin: Int?,
    val repMax: Int?,
    val targetRir: Int?,
    val durationSec: Int?,
    val distanceM: Int?,
    val startWeightKg: Double?,
    val restSec: Int?,
    val supersetGroup: Int?,
    val note: String?,
    val progression: ProgressionRule?,
)

fun PlannedExercise.targets() = PlannedTargets(sets, repMin, repMax, targetRir, durationSec, distanceM, startWeightKg, restSec, supersetGroup, note, progression)

data class DayVariant(
    val id: String,
    val kind: VariantKind,
    val estimatedMinutes: Int?,
    val exercises: List<PlannedExercise>,
)

data class ProgramDay(
    val id: String,
    val position: Int,
    val name: String,
    val isRest: Boolean,
    val preferredWeekday: DayOfWeek?,
    val note: String?,
    val variants: List<DayVariant>,
) {
    fun variant(kind: VariantKind): DayVariant? = variants.firstOrNull { it.kind == kind }
    val full: List<PlannedExercise> get() = variant(VariantKind.FULL)?.exercises.orEmpty()
}

data class Program(
    val id: String,
    val name: String,
    val description: String?,
    val isActive: Boolean,
    val scheduleMode: ScheduleMode,
    val days: List<ProgramDay>,
    val archived: Boolean,
) {
    val trainingDays: List<ProgramDay> get() = days.filter { !it.isRest }
}

data class ProgramSummary(val id: String, val name: String, val isActive: Boolean, val trainingDays: Int, val archived: Boolean)

data class ProgramChange(val at: Instant, val summary: String)

interface ExerciseRepository {
    /** Library and own exercises (not deleted), built-in ones created on first read. */
    val all: Flow<List<Exercise>>

    fun exercise(id: String): Flow<Exercise?>

    suspend fun addCustom(input: ExerciseInput): Exercise

    suspend fun updateCustom(id: String, input: ExerciseInput)

    /** Hides an own exercise from the library; programs that use it keep working. */
    suspend fun deleteCustom(id: String)

    suspend fun setFavorite(id: String, favorite: Boolean)

    /** How many program days use the exercise (for a warning before deleting). */
    suspend fun usageCount(id: String): Int
}

interface ProgramRepository {
    val programs: Flow<List<ProgramSummary>>
    val active: Flow<Program?>

    fun program(id: String): Flow<Program?>
    fun changes(programId: String): Flow<List<ProgramChange>>

    suspend fun createEmpty(name: String): String
    suspend fun createFromTemplate(template: ProgramTemplate, homeEquipment: Set<app.fitos.core.domain.profile.HomeEquipment>): String
    suspend fun duplicate(programId: String, name: String): String
    suspend fun rename(programId: String, name: String)
    suspend fun setScheduleMode(programId: String, mode: ScheduleMode)
    suspend fun setActive(programId: String)
    suspend fun archive(programId: String, archived: Boolean)

    suspend fun addDay(programId: String, name: String, isRest: Boolean): String
    suspend fun updateDay(dayId: String, name: String, isRest: Boolean, weekday: DayOfWeek?, note: String?)
    suspend fun moveDay(dayId: String, by: Int)
    suspend fun deleteDay(dayId: String)

    suspend fun addExercise(dayId: String, kind: VariantKind, exerciseId: String): String
    suspend fun updatePlanned(plannedId: String, targets: PlannedTargets)
    suspend fun movePlanned(plannedId: String, by: Int)
    suspend fun replaceExercise(plannedId: String, exerciseId: String)
    suspend fun removePlanned(plannedId: String)
    /** Replaces the exercises of [to] with a copy of [from]'s (e.g. start the home version from the gym one). */
    suspend fun copyVariant(dayId: String, from: VariantKind, to: VariantKind)
}
