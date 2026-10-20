package app.fitos.core.data.repository

import androidx.room.withTransaction
import app.fitos.core.data.db.FitDatabase
import app.fitos.core.data.db.entity.BodyMeasurementEntity
import app.fitos.core.data.db.entity.MeasurementSiteEntity
import app.fitos.core.domain.body.BodyMeasurement
import app.fitos.core.domain.body.BuiltinSite
import app.fitos.core.domain.body.MeasurementRepository
import app.fitos.core.domain.body.MeasurementSite
import app.fitos.core.domain.common.DataSource
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class RoomMeasurementRepository(private val db: FitDatabase, private val clock: Clock) : MeasurementRepository {
    private val dao = db.measurementDao()

    override val sites: Flow<List<MeasurementSite>> = dao.observeSites()
        .onStart { ensureBuiltinSites() }
        .map { list -> list.map { it.toDomain() } }

    override val all: Flow<List<BodyMeasurement>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Built-in places are created lazily and only once (their key is unique), in enum order. */
    suspend fun ensureBuiltinSites() {
        val existing = dao.builtinKeys().toSet()
        val missing = BuiltinSite.entries.filter { it.key !in existing }
        if (missing.isEmpty()) return
        val now = clock.millis()
        db.withTransaction {
            missing.forEach { site ->
                dao.insertSite(
                    MeasurementSiteEntity(
                        id = newId(),
                        builtinKey = site.key,
                        nameFa = site.nameFa,
                        position = site.ordinal,
                        isHidden = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    override suspend fun add(day: LocalDate, valuesCm: Map<String, Double>, note: String?) {
        if (valuesCm.isEmpty()) return
        val now = clock.instant()
        val measuredAt = if (day == now.atZone(clock.zone).toLocalDate()) now.toEpochMilli() else day.atTime(12, 0).atZone(clock.zone).toInstant().toEpochMilli()
        dao.insert(
            valuesCm.map { (siteId, cm) ->
                BodyMeasurementEntity(
                    id = newId(),
                    siteId = siteId,
                    day = day.toEpochDay(),
                    measuredAt = measuredAt,
                    zoneId = clock.zone.id,
                    valueCm = cm,
                    note = note.clean(),
                    source = DataSource.MANUAL.code,
                    createdAt = now.toEpochMilli(),
                    updatedAt = now.toEpochMilli(),
                    deletedAt = null,
                )
            },
        )
    }

    override suspend fun update(id: String, day: LocalDate, valueCm: Double, note: String?) {
        val old = dao.get(id) ?: return
        dao.update(old.copy(day = day.toEpochDay(), valueCm = valueCm, note = note.clean(), updatedAt = clock.millis()))
    }

    override suspend fun delete(id: String) = dao.softDelete(id, clock.millis())

    override suspend fun restore(id: String) = dao.restore(id, clock.millis())

    override suspend fun addSite(name: String): MeasurementSite {
        val now = clock.millis()
        val entity = MeasurementSiteEntity(
            id = newId(),
            builtinKey = null,
            nameFa = name.trim(),
            position = dao.maxPosition() + 1,
            isHidden = false,
            createdAt = now,
            updatedAt = now,
        )
        dao.insertSite(entity)
        return entity.toDomain()
    }

    override suspend fun setHidden(siteId: String, hidden: Boolean) = dao.setHidden(siteId, hidden, clock.millis())
}

internal fun MeasurementSiteEntity.toDomain() = MeasurementSite(id, builtinKey, nameFa, position, isHidden)

internal fun BodyMeasurementEntity.toDomain() = BodyMeasurement(id, siteId, LocalDate.ofEpochDay(day), valueCm, note)
