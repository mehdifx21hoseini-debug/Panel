package app.fitos.core.domain.training

import app.fitos.core.domain.profile.HomeEquipment
import app.fitos.core.domain.profile.HomeEquipment.BANDS
import app.fitos.core.domain.profile.HomeEquipment.DUMBBELLS
import app.fitos.core.domain.profile.HomeEquipment.PULL_UP_BAR
import java.time.DayOfWeek

/** One exercise choice; at home the first option whose equipment the user has is taken. */
data class TemplateOption(val key: String, val needs: Set<HomeEquipment> = emptySet())

data class TemplateSlot(
    val options: List<TemplateOption>,
    val sets: Int,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rir: Int? = null,
    val restSec: Int,
    val durationSec: Int? = null,
)

data class TemplateDay(
    val name: String,
    val weekday: DayOfWeek,
    val rest: Boolean = false,
    val full: List<TemplateSlot> = emptyList(),
    val home: List<TemplateSlot> = emptyList(),
    val minimal: List<TemplateSlot> = emptyList(),
)

data class ProgramTemplate(val key: String, val name: String, val description: String, val days: List<TemplateDay>)

/** A slot resolved for the user's equipment. */
data class ResolvedSlot(val key: String, val slot: TemplateSlot)

object ProgramTemplates {

    private fun gym(key: String) = listOf(TemplateOption(key))
    private fun opt(key: String, vararg needs: HomeEquipment) = TemplateOption(key, needs.toSet())
    private fun reps(options: List<TemplateOption>, sets: Int, min: Int, max: Int, rir: Int, rest: Int) =
        TemplateSlot(options, sets, min, max, rir, rest)
    private fun hold(options: List<TemplateOption>, sets: Int, seconds: Int, rest: Int) =
        TemplateSlot(options, sets, restSec = rest, durationSec = seconds)

    private val homePush = listOf(opt("db_floor_press", DUMBBELLS), opt("push_up"))
    private val homeRow = listOf(opt("one_arm_db_row", DUMBBELLS), opt("band_row", BANDS), opt("inverted_row"))
    private val homeLateral = listOf(opt("lateral_raise", DUMBBELLS), opt("band_lateral_raise", BANDS), opt("pike_push_up"))
    private val homeTriceps = listOf(opt("overhead_db_extension", DUMBBELLS), opt("band_triceps_extension", BANDS), opt("bench_dip"))
    private val homeSquat = listOf(opt("goblet_squat", DUMBBELLS), opt("bodyweight_squat"))
    private val homeHinge = listOf(opt("db_romanian_deadlift", DUMBBELLS), opt("glute_bridge"))
    private val homePress = listOf(opt("seated_db_press", DUMBBELLS), opt("band_shoulder_press", BANDS), opt("pike_push_up"))
    private val homeRear = listOf(opt("rear_delt_fly", DUMBBELLS), opt("band_pull_apart", BANDS))
    private val homeCurl = listOf(opt("dumbbell_curl", DUMBBELLS), opt("band_curl", BANDS))
    private val homeVertical = listOf(opt("pull_up", PULL_UP_BAR), opt("inverted_row"))
    private val homeFace = listOf(opt("band_face_pull", BANDS), opt("rear_delt_fly", DUMBBELLS))

