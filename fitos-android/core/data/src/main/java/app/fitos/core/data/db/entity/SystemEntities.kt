package app.fitos.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A generated weekly report. [isStale] is set when data of that week changes after generation. */
@Entity(tableName = "weekly_report", indices = [Index(value = ["weekStart"], unique = true)])
data class WeeklyReportEntity(
    @PrimaryKey val id: String,
    val weekStart: Long,
    val generatedAt: Long,
    val formatVersion: Int,
    val metricsJson: String,
    val suggestionsJson: String?,
    val isStale: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A detected break in logging and what the user chose to do about it. */
@Entity(tableName = "return_plan_event")
data class ReturnPlanEventEntity(
    @PrimaryKey val id: String,
    val detectedAt: Long,
    val gapDays: Int,
    /** continue, lighter, minimal or dismissed; null while unanswered. */
    val choice: String?,
    val chosenAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Explicit consent for any future feature that would move data (none exists in this version). */
@Entity(tableName = "consent", indices = [Index(value = ["feature"], unique = true)])
data class ConsentEntity(
    @PrimaryKey val id: String,
    val feature: String,
    val granted: Boolean,
    val at: Long,
    val note: String?,
)
