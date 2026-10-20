package app.fitos.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "weight_entry", indices = [Index("day"), Index("measuredAt")])
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val day: Long,
    val measuredAt: Long,
    val zoneId: String,
    val weightKg: Double,
    /** fasted, after_meal or other. */
    val condition: String?,
    val note: String?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

@Entity(tableName = "measurement_site", indices = [Index(value = ["builtinKey"], unique = true)])
data class MeasurementSiteEntity(
    @PrimaryKey val id: String,
    val builtinKey: String?,
    val nameFa: String,
    val position: Int,
    val isHidden: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "body_measurement",
    foreignKeys = [ForeignKey(entity = MeasurementSiteEntity::class, parentColumns = ["id"], childColumns = ["siteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("siteId"), Index("day")],
)
data class BodyMeasurementEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val day: Long,
    val measuredAt: Long,
    val zoneId: String,
    val valueCm: Double,
    val note: String?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)

/** Photos live in app-private storage; only file names are stored here. */
@Entity(tableName = "progress_photo", indices = [Index("day")])
data class ProgressPhotoEntity(
    @PrimaryKey val id: String,
    val day: Long,
    val takenAt: Long,
    val zoneId: String,
    /** front, side, back or other. */
    val angle: String,
    val fileName: String,
    val thumbFileName: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
