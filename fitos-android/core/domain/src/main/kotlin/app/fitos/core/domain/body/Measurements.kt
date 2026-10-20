package app.fitos.core.domain.body

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Built-in measurement places, created on first use. Their keys never change. */
enum class BuiltinSite(val key: String, val nameFa: String) {
    ABDOMEN("abdomen", "دور شکم (روی ناف)"),
    WAIST("waist", "دور کمر (باریک‌ترین نقطه)"),
    CHEST("chest", "دور سینه"),
    SHOULDERS("shoulders", "دور سرشانه"),
    ARM("arm", "بازو"),
    THIGH("thigh", "ران"),
    HIPS("hips", "باسن"),
    NECK("neck", "گردن"),
}

data class MeasurementSite(
    val id: String,
    val builtinKey: String?,
    val name: String,
    val position: Int,
    val isHidden: Boolean,
)

data class BodyMeasurement(
    val id: String,
    val siteId: String,
    val day: LocalDate,
    val valueCm: Double,
    val note: String?,
)

interface MeasurementRepository {
    /** All places, built-in ones included (they are created on first read), in display order. */
    val sites: Flow<List<MeasurementSite>>

    /** Every measurement that is not deleted, oldest first. */
    val all: Flow<List<BodyMeasurement>>

    /** Saves one value per place for [day]; empty places are simply not in [valuesCm]. */
    suspend fun add(day: LocalDate, valuesCm: Map<String, Double>, note: String?)

    suspend fun update(id: String, day: LocalDate, valueCm: Double, note: String?)

    suspend fun delete(id: String)

    suspend fun restore(id: String)

    suspend fun addSite(name: String): MeasurementSite

    suspend fun setHidden(siteId: String, hidden: Boolean)
}

/** Accepted range for a body circumference, in cm. */
val MEASUREMENT_CM = 10.0..250.0
