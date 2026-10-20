package app.fitos.core.domain.body

import app.fitos.core.domain.common.DataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/** When the weight was taken; helps compare like with like. */
enum class WeightCondition(val code: String) {
    FASTED("fasted"),
    AFTER_MEAL("after_meal"),
    OTHER("other");

    companion object {
        fun fromCode(code: String?): WeightCondition? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One weigh-in. [day] is the calendar day at the moment of logging and is stored as is, so a
 * later change of time-zone rules never moves an entry to another day.
 */
data class WeightEntry(
    val id: String,
    val measuredAt: Instant,
    val zone: ZoneId,
    val day: LocalDate,
    val weightKg: Double,
    val condition: WeightCondition?,
    val note: String?,
    val source: DataSource,
)

interface WeightRepository {
    val latest: Flow<WeightEntry?>

    suspend fun add(weightKg: Double, condition: WeightCondition?, note: String?): WeightEntry
}
