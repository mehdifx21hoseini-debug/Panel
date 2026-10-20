package app.fitos.core.domain.training

import app.fitos.core.domain.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** What the user chose after a break. */
enum class ReturnChoice(val code: String) {
    /** Carry on with the normal plan (the break rule of [Progression] still eases the first loads). */
    CONTINUE("continue"),
    /** The next sessions with one set less and about 10 % lighter weights. */
    LIGHTER("lighter"),
    /** The next sessions in their minimal version. */
    MINIMAL("minimal"),
    /** Not now; the card goes away until the next break. */
    DISMISSED("dismissed");

    companion object {
        fun fromCode(code: String?): ReturnChoice? = entries.firstOrNull { it.code == code }
    }
}

/** A break that was noticed, and the answer (null while open). */
data class ReturnEvent(
    val id: String,
    val detectedAt: Instant,
    val gapDays: Int,
    val choice: ReturnChoice?,
    val chosenAt: Instant?,
)

interface ReturnPlanRepository {
    /** The newest event. */
    val latest: Flow<ReturnEvent?>

    /** Records a newly noticed break. */
    suspend fun open(gapDays: Int): ReturnEvent

    suspend fun choose(id: String, choice: ReturnChoice)
}

/** An easier start after a break: which of the [total] easy sessions comes next (1-based). */
data class EasyStart(val choice: ReturnChoice, val number: Int, val total: Int)

/**
 * Coming back after a break: one calm question per break, no streaks and no blame.
 * Asked once [gapDays] reaches the threshold; at most one notification per break.
 */
object ReturnPlan {
    const val DEFAULT_THRESHOLD_DAYS = 4
    val THRESHOLD_DAYS = 3..14
    const val EASY_SESSIONS = 2
    const val LIGHTER_PERCENT = 10

    /** Whole days without a finished workout between the last one and today. */
    fun gapDays(lastTrained: LocalDate, today: LocalDate): Int =
        (ChronoUnit.DAYS.between(lastTrained, today) - 1).toInt().coerceAtLeast(0)

    /**
     * The break length worth asking about, or null. Only after at least one finished workout,
     * never with a session open, and only once per break: an event created after the last
     * workout started means this break was already asked about.
     */
    fun detect(lastDay: LocalDate?, lastStartedAt: Instant?, latest: ReturnEvent?, sessionOpen: Boolean, today: LocalDate, threshold: Int): Int? {
        if (lastDay == null || lastStartedAt == null || sessionOpen) return null
        val gap = gapDays(lastDay, today)
        if (gap < threshold) return null
        if (latest != null && latest.detectedAt >= lastStartedAt) return null
        return gap
    }

    /** The open question, if the latest event is still unanswered. */
    fun pending(latest: ReturnEvent?): ReturnEvent? = latest?.takeIf { it.choice == null }

    /**
     * Whether a session that starts (or started) at [startedAt] is one of the easy ones, given
     * the start times of all sessions. Easy sessions are the first [EASY_SESSIONS] started after
     * the choice.
     */
    fun easy(latest: ReturnEvent?, startedAt: Instant, starts: List<Instant>): EasyStart? {
        val choice = latest?.choice?.takeIf { it == ReturnChoice.LIGHTER || it == ReturnChoice.MINIMAL } ?: return null
        val chosenAt = latest.chosenAt ?: return null
        if (startedAt < chosenAt) return null
        val before = starts.count { it >= chosenAt && it < startedAt }
        return if (before < EASY_SESSIONS) EasyStart(choice, before + 1, EASY_SESSIONS) else null
    }

    /**
     * Whether a session of [kind] is started lighter: during the easy sessions, except the minimal
     * version, which is light already. A "minimal" choice starts the other versions lighter only
     * when the day has no minimal version to offer.
     */
    fun lightens(easy: EasyStart?, kind: VariantKind, dayHasMinimal: Boolean): Boolean =
        easy != null && kind != VariantKind.MINIMAL && (easy.choice == ReturnChoice.LIGHTER || !dayHasMinimal)

    /** The easy start for a session that would start [now]. */
    fun next(latest: ReturnEvent?, now: Instant, starts: List<Instant>): EasyStart? = easy(latest, now, starts)

    /** One set less from three sets up. */
    fun lighterSets(sets: Int): Int = if (sets >= 3) sets - 1 else sets

    /**
     * About 10 % lighter than the suggestion would be, on the exercise's weight steps. Suggestions
     * that already go down (pain, below range, long break) stay as they are.
     */
    fun lighten(s: Suggestion, step: Double): Suggestion {
        val weight = s.weightKg ?: return s
        if (s.kind == SuggestionKind.DECREASE || s.kind == SuggestionKind.RETURN || s.kind == SuggestionKind.FIRST_TIME) return s
        val base = s.previousWeightKg ?: weight
        return s.copy(kind = SuggestionKind.RETURN, reason = SuggestionReason.EASY_RETURN, weightKg = Progression.reduce(base, LIGHTER_PERCENT, step))
    }
}

/**
 * Notices a break and records it once. Used when Home opens and by the daily background check;
 * whichever comes first creates the event, so there is never more than one per break.
 */
class ReturnPlanChecker(
    private val workouts: WorkoutRepository,
    private val programs: ProgramRepository,
    private val events: ReturnPlanRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** The newly opened event, or null when there is nothing new to ask. */
    suspend fun check(): ReturnEvent? {
        if (programs.active.first() == null) return null
        val last = workouts.history.first().maxByOrNull { it.startedAt }
        val gap = ReturnPlan.detect(
            lastDay = last?.day,
            lastStartedAt = last?.startedAt,
            latest = events.latest.first(),
            sessionOpen = workouts.active.first() != null,
            today = LocalDate.now(clock),
            threshold = settings.returnThresholdDays.first(),
        ) ?: return null
        return events.open(gap)
    }
}
