package io.issac.pleosandroid.core.data.collection.mapper

import io.issac.pleosandroid.core.data.driving.decodeDriving
import io.issac.pleosandroid.core.data.driving.encode
import io.issac.pleosandroid.core.database.entity.JourneyEntity
import io.issac.pleosandroid.core.database.entity.PlaceEntity
import io.issac.pleosandroid.core.database.entity.VisitEntity
import io.issac.pleosandroid.core.domain.cards.PackType
import io.issac.pleosandroid.core.domain.collection.model.AssessmentStatus
import io.issac.pleosandroid.core.domain.collection.model.ConfirmedVisit
import io.issac.pleosandroid.core.domain.collection.model.JourneyRecord
import io.issac.pleosandroid.core.domain.collection.model.Place
import java.time.Instant

internal fun PlaceEntity.toDomain() = Place(
    id, name, district, category, latitude, longitude, radiusMeters, minimumDwellSeconds, imageKey, active, regionId,
    rewardPack?.let(PackType::valueOf), cardPrintId,
)
internal fun Place.toEntity() = PlaceEntity(
    id, name, district, category, latitude, longitude, radiusMeters, minimumDwellSeconds, imageKey, active, regionId,
    rewardPack?.name, cardPrintId,
)
internal fun VisitEntity.toDomain() = ConfirmedVisit(
    id, placeId, journeyId, Instant.ofEpochMilli(arrivedAt), Instant.ofEpochMilli(departedAt),
    Instant.ofEpochMilli(confirmedAt), zoneId, distanceFromPlaceMeters, accuracyMeters, qualificationVersion,
)
internal fun ConfirmedVisit.toEntity() = VisitEntity(
    id, placeId, journeyId, arrivedAt.toEpochMilli(), departedAt.toEpochMilli(), confirmedAt.toEpochMilli(),
    zoneId, distanceFromPlaceMeters, accuracyMeters, qualificationVersion,
)
internal fun JourneyEntity.toDomain() = JourneyRecord(
    id, Instant.ofEpochMilli(startedAt), Instant.ofEpochMilli(endedAt), zoneId, distanceMeters,
    AssessmentStatus.valueOf(assessment), score, harshStarts, assessmentVersion, decodeDriving(drivingJson),
)
internal fun JourneyRecord.toEntity() = JourneyEntity(
    id, startedAt.toEpochMilli(), endedAt.toEpochMilli(), zoneId, distanceMeters,
    assessment.name, score, harshStarts, assessmentVersion, driving?.encode(),
)