    /**
     * Five training days a week with extra work for chest, back and shoulders and a balanced
     * lower body (approved plan, decision 10). Saturday-first week, rest on Monday and Friday.
     */
    val upperFocus5 = ProgramTemplate(
        key = "upper_focus_5",
        name = "۵ روزه: بالاتنه، سرشانه و پشت",
        description = "پنج جلسه در هفته با تمرکز بر سینه، پشت و سرشانه و پایین‌تنه‌ی متعادل. هر روز نسخه‌ی خانگی و حداقلی هم دارد.",
        days = listOf(
            TemplateDay(
                "سینه و پشت", DayOfWeek.SATURDAY,
                full = listOf(
                    reps(gym("barbell_bench_press"), 4, 6, 8, 2, 150),
                    reps(gym("lat_pulldown"), 4, 8, 10, 2, 120),
                    reps(gym("incline_dumbbell_press"), 3, 8, 12, 2, 120),
                    reps(gym("seated_cable_row"), 3, 10, 12, 2, 90),
                    reps(gym("lateral_raise"), 3, 12, 15, 1, 60),
                    reps(gym("triceps_pushdown"), 3, 10, 15, 1, 60),
                ),
                home = listOf(reps(homePush, 3, 8, 15, 2, 75), reps(homeRow, 3, 10, 15, 2, 75), reps(homeLateral, 2, 10, 15, 1, 60), reps(homeTriceps, 2, 10, 15, 1, 60)),
                minimal = listOf(reps(listOf(opt("push_up")), 2, 8, 20, 2, 60), reps(homeRow, 2, 10, 15, 2, 60)),
            ),
            TemplateDay(
                "پایین‌تنه", DayOfWeek.SUNDAY,
                full = listOf(
                    reps(gym("back_squat"), 4, 6, 8, 2, 180),
                    reps(gym("romanian_deadlift"), 3, 8, 10, 2, 150),
                    reps(gym("leg_press"), 3, 10, 12, 2, 120),
                    reps(gym("lying_leg_curl"), 3, 10, 15, 1, 90),
                    reps(gym("standing_calf_raise"), 3, 10, 15, 1, 60),
                    hold(gym("plank"), 3, 40, 60),
                ),
                home = listOf(reps(homeSquat, 3, 10, 15, 2, 75), reps(homeHinge, 3, 10, 15, 2, 75), reps(listOf(opt("lunge")), 2, 8, 12, 2, 60), hold(listOf(opt("plank")), 2, 40, 45)),
                minimal = listOf(reps(listOf(opt("bodyweight_squat")), 2, 15, 20, 2, 60), hold(listOf(opt("plank")), 2, 40, 45)),
            ),
            TemplateDay("استراحت", DayOfWeek.MONDAY, rest = true),
            TemplateDay(
                "سرشانه و بازو", DayOfWeek.TUESDAY,
                full = listOf(
                    reps(gym("seated_db_press"), 4, 8, 10, 2, 120),
                    reps(gym("lateral_raise"), 4, 12, 15, 1, 60),
                    reps(gym("reverse_pec_deck"), 3, 12, 15, 1, 60),
                    reps(gym("ez_curl"), 3, 8, 12, 1, 75),
                    reps(gym("cable_overhead_extension"), 3, 10, 12, 1, 75),
                    reps(gym("hammer_curl"), 2, 10, 12, 1, 60),
                ),
                home = listOf(reps(homePress, 3, 8, 12, 2, 75), reps(homeLateral, 3, 12, 15, 1, 60), reps(homeRear, 2, 12, 15, 1, 60), reps(homeCurl, 2, 10, 15, 1, 60), reps(homeTriceps, 2, 10, 15, 1, 60)),
                minimal = listOf(reps(listOf(opt("pike_push_up")), 2, 6, 12, 2, 60), reps(listOf(opt("lateral_raise", DUMBBELLS), opt("band_lateral_raise", BANDS), opt("diamond_push_up")), 2, 10, 15, 1, 60)),
            ),
            TemplateDay(
                "پشت و سرشانه", DayOfWeek.WEDNESDAY,
                full = listOf(
                    reps(gym("pull_up"), 4, 6, 10, 2, 120),
                    reps(gym("barbell_row"), 4, 8, 10, 2, 120),
                    reps(gym("one_arm_db_row"), 3, 10, 12, 2, 90),
                    reps(gym("face_pull"), 3, 12, 15, 1, 60),
                    reps(gym("cable_lateral_raise"), 3, 12, 15, 1, 60),
                    reps(gym("dumbbell_shrug"), 2, 10, 12, 1, 60),
                ),
                home = listOf(reps(homeVertical, 3, 6, 12, 2, 90), reps(homeRow, 3, 10, 15, 2, 75), reps(homeFace, 2, 12, 15, 1, 60), reps(homeLateral, 2, 12, 15, 1, 60)),
                minimal = listOf(reps(homeVertical, 2, 6, 12, 2, 60), reps(listOf(opt("band_pull_apart", BANDS), opt("superman")), 2, 12, 15, 1, 45)),
            ),
            TemplateDay(
                "کل بدن", DayOfWeek.THURSDAY,
                full = listOf(
                    reps(gym("bulgarian_split_squat"), 3, 8, 10, 2, 90),
                    reps(gym("incline_barbell_press"), 3, 8, 10, 2, 120),
                    reps(gym("hip_thrust"), 3, 8, 12, 2, 90),
                    reps(gym("close_grip_pulldown"), 3, 10, 12, 2, 90),
                    reps(gym("arnold_press"), 3, 10, 12, 2, 90),
                    reps(gym("hanging_leg_raise"), 3, 10, 15, 1, 60),
                ),
                home = listOf(reps(listOf(opt("bulgarian_split_squat", DUMBBELLS), opt("lunge")), 3, 8, 12, 2, 75), reps(homePush, 3, 8, 15, 2, 75), reps(listOf(opt("glute_bridge")), 3, 12, 20, 2, 60), reps(homeRow, 3, 10, 15, 2, 75), reps(listOf(opt("hanging_leg_raise", PULL_UP_BAR), opt("lying_leg_raise")), 2, 10, 15, 1, 45)),
                minimal = listOf(reps(listOf(opt("burpee")), 2, 8, 12, 2, 60), hold(listOf(opt("plank")), 2, 40, 45)),
            ),
            TemplateDay("استراحت", DayOfWeek.FRIDAY, rest = true),
        ),
    )

    val all = listOf(upperFocus5)

    /**
     * Gym slots take their only option; home slots the first option the user's equipment allows
     * that is not already in the session. A slot with no such option is left out, not guessed.
     */
    fun resolve(slots: List<TemplateSlot>, equipment: Set<HomeEquipment>): List<ResolvedSlot> {
        val used = mutableSetOf<String>()
        return slots.mapNotNull { slot ->
            slot.options.firstOrNull { equipment.containsAll(it.needs) && it.key !in used }
                ?.also { used += it.key }
                ?.let { ResolvedSlot(it.key, slot) }
        }
    }
}
